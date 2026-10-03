param(
    [ValidateSet('Probe')][string]$Stage = 'Probe',
    [string]$ApiUrl = 'http://127.0.0.1:15173/api',
    [string]$Username = 'demo_user',
    [ValidateRange(1,45)][int]$TotalTimeoutSeconds = 45
)
# Explicit verification only. Never invoked by Maven; never reads or prints a Provider Key.
$ErrorActionPreference = 'Stop'
$watch = [Diagnostics.Stopwatch]::StartNew()
# Leave one second for aborting/disposing a legacy .NET Framework HTTP response and writing the safe result.
$totalBudgetMilliseconds = [Math]::Max(250,$TotalTimeoutSeconds*1000-1000)
$projectPath = Split-Path -Parent $PSScriptRoot
$headers = @{}
$password = $null
$login = $null
$status = $null
$phase = 'CONFIGURATION'
$sent = 0
$passed = $false
$record = $null
$providerCategories = @('DNS','TCP','TLS','PROXY','CONNECT_TIMEOUT','READ_TIMEOUT','TOTAL_TIMEOUT','AUTH','BALANCE','PERMISSION','ENDPOINT','REQUEST_FORMAT','MODEL_NOT_FOUND','RATE_LIMIT','PROVIDER_5XX','JSON','ADAPTER','MISSING_KEY','IN_PROGRESS','LOCAL_BUSY','CANCELLED','EMPTY_RESPONSE')

function New-ProbeFailure([string]$Category, [int]$HttpStatus = 0, [int]$UpstreamStatus = 0) {
    $failure = [InvalidOperationException]::new('The live Probe stopped safely without retry.')
    $failure.Data['category'] = $Category
    $failure.Data['httpStatus'] = $HttpStatus
    $failure.Data['upstreamStatus'] = $UpstreamStatus
    return $failure
}

function Invoke-LocalApi([string]$Path, [string]$Method = 'GET', $Body = $null, [int]$LimitMilliseconds = 5000) {
    $remaining = $totalBudgetMilliseconds-[int]$watch.ElapsedMilliseconds
    if ($remaining -le 0) { throw (New-ProbeFailure 'SCRIPT_TOTAL_TIMEOUT') }
    $limit = [Math]::Min($remaining,$LimitMilliseconds)
    $json = $null
    if ($null -ne $Body) { $json = $Body | ConvertTo-Json -Compress -Depth 6 }
    $reply = Invoke-DeadlineLocalHttp -Uri ($ApiUrl+$Path) -Method $Method -Headers $headers -JsonBody $json -TimeoutMilliseconds $limit
    $json = $null
    if ($reply.Category) { throw (New-ProbeFailure $reply.Category $reply.StatusCode) }
    $payload = $null
    try { $payload = $reply.Body | ConvertFrom-Json -ErrorAction Stop } catch { }
    $reply.Body = $null
    if ($reply.StatusCode -ne 200 -or $null -eq $payload -or $payload.code -ne 200) {
        $category = switch ($reply.StatusCode) {
            { $_ -ge 300 -and $_ -lt 400 } { 'LOCAL_REDIRECT' }
            401 { 'LOCAL_AUTH' }
            403 { 'LOCAL_PERMISSION' }
            404 { 'LOCAL_ENDPOINT' }
            429 { 'LOCAL_RATE_LIMIT' }
            502 { 'LOCAL_PROXY' }
            504 { 'LOCAL_PROXY' }
            default { if ($null -eq $payload) { 'LOCAL_JSON' } elseif ($Path -eq '/health/ready') { 'LOCAL_DATABASE' } else { 'LOCAL_BACKEND' } }
        }
        $upstream = 0
        if ($payload -and $payload.data -and $providerCategories -contains [string]$payload.data.category) {
            $category = [string]$payload.data.category
            $parsedStatus = 0
            if ([int]::TryParse([string]$payload.data.upstreamStatus,[ref]$parsedStatus) -and $parsedStatus -ge 100 -and $parsedStatus -le 599) { $upstream = $parsedStatus }
        }
        # Never print response messages, headers or raw upstream bodies; only allowlisted categories and status codes.
        throw (New-ProbeFailure $category $reply.StatusCode $upstream)
    }
    return $payload.data
}

