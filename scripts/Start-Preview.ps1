param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateRange(1024,65535)][int]$BackendPort = 8080,
    [ValidateRange(1024,65535)][int]$FrontendPort = 15173,
    [ValidateSet('mock','live')][string]$AiMode = $(if ($env:AI_MODE) { $env:AI_MODE } else { 'mock' })
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectPath 'sky-server/target/sky-server-0.0.1-SNAPSHOT.jar'
$frontendPath = Join-Path $projectPath 'sky-web'
$vitePath = Join-Path $frontendPath 'node_modules/vite/bin/vite.js'
$javaPath = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { (Get-Command java -CommandType Application | Select-Object -First 1).Source }
$nodePath = (Get-Command node -CommandType Application | Select-Object -First 1).Source
if (-not (Test-Path -LiteralPath $javaPath)) { throw 'Java executable not found. Pass -JavaHome with your JDK 17 directory.' }
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build the backend with mvn package before starting the preview.' }
if (-not (Test-Path -LiteralPath $vitePath)) { throw 'Install the locked frontend dependencies with npm ci first.' }
if (-not $env:DB_PASSWORD) { throw 'Set DB_PASSWORD securely in this terminal first. The script never loads or saves password files.' }
if ($BackendPort -eq $FrontendPort) { throw 'Backend and frontend ports must differ.' }
if ($AiMode -eq 'live') {
    foreach ($name in @('AI_API_KEY','AI_BASE_URL','AI_MODEL')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            throw "Live mode requires $name in this terminal. Values are never printed."
        }
    }
    $providerUri = $null
    if (-not [Uri]::TryCreate($env:AI_BASE_URL, [UriKind]::Absolute, [ref]$providerUri) -or
        $providerUri.UserInfo -or $providerUri.Query -or $providerUri.Fragment -or
        ($providerUri.Scheme -ne 'https' -and -not ($providerUri.Scheme -eq 'http' -and $providerUri.IsLoopback))) {
        throw 'AI_BASE_URL must be an HTTPS API base without embedded credentials, query or fragment (HTTP only for local testing).'
    }
    if ($providerUri.Host -eq 'api.deepseek.com') {
        if ($providerUri.AbsolutePath.TrimEnd('/') -notin @('', '/v1')) { throw 'DeepSeek AI_BASE_URL must be https://api.deepseek.com or https://api.deepseek.com/v1.' }
        if ($env:AI_MODEL -in @('deepseek-chat','deepseek-reasoner')) { throw 'DeepSeek retired these legacy model names. Set AI_MODEL=deepseek-flash (or an explicitly selected current official model). Your Key is unchanged.' }
    }
}

function Assert-PortFree([int]$Port) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() }
    catch { throw "Port $Port is already occupied. Identify and stop only the intended project process before retrying." }
    finally { $listener.Stop() }
}
function Wait-Ready([string]$Url, $Process) {
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    $requestArguments = @{ Uri=$Url; TimeoutSec=3; UseBasicParsing=$true; MaximumRedirection=0; ErrorAction='Stop' }
    # Keep readiness bounded on both Windows PowerShell 5.1 and modern PowerShell with split timeouts.
    if ((Get-Command Invoke-RestMethod).Parameters.ContainsKey('OperationTimeoutSeconds')) { $requestArguments.OperationTimeoutSeconds = 3 }
    do {
        $Process.Refresh()
        if ($Process.HasExited) { throw 'Preview process exited; inspect the local preview log.' }
        try {
            $response = Invoke-RestMethod @requestArguments
            if ($response.code -eq 200 -and $response.data.status -eq 'ready') { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Database readiness did not pass. Check the current terminal DB connection settings; /health alone is not sufficient.'
}
Assert-PortFree $BackendPort
Assert-PortFree $FrontendPort

$logPath = Join-Path $projectPath 'logs/preview'
New-Item -ItemType Directory -Force -Path $logPath | Out-Null
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$environmentNames = @('SERVER_PORT','API_PROXY_TARGET','JWT_SECRET','AI_MODE','CACHE_ENABLED')
$previous = @{}
foreach ($name in $environmentNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$backend = $null
$frontend = $null
try {
    # Both children inherit this process environment; no secret is passed in arguments or saved.
    $env:SERVER_PORT = [string]$BackendPort
    $env:API_PROXY_TARGET = "http://127.0.0.1:$BackendPort"
    # Respect the explicit mode; never silently turn a live terminal back into Mock.
    $env:AI_MODE = $AiMode.ToLowerInvariant()
    if (-not $env:CACHE_ENABLED) { $env:CACHE_ENABLED = 'true' }
    if (-not $env:JWT_SECRET) {
        $key = New-Object byte[] 32
        $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $generator.GetBytes($key); $env:JWT_SECRET = [Convert]::ToBase64String($key) }
        finally { $generator.Dispose(); [Array]::Clear($key, 0, $key.Length) }
    }
    $backend = Start-Process -FilePath $javaPath -ArgumentList @('-jar', ('"' + $jarPath + '"')) -WorkingDirectory $projectPath -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logPath "$runId-backend.log") -RedirectStandardError (Join-Path $logPath "$runId-backend-error.log")
    Wait-Ready "http://127.0.0.1:$BackendPort/health/ready" $backend
    $frontend = Start-Process -FilePath $nodePath -ArgumentList @(('"' + $vitePath + '"'), '--host','127.0.0.1','--port',[string]$FrontendPort,'--strictPort') -WorkingDirectory $frontendPath -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logPath "$runId-frontend.log") -RedirectStandardError (Join-Path $logPath "$runId-frontend-error.log")
    # Verify the browser's actual same-origin proxy reaches the same ready backend.
    Wait-Ready "http://127.0.0.1:$FrontendPort/api/health/ready" $frontend
    [pscustomobject]@{startedAt=(Get-Date -Format o); backendPid=$backend.Id; frontendPid=$frontend.Id; backendPort=$BackendPort; frontendPort=$FrontendPort; projectPath=$projectPath; aiMode=$env:AI_MODE} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logPath 'processes.json') -Encoding UTF8
    Write-Host "Preview ready: http://127.0.0.1:$FrontendPort -> /api -> http://127.0.0.1:$BackendPort"
    Write-Host "Database readiness passed for sky_takeout_ai. Backend PID=$($backend.Id), frontend PID=$($frontend.Id). AI_MODE=$env:AI_MODE."
    if ($AiMode -eq 'live') { Write-Host 'Provider configured, not yet verified. Run Test-AiLive.ps1 -Stage Probe for one minimal greeting; no paid request was made during startup.' }
} catch {
    # Only processes created by this invocation are stopped; existing listeners were rejected above.
    foreach ($child in @($frontend,$backend)) {
        if ($null -ne $child) { $child.Refresh(); if (-not $child.HasExited) { Stop-Process -Id $child.Id -Force } }
    }
    throw
} finally {
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
