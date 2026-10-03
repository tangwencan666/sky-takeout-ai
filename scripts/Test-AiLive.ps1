param(
    [ValidateSet('Probe')][string]$Stage = 'Probe',
    [string]$ApiUrl = 'http://127.0.0.1:15173/api',
    [string]$Username = 'demo_user'
)
# Explicit live verification only. Maven/Surefire never invokes this script.
# No Provider Key is read here: the running backend inherited it from the user's terminal.
$ErrorActionPreference = 'Stop'
$uri = $null
if (-not [Uri]::TryCreate($ApiUrl, [UriKind]::Absolute, [ref]$uri) -or -not $uri.IsLoopback -or
    $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
    throw 'Use the local preview API URL without embedded credentials, query or fragment.'
}
$ApiUrl = $ApiUrl.TrimEnd('/')
$projectPath = Split-Path -Parent $PSScriptRoot
$headers = @{}
$restParameters = (Get-Command Invoke-RestMethod).Parameters
function Invoke-LocalApi([string]$Path, [string]$Method = 'GET', $Body = $null) {
    # Basic parsing avoids the legacy Windows PowerShell page parser. Never redirect local credentials or probes.
    $arguments = @{ Uri=($ApiUrl+$Path); Method=$Method; Headers=$headers; TimeoutSec=55; UseBasicParsing=$true; MaximumRedirection=0; ErrorAction='Stop' }
    # PowerShell 7.6 treats TimeoutSec as connection timeout only; bound response reads too when supported.
    if ($restParameters.ContainsKey('OperationTimeoutSeconds')) { $arguments.OperationTimeoutSeconds = 55 }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress -Depth 6))
    }
    try { $response = Invoke-RestMethod @arguments }
    catch {
        $code = if ($null -ne $_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'unreachable' }
        throw "Local API $Path failed (HTTP $code). Stopped without retry; no credentials or upstream error body are printed."
    }
    if ($response.code -ne 200) { throw "Local API $Path did not succeed. No retry was sent." }
    return $response.data
}
$password = $env:DEMO_PASSWORD
$securePassword = $null
try {
    $ready = Invoke-LocalApi '/health/ready'
    if ($ready.status -ne 'ready' -or $ready.database -ne 'sky_takeout_ai') { throw 'The expected project database is not ready.' }
    if (-not $password) {
        $securePassword = Read-Host 'Development account password (not the API Key)' -AsSecureString
        $password = [Net.NetworkCredential]::new('', $securePassword).Password
    }
    $login = Invoke-LocalApi '/user/auth/login' 'POST' @{username=$Username;password=$password}
    if (-not $login.token) { throw 'Local login did not return a token.' }
    $headers.Authorization = 'Bearer ' + $login.token
    $login = $null
    $password = $null
    $status = Invoke-LocalApi '/user/ai/status'
    if ($status.mode -ne 'live') { throw 'Backend is still Mock. Restart from the terminal with AI_MODE=live or Start-Preview.ps1 -AiMode live.' }
    if (-not $status.keyConfigured) { throw 'The backend did not inherit AI_API_KEY. Restart it from the terminal where the Key is already loaded; do not print the Key.' }
    if ($status.provider -ne 'deepseek') { throw 'This verification targets the official DeepSeek endpoint. Check AI_BASE_URL without printing any Key.' }
    if (-not $status.instanceId) { throw 'Backend is outdated; rebuild/restart before paid verification.' }
    $greeting = $null
    $sent = 0
    if (-not $status.providerVerified) {
        # Fixed greeting only: backend caps output at 64 tokens and disables tools/history/retries.
        $greeting = Invoke-LocalApi '/user/ai/probe' 'POST'
        $sent = 1
        if ($greeting.mode -ne 'live' -or -not $greeting.providerVerified -or [string]::IsNullOrWhiteSpace($greeting.message)) {
            throw 'The single greeting was not verified as a real Provider success. Stop here.'
        }
        $after = Invoke-LocalApi '/user/ai/status'
        if ($after.instanceId -ne $status.instanceId -or -not $after.providerVerified) { throw 'Backend instance changed during verification. Stop here; no retry was sent.' }
        $status = $after
    }
    $record = [pscustomobject]@{verifiedAt=(Get-Date -Format o); stage='Probe'; result='PASS'; instanceId=$status.instanceId; provider=$status.provider; model=$status.model; mode=$status.mode; greetingRequestsSent=$sent; subsequentStagesRun=$false}
    $logPath = Join-Path $projectPath 'logs/ai-live'
    New-Item -ItemType Directory -Force -Path $logPath | Out-Null
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logPath 'probe-result.json') -Encoding UTF8
    Write-Host "LIVE_PROBE=PASS provider=$($status.provider) model=$($status.model) greetingRequestsSent=$sent"
    if ($greeting) { Write-Host ('Reply: ' + $greeting.message.Substring(0, [Math]::Min(300, $greeting.message.Length))) }
    else { Write-Host 'This backend instance was already verified; no additional paid greeting was sent.' }
    Write-Host 'Stopped after the minimal probe. Dish Tools -> Memory -> Structured Output -> RAG -> Order Tool -> SSE remain separate, sequential checks.'
    Write-Host 'Refresh the AI page status: it should show real model, verified. No API Key or JWT was saved.'
} finally {
    $headers.Clear()
    $password = $null
    if ($securePassword) { $securePassword.Dispose() }
}
