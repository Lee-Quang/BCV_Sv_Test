param(
    [string]$ApiBase = '',
    [string]$Email = 'admin@bcv.local',
    [string]$Psql = '',
    [string]$ConfigPath = ''
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../../backend/local-config.ps1')
$config = Get-BcvLocalConfig $ConfigPath
$Psql = Resolve-BcvPsql $config $Psql
if (-not $ApiBase) { $ApiBase = ([string]$config.ApiUrls).Split(';')[0].Replace('0.0.0.0', '127.0.0.1').TrimEnd('/') }
# This integration test creates only uniquely named disposable fixtures. The finally block removes
# those fixtures by BOTH returned IDs and the unique prefix; existing business data is not edited.
if (-not $env:BCV_ADMIN_PASSWORD) {
    $env:BCV_ADMIN_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Admin password' -AsSecureString)).Password
}
$dbPassword = Get-BcvEnvironment 'BCV_DB_PASSWORD'
if (-not $dbPassword) { throw 'Set BCV_DB_PASSWORD for fixture cleanup before running this check.' }
$prefix = '__bcv_web_check_' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$created = [ordered]@{}
$headers = @{}
function Call([string]$method, [string]$path, $body = $null) {
    $args = @{ Uri="$ApiBase/api/admin$path"; Method=$method; Headers=$headers; ErrorAction='Stop' }
    if ($null -ne $body) { $args.ContentType='application/json'; $args.Body=($body | ConvertTo-Json -Depth 12 -Compress) }
    Invoke-RestMethod @args
}
function Assert($condition, [string]$message) { if (-not $condition) { throw $message }; Write-Output "PASS $message" }
function ExpectError([int]$status, [string]$method, [string]$path, $body) {
    try { Call $method $path $body | Out-Null; throw "Expected HTTP $status for $path" }
    catch { if ([int]$_.Exception.Response.StatusCode -ne $status) { throw }; Write-Output "PASS rejects invalid input: $path ($status)" }
}
try {
    $auth = Call POST '/login' @{ email=$Email; password=$env:BCV_ADMIN_PASSWORD }
    $headers.Authorization = "Bearer $($auth.token)"
    Assert ($auth.user.role -eq 'ADMIN') 'Admin login'
    $dashboard = Call GET '/dashboard?days=7'
    Assert ($dashboard.trend.Count -eq 7) 'Seven-day operational statistics'
    foreach ($resource in @('users','regions','places','place-types','cards','collections','quests','items')) {
        $list=Call GET "/data/$resource`?pageSize=2"
        Assert ($list.items.Count -le 2) "Pagination: $resource"
        if ($resource -eq 'users') { Assert (-not ($list.items | Where-Object {$_.PSObject.Properties.Name -contains 'password_hash'})) 'Password hashes never returned' }
    }
    $region = Call POST '/data/regions' @{ name="$prefix-region"; description='Disposable admin integration fixture'; status='ACTIVE' }
    $created.region=$region.id
    $type = (Call GET '/lookups').types[0].id
    $placeBody=@{name="$prefix-place";region_id=$region.id;place_type_id=$type;latitude=21.028511;longitude=105.804817;introduction='Kiểm thử nội dung';highlights='Kiểm thử';rating=4.5;rating_count=1;status='DRAFT';images=@('https://example.com/test.jpg')}
    $place=Call POST '/data/places' $placeBody; $created.place=$place.id
    $placeBody.status='ACTIVE'; $null=Call PUT "/data/places/$($place.id)" $placeBody
    $detail=Call GET "/data/places/$($place.id)"
    Assert ($detail.images.Count -eq 1 -and $detail.status -eq 'ACTIVE') 'Place content, coordinates, image links and publication'
    $placeBody.latitude=100
    ExpectError 400 PUT "/data/places/$($place.id)" $placeBody
    $placeBody.latitude=21.028511
    $before=(Call GET "/data/places/$($place.id)").latitude
    Assert ($before -eq 21.028511) 'Rejected coordinates leave old values intact'
    $cardBody=@{name="$prefix-card";place_id=$place.id;image_key='https://example.com/test.jpg';description='Disposable card';status='ACTIVE'}
    $card=Call POST '/data/cards' $cardBody; $created.card=$card.id
    ExpectError 409 POST '/data/cards' $cardBody
    $collectionBody=@{name="$prefix-collection";description='Disposable collection';status='ACTIVE';card_ids=@($card.id)}
    $collection=Call POST '/data/collections' $collectionBody; $created.collection=$collection.id
    Assert ((Call GET "/data/collections/$($collection.id)").card_ids[0] -eq $card.id) 'Collection card assignment'
    $collectionBody.card_ids=@(9223372036854775806)
    ExpectError 400 PUT "/data/collections/$($collection.id)" $collectionBody
    Assert ((Call GET "/data/collections/$($collection.id)").card_ids[0] -eq $card.id) 'Failed collection update rolls back membership'
    $collectionBody.card_ids=@();$null=Call PUT "/data/collections/$($collection.id)" $collectionBody
    Assert ((Call GET "/data/collections/$($collection.id)").card_ids.Count -eq 0) 'Remove card from collection'
    $questBody=@{name="$prefix-quest";quest_type='DAILY';condition_type='STEP_COUNT';target_value=1000;cycle_type='DAILY';reward_item_id=$null;reward_quantity=$null;status='DRAFT'}
    $quest=Call POST '/data/quests' $questBody; $created.quest=$quest.id
    Assert ((Call GET "/data/quests/$($quest.id)").target_value -eq 1000) 'Daily quest creation'
    $questBody.cycle_type='WEEKLY';ExpectError 400 PUT "/data/quests/$($quest.id)" $questBody
    ExpectError 400 POST '/data/users' @{ status='ACTIVE' }
    ExpectError 400 PUT "/data/users/$($auth.user.userId)" @{ status='BLOCKED' }
    $searched=Call GET ("/data/places?q="+[Uri]::EscapeDataString($prefix)+'&status=ACTIVE')
    Assert ($searched.total -eq 1) 'Combined keyword and status search'
    ExpectError 400 DELETE "/data/users/$($auth.user.userId)" $null
    ExpectError 400 DELETE "/data/regions/$($region.id)" $null
    ExpectError 400 DELETE "/data/places/$($place.id)" $null
    Assert ((Call GET "/data/places/$($place.id)").images.Count -eq 1) 'Rejected delete rolls back owned images'
    $collectionBody.card_ids=@($card.id);$null=Call PUT "/data/collections/$($collection.id)" $collectionBody
    ExpectError 400 DELETE "/data/cards/$($card.id)" $null
    foreach ($entry in @(@('collections',$collection.id),@('cards',$card.id),@('quests',$quest.id),@('places',$place.id),@('regions',$region.id))) {
        $path="/data/$($entry[0])/$($entry[1])"
        $null=Call DELETE $path
        ExpectError 404 GET $path $null
        ExpectError 404 DELETE $path $null
    }
    Assert $true 'Delete disposable content and protect referenced records/admin account'
    Write-Output 'All admin integration checks passed.'
} finally {
    $env:PGPASSWORD=$dbPassword
    $statements = [Collections.Generic.List[string]]::new()
    $statements.Add('BEGIN;')
    if($created.collection){$statements.Add("DELETE FROM collection_card WHERE collection_id=$($created.collection) AND EXISTS(SELECT 1 FROM collection WHERE collection_id=$($created.collection) AND name='$prefix-collection');");$statements.Add("DELETE FROM collection WHERE collection_id=$($created.collection) AND name='$prefix-collection';")}
    if($created.card){$statements.Add("DELETE FROM card WHERE card_id=$($created.card) AND name='$prefix-card';")}
    if($created.quest){$statements.Add("DELETE FROM quest WHERE quest_id=$($created.quest) AND name='$prefix-quest';")}
    if($created.place){$statements.Add("DELETE FROM place_image WHERE place_id=$($created.place) AND EXISTS(SELECT 1 FROM place WHERE place_id=$($created.place) AND name='$prefix-place');");$statements.Add("DELETE FROM place WHERE place_id=$($created.place) AND name='$prefix-place';")}
    if($created.region){$statements.Add("DELETE FROM region WHERE region_id=$($created.region) AND name='$prefix-region';")}
    $statements.Add('COMMIT;')
    try { ($statements -join "`n") | & $Psql -X -w -q -v ON_ERROR_STOP=1 -h $config.DbHost -p $config.DbPort -U $config.DbUser -d $config.DbName; if($LASTEXITCODE -ne 0){throw 'Fixture cleanup failed'}; Write-Output 'Disposable fixtures removed.' }
    finally { Remove-Item Env:PGPASSWORD,Env:BCV_ADMIN_PASSWORD -ErrorAction SilentlyContinue }
}
