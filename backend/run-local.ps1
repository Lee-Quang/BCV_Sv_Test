$ErrorActionPreference = 'Stop'
$dotnet = Join-Path $env:USERPROFILE '.dotnet\dotnet.exe'
if (-not (Test-Path -LiteralPath $dotnet)) {
    $dotnet = (Get-Command dotnet -ErrorAction Stop).Source
}

& $dotnet build (Join-Path $PSScriptRoot 'Bcv.Api\Bcv.Api.csproj') --verbosity quiet -p:UseAppHost=false
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }

# Secrets come from user-level environment variables when set (how to set them: see the SMTP note below),
# otherwise they are asked for. Nothing is written to the repository.
function Get-Secret([string]$name, [string]$prompt) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if (-not $value) { $value = [Environment]::GetEnvironmentVariable($name, 'Process') }
    if ($value) { return $value }
    $secure = Read-Host $prompt -AsSecureString
    return [System.Net.NetworkCredential]::new('', $secure).Password
}

$plain = Get-Secret 'BCV_DB_PASSWORD' 'PostgreSQL BCV password'
$env:ConnectionStrings__Bcv = "Host=127.0.0.1;Port=5433;Database=bcv;Username=postgres;Password=$plain"
$plain = $null

# Password reset codes go out through Gmail SMTP. Leave the App Password empty (press Enter)
# and the API logs the code instead.
#
# To stop being asked, set the secrets once for your Windows account:
#   [Environment]::SetEnvironmentVariable('BCV_DB_PASSWORD', (Read-Host 'PostgreSQL password'), 'User')
#   [Environment]::SetEnvironmentVariable('BCV_SMTP_PASSWORD', (Read-Host 'Gmail App Password'), 'User')
$smtpUser = 'nguyenthitho.eve@gmail.com'
$smtpPassword = (Get-Secret 'BCV_SMTP_PASSWORD' "Gmail App Password for $smtpUser (Enter to log codes instead)") -replace '\s', ''
if ($smtpPassword) {
    $env:Smtp__Host = 'smtp.gmail.com'
    $env:Smtp__Port = '587'
    $env:Smtp__EnableSsl = 'true'
    $env:Smtp__User = $smtpUser
    $env:Smtp__From = $smtpUser
    $env:Smtp__Password = $smtpPassword
    Write-Host "Password reset codes will be emailed from $smtpUser."
}
else {
    Write-Host 'SMTP skipped: password reset codes will be written to this log.'
}
$smtpPassword = $null

# Avatars are uploaded to Cloudflare R2 when all five BCV_R2_* values are set (backend\set-local-secrets.ps1);
# without them the API keeps avatars inline in the database. Never asked for here.
$r2 = @{
    AccountId = 'BCV_R2_ACCOUNT_ID'; Bucket = 'BCV_R2_BUCKET'; PublicBaseUrl = 'BCV_R2_PUBLIC_URL'
    AccessKeyId = 'BCV_R2_ACCESS_KEY_ID'; SecretAccessKey = 'BCV_R2_SECRET_ACCESS_KEY'
}
$r2Values = @{}
foreach ($entry in $r2.GetEnumerator()) {
    $value = [Environment]::GetEnvironmentVariable($entry.Value, 'User')
    if ($value) { $r2Values[$entry.Key] = $value }
}
if ($r2Values.PublicBaseUrl -like '*.r2.cloudflarestorage.com*') {
    # That is the private S3 API endpoint; phones can only load avatars from the bucket's public URL.
    Write-Host 'BCV_R2_PUBLIC_URL is the S3 API endpoint, not the public r2.dev URL: avatars stay in the database.' -ForegroundColor Yellow
}
elseif ($r2Values.Count -eq $r2.Count) {
    foreach ($entry in $r2Values.GetEnumerator()) { Set-Item "Env:R2__$($entry.Key)" $entry.Value }
    Write-Host "Avatars will be stored in Cloudflare R2 bucket $($r2Values.Bucket)."
}
else {
    Write-Host 'Cloudflare R2 not configured: avatars stay in the database.'
}
$r2Values = $null
try {
    & $dotnet (Join-Path $PSScriptRoot 'Bcv.Api\bin\Debug\net10.0\Bcv.Api.dll') --urls 'http://0.0.0.0:5080'
}
finally {
    Remove-Item Env:ConnectionStrings__Bcv -ErrorAction SilentlyContinue
    'Host', 'Port', 'EnableSsl', 'User', 'From', 'Password' | ForEach-Object {
        Remove-Item "Env:Smtp__$_" -ErrorAction SilentlyContinue
    }
    $r2.Keys | ForEach-Object { Remove-Item "Env:R2__$_" -ErrorAction SilentlyContinue }
}
