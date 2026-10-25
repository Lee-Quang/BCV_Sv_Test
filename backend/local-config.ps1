# Shared, non-secret machine settings for run-local, create-admin and integration checks.
function Get-BcvEnvironment([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrEmpty($value)) { $value = [Environment]::GetEnvironmentVariable($Name, 'User') }
    return $value
}

function Get-BcvLocalConfig([string]$ConfigPath = '') {
    $settings = @{
        DbHost = '127.0.0.1'; DbPort = 5432; DbName = 'bcv'; DbUser = 'postgres'
        ApiUrls = 'http://0.0.0.0:5081'; SmtpHost = 'smtp.gmail.com'; SmtpPort = 587
        SmtpEnableSsl = $true; SmtpUser = ''; SmtpFrom = ''; PsqlPath = ''
    }
    $explicitPath = -not [string]::IsNullOrWhiteSpace($ConfigPath)
    if (-not $explicitPath) { $ConfigPath = Join-Path $PSScriptRoot 'local.settings.json' }
    if (Test-Path -LiteralPath $ConfigPath) {
        $local = Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($null -eq $local -or $local -is [array] -or $local -isnot [pscustomobject]) { throw 'Machine settings must be a JSON object.' }
        foreach ($property in $local.PSObject.Properties) {
            if (-not $settings.ContainsKey($property.Name)) { throw "Unknown machine setting: $($property.Name)" }
            $settings[$property.Name] = $property.Value
        }
    } elseif ($explicitPath) { throw "Machine settings not found: $ConfigPath" }
    $environment = @{
        DbHost='BCV_DB_HOST'; DbPort='BCV_DB_PORT'; DbName='BCV_DB_NAME'; DbUser='BCV_DB_USER'
        ApiUrls='BCV_API_URLS'; SmtpHost='BCV_SMTP_HOST'; SmtpPort='BCV_SMTP_PORT'
        SmtpEnableSsl='BCV_SMTP_ENABLE_SSL'; SmtpUser='BCV_SMTP_USER'; SmtpFrom='BCV_SMTP_FROM'; PsqlPath='BCV_PSQL_PATH'
    }
    foreach ($entry in $environment.GetEnumerator()) {
        $value = Get-BcvEnvironment $entry.Value
        if (-not [string]::IsNullOrEmpty($value)) { $settings[$entry.Key] = $value }
    }
    foreach ($name in @('DbPort', 'SmtpPort')) {
        $port = 0
        if (-not [int]::TryParse([string]$settings[$name], [ref]$port) -or $port -lt 1 -or $port -gt 65535) { throw "$name must be an integer from 1 to 65535." }
        $settings[$name] = $port
    }
    $ssl = $false
    if (-not [bool]::TryParse([string]$settings.SmtpEnableSsl, [ref]$ssl)) { throw 'SmtpEnableSsl must be true or false.' }
    $settings.SmtpEnableSsl = $ssl
    foreach ($name in @('DbHost', 'DbName', 'DbUser', 'ApiUrls')) {
        if ([string]::IsNullOrWhiteSpace([string]$settings[$name])) { throw "$name must not be empty." }
    }
    foreach ($url in ([string]$settings.ApiUrls).Split(';')) {
        $uri = $null
        if (-not [Uri]::TryCreate($url, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -notin @('http','https')) { throw 'ApiUrls must contain absolute HTTP/HTTPS URLs separated by semicolons.' }
    }
    return [pscustomobject]$settings
}

function New-BcvConnectionString($Config, [string]$Password) {
    $builder = [System.Data.Common.DbConnectionStringBuilder]::new()
    $builder.Add('Host', $Config.DbHost); $builder.Add('Port', $Config.DbPort)
    $builder.Add('Database', $Config.DbName); $builder.Add('Username', $Config.DbUser); $builder.Add('Password', $Password)
    return $builder.ConnectionString
}

function Resolve-BcvPsql($Config, [string]$Override = '') {
    $path = if ($Override) { $Override } else { [string]$Config.PsqlPath }
    if (-not $path) { $command = Get-Command psql -ErrorAction SilentlyContinue; if ($command) { $path = $command.Source } }
    if (-not $path -or -not (Test-Path -LiteralPath $path)) { throw 'Set PsqlPath in backend/local.settings.json, put psql on PATH, or pass -Psql with the executable path.' }
    return $path
}