try {
    . (Join-Path $PSScriptRoot 'Local-AiRequest.ps1')
    $uri = $null
    if (-not [Uri]::TryCreate($ApiUrl,[UriKind]::Absolute,[ref]$uri) -or -not $uri.IsLoopback -or
        $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) { throw (New-ProbeFailure 'LOCAL_URL') }
    $ApiUrl = $ApiUrl.TrimEnd('/')
    $password = $env:DEMO_PASSWORD
    if ([string]::IsNullOrEmpty($password)) {
        # A hidden Read-Host prompt used to look like a stuck network request and had no deadline.
        throw (New-ProbeFailure 'LOCAL_CREDENTIALS_MISSING')
    }
    $phase = 'READINESS'; Write-Host 'LIVE_PROBE_PROGRESS stage=READINESS timeoutSeconds=5'
    $ready = Invoke-LocalApi '/health/ready'
    if ($ready.status -ne 'ready' -or $ready.database -ne 'sky_takeout_ai') { throw (New-ProbeFailure 'LOCAL_DATABASE') }
    $phase = 'LOGIN'; Write-Host 'LIVE_PROBE_PROGRESS stage=LOGIN credentialSource=DEMO_PASSWORD timeoutSeconds=5'
    $login = Invoke-LocalApi '/user/auth/login' 'POST' @{username=$Username;password=$password}
    $password = $null
    if (-not $login.token) { throw (New-ProbeFailure 'LOCAL_LOGIN_CONTRACT') }
    $headers.Authorization = 'Bearer '+$login.token
    $login = $null
    $phase = 'AI_STATUS'; Write-Host 'LIVE_PROBE_PROGRESS stage=AI_STATUS timeoutSeconds=5'
    $status = Invoke-LocalApi '/user/ai/status'
    if ($status.mode -ne 'live') { throw (New-ProbeFailure 'MODE_NOT_LIVE') }
    if (-not $status.keyConfigured) { throw (New-ProbeFailure 'MISSING_KEY') }
    if ($status.provider -ne 'deepseek') { throw (New-ProbeFailure 'PROVIDER_MISMATCH') }
    if (-not $status.instanceId) { throw (New-ProbeFailure 'LOCAL_BACKEND_OUTDATED') }
    if (-not $status.providerVerified) {
        $phase = 'PROBE'; Write-Host "LIVE_PROBE_PROGRESS stage=PROBE maxWaitSeconds=40 totalDeadlineSeconds=$TotalTimeoutSeconds retries=0"
        if ($watch.ElapsedMilliseconds -ge $totalBudgetMilliseconds) { throw (New-ProbeFailure 'SCRIPT_TOTAL_TIMEOUT') }
        $sent = 1
        # Exactly one POST. Backend's 35-second deadline normally returns its precise safe category first.
        $greeting = Invoke-LocalApi '/user/ai/probe' 'POST' $null 40000
        if ($greeting.mode -ne 'live' -or -not $greeting.providerVerified -or [string]::IsNullOrWhiteSpace($greeting.message)) { throw (New-ProbeFailure 'EMPTY_RESPONSE') }
        if ($greeting.instanceId -ne $status.instanceId) { throw (New-ProbeFailure 'LOCAL_INSTANCE_CHANGED') }
        $greeting = $null
        $phase = 'VERIFY'; Write-Host 'LIVE_PROBE_PROGRESS stage=VERIFY timeoutSeconds=3'
        $after = Invoke-LocalApi '/user/ai/status' 'GET' $null 3000
        if ($after.instanceId -ne $status.instanceId -or -not $after.providerVerified) { throw (New-ProbeFailure 'LOCAL_INSTANCE_CHANGED') }
        $status = $after
    }
    $passed = $true
    $record = [pscustomobject]@{verifiedAt=(Get-Date -Format o);stage='Probe';result='PASS';instanceId=$status.instanceId;provider=$status.provider;model=$status.model;mode=$status.mode;greetingRequestsSent=$sent;elapsedMilliseconds=$watch.ElapsedMilliseconds;subsequentStagesRun=$false}
    Write-Host "LIVE_PROBE=PASS provider=$($status.provider) model=$($status.model) greetingRequestsSent=$sent"
    Write-Host 'Probe finished. No Key, JWT or response text is printed or saved; no later AI stage was called by this script.'
} catch {
    $category = 'SCRIPT_RUNTIME'
    $httpStatus = 0; $upstreamStatus = 0
    if ($_.Exception.Data.Contains('category')) {
        $category = [string]$_.Exception.Data['category']
        $httpStatus = [int]$_.Exception.Data['httpStatus']
        $upstreamStatus = [int]$_.Exception.Data['upstreamStatus']
    }
    $record = [pscustomobject]@{verifiedAt=(Get-Date -Format o);stage=$phase;result='FAIL';category=$category;httpStatus=$httpStatus;upstreamStatus=$upstreamStatus;greetingRequestsSent=$sent;elapsedMilliseconds=$watch.ElapsedMilliseconds;subsequentStagesRun=$false}
    Write-Host "LIVE_PROBE=FAIL category=$category stage=$phase httpStatus=$httpStatus upstreamStatus=$upstreamStatus greetingRequestsSent=$sent"
    if ($category -eq 'LOCAL_CREDENTIALS_MISSING') { Write-Host 'DEMO_PASSWORD is missing from this process. No password prompt or Provider request was started.' }
    Write-Host 'Stopped without retry. A local timeout does not authorize another paid request; inspect backend status/logs first.'
} finally {
    $headers.Clear(); $password = $null; $login = $null
    $watch.Stop()
}
try {
    $logPath = Join-Path $projectPath 'logs/ai-live'
    New-Item -ItemType Directory -Force -Path $logPath | Out-Null
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logPath 'probe-result.json') -Encoding UTF8
} catch { Write-Host 'PROBE_RECORD=NOT_SAVED; the safe terminal result above remains authoritative.' }
if (-not $passed) { exit 1 }
$global:LASTEXITCODE = 0
