param([string]$Email = 'admin@bcv.local', [string]$ConfigPath = '')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-config.ps1')
$config = Get-BcvLocalConfig $ConfigPath
$password = Get-BcvEnvironment 'BCV_DB_PASSWORD'
if (-not $password) { $password = [System.Net.NetworkCredential]::new('', (Read-Host 'PostgreSQL password' -AsSecureString)).Password }
$env:ConnectionStrings__Bcv = New-BcvConnectionString $config $password
$env:BCV_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'New admin password (at least 12 characters, letters and digits)' -AsSecureString)).Password
try {
    dotnet build (Join-Path $PSScriptRoot 'Bcv.Api/Bcv.Api.csproj') --verbosity quiet -p:UseAppHost=false
    if ($LASTEXITCODE -ne 0) { throw 'Build failed. Stop the running backend and retry.' }
    dotnet (Join-Path $PSScriptRoot 'Bcv.Api/bin/Debug/net10.0/Bcv.Api.dll') --create-admin $Email
    if ($LASTEXITCODE -ne 0) { throw 'Could not create admin.' }
} finally {
    Remove-Item Env:BCV_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:ConnectionStrings__Bcv -ErrorAction SilentlyContinue
}
