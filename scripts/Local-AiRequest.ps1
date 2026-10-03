# Local-only HTTP transport shared by the explicit Probe and its local fault tests.
# It never reads a Provider Key and never follows redirects or uses a system proxy.
Add-Type -AssemblyName System.Net.Http

function Get-LocalTransportCategory($Failure) {
    $current = $Failure
    while ($null -ne $current) {
        if ($current -is [Security.Authentication.AuthenticationException]) { return 'LOCAL_TLS' }
        if ($current -is [Net.Sockets.SocketException]) {
            if ($current.SocketErrorCode -in @('HostNotFound','TryAgain','NoData')) { return 'LOCAL_DNS' }
            if ($current.SocketErrorCode -eq 'TimedOut') { return 'LOCAL_CONNECT_TIMEOUT' }
            return 'LOCAL_TCP'
        }
        if ($current -is [Net.WebException]) {
            switch ([string]$current.Status) {
                'NameResolutionFailure' { return 'LOCAL_DNS' }
                'ProxyNameResolutionFailure' { return 'LOCAL_PROXY' }
                'ConnectFailure' { return 'LOCAL_TCP' }
                'TrustFailure' { return 'LOCAL_TLS' }
                'SecureChannelFailure' { return 'LOCAL_TLS' }
                'Timeout' { return 'LOCAL_RESPONSE_TIMEOUT' }
            }
        }
        if ($current -is [OperationCanceledException]) { return 'LOCAL_RESPONSE_TIMEOUT' }
        $current = $current.InnerException
    }
    return 'LOCAL_TRANSPORT'
}

function Invoke-DeadlineLocalHttp {
    param(
        [Parameter(Mandatory=$true)][Uri]$Uri,
        [string]$Method = 'GET',
        [hashtable]$Headers = @{},
        [string]$JsonBody,
        [ValidateRange(1,45000)][int]$TimeoutMilliseconds = 5000
    )
    if (-not $Uri.IsAbsoluteUri -or -not $Uri.IsLoopback -or $Uri.Scheme -notin @('http','https') -or $Uri.UserInfo -or $Uri.Query -or $Uri.Fragment) {
        return [pscustomobject]@{StatusCode=0;Body=$null;Category='LOCAL_URL'}
    }
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $handler = $null; $client = $null; $request = $null; $response = $null; $cancellation = $null
    try {
        $handler = [Net.Http.HttpClientHandler]::new()
        $handler.UseProxy = $false
        $handler.AllowAutoRedirect = $false
        $handler.UseCookies = $false
        $client = [Net.Http.HttpClient]::new($handler)
        $client.Timeout = [TimeSpan]::FromMilliseconds($TimeoutMilliseconds)
        $cancellation = [Threading.CancellationTokenSource]::new()
        $cancellation.CancelAfter($TimeoutMilliseconds)
        $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),$Uri)
        $null = $request.Headers.TryAddWithoutValidation('Accept','application/json')
        foreach ($name in $Headers.Keys) { $null = $request.Headers.TryAddWithoutValidation([string]$name,[string]$Headers[$name]) }
        if ($null -ne $JsonBody -and $JsonBody.Length -gt 0) {
            $request.Content = [Net.Http.StringContent]::new($JsonBody,[Text.Encoding]::UTF8,'application/json')
        }
        # Task.Wait is the independent wall-clock guard; no PowerShell web-cmdlet timeout semantics are involved.
        $pending = $client.SendAsync($request,[Net.Http.HttpCompletionOption]::ResponseHeadersRead,$cancellation.Token)
        $remaining = [Math]::Max(1,$TimeoutMilliseconds-[int]$watch.ElapsedMilliseconds)
        if (-not $pending.Wait($remaining)) {
            $cancellation.Cancel()
            return [pscustomobject]@{StatusCode=0;Body=$null;Category='LOCAL_RESPONSE_TIMEOUT'}
        }
        $response = $pending.GetAwaiter().GetResult()
        $remaining = $TimeoutMilliseconds-[int]$watch.ElapsedMilliseconds
        if ($remaining -le 0) { return [pscustomobject]@{StatusCode=0;Body=$null;Category='LOCAL_READ_TIMEOUT'} }
        # Bound body size and body-read wait separately; a server that never closes its body cannot hang Probe.
        $buffer = $response.Content.LoadIntoBufferAsync(65536)
        if (-not $buffer.Wait($remaining)) {
            $cancellation.Cancel()
            return [pscustomobject]@{StatusCode=[int]$response.StatusCode;Body=$null;Category='LOCAL_READ_TIMEOUT'}
        }
        $null = $buffer.GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{StatusCode=[int]$response.StatusCode;Body=$body;Category=$null}
    } catch {
        return [pscustomobject]@{StatusCode=0;Body=$null;Category=(Get-LocalTransportCategory $_.Exception)}
    } finally {
        # Disposing aborts this request only. It never stops the backend, frontend or another process.
        foreach ($resource in @($response,$request,$client,$handler,$cancellation)) {
            if ($null -ne $resource) { try { $resource.Dispose() } catch { } }
        }
        $watch.Stop()
    }
}
