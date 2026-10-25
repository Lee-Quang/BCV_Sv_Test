# Stores the secrets run-local.ps1 needs as user-level environment variables, so it stops asking.
# Run once; run again to change a value. Leave a prompt empty to keep the current value.
#   .\backend\set-local-secrets.ps1        all values
#   .\backend\set-local-secrets.ps1 -R2    only the Cloudflare R2 values
param([switch]$R2)
$ErrorActionPreference = 'Stop'

function Set-Secret([string]$name, [string]$prompt, [string]$pattern = '', [string]$hint = '') {
    while ($true) {
        $secure = Read-Host $prompt -AsSecureString
        $value = [System.Net.NetworkCredential]::new('', $secure).Password.Trim()
        if (-not $value) {
            Write-Host "$name unchanged."
            return
        }
        if ($pattern -and $value -notmatch $pattern) {
            Write-Host "That does not look right ($($value.Length) characters). $hint" -ForegroundColor Yellow
            continue
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'User')
        Write-Host "$name saved."
        return
    }
}

function Set-Value([string]$name, [string]$prompt, [string]$pattern, [string]$hint) {
    while ($true) {
        $value = (Read-Host $prompt).Trim()
        if (-not $value) {
            Write-Host "$name unchanged."
            return
        }
        if ($value -notmatch $pattern) {
            Write-Host "That does not look right. $hint" -ForegroundColor Yellow
            continue
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'User')
        Write-Host "$name saved."
        return
    }
}

if (-not $R2) {
    Set-Secret 'BCV_DB_PASSWORD' 'PostgreSQL BCV password'
    Set-Secret 'BCV_SMTP_PASSWORD' 'SMTP App Password (sender configured in backend/local.settings.json)'
}

# Cloudflare R2 for avatars (dashboard: R2 > bucket > Settings, and R2 > Manage API tokens).
# Paste one value per prompt. In hidden prompts, paste with right-click (Ctrl+V may type nothing).
Set-Value 'BCV_R2_ACCOUNT_ID' 'Cloudflare Account ID (32 characters)' '^[0-9a-f]{32}$' `
    'Only the 32-character Account ID, without https:// or .r2.cloudflarestorage.com.'
Set-Value 'BCV_R2_BUCKET' 'R2 bucket name' '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$' `
    'Only the bucket name, e.g. bcv-avatars.'
# The S3 API endpoint (<account>.r2.cloudflarestorage.com) needs signed requests, so phones cannot load from it.
Set-Value 'BCV_R2_PUBLIC_URL' 'R2 public URL (https://pub-....r2.dev)' '^https://(?![^/]*\.r2\.cloudflarestorage\.com)[^\s/]+/?$' `
    'Use the bucket''s public URL (Settings > Public access > R2.dev subdomain or a custom domain), not the S3 API endpoint.'
Set-Secret 'BCV_R2_ACCESS_KEY_ID' 'R2 Access Key ID (hidden)' '^[0-9a-f]{32}$' `
    'The Access Key ID is 32 characters.'
Set-Secret 'BCV_R2_SECRET_ACCESS_KEY' 'R2 Secret Access Key (hidden)' '^[0-9a-f]{64}$' `
    'The Secret Access Key is 64 characters.'
Write-Host 'Done. Start the API with .\backend\run-local.ps1'
