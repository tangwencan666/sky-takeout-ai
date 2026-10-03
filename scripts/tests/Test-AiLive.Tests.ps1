param([string]$ReportPath)
# Local TCP fixtures only: no running application, database, real account or Provider endpoint is contacted.
$ErrorActionPreference = 'Stop'
$scriptsPath = Split-Path -Parent $PSScriptRoot
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('sky-probe-test-'+[Guid]::NewGuid().ToString('N'))
$copiedScripts = Join-Path $fixtureRoot 'scripts'
$null = New-Item -ItemType Directory -Path $copiedScripts
Copy-Item -LiteralPath (Join-Path $scriptsPath 'Test-AiLive.ps1') -Destination $copiedScripts
Copy-Item -LiteralPath (Join-Path $scriptsPath 'Local-AiRequest.ps1') -Destination $copiedScripts
$shellPath = if (Test-Path -LiteralPath (Join-Path $PSHOME 'pwsh.exe')) { Join-Path $PSHOME 'pwsh.exe' } else { Join-Path $PSHOME 'powershell.exe' }
$results = [Collections.Generic.List[object]]::new()

Add-Type -TypeDefinition @'
using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.IO;
using System.Text.RegularExpressions;
public sealed class SkyProbeFixture : IDisposable {
    private readonly TcpListener listener;
    private readonly string mode;
    private volatile bool closed, verified;
    public int Requests, Logins, Probes;
    public int Port { get { return ((IPEndPoint)listener.LocalEndpoint).Port; } }
    public SkyProbeFixture(string mode) {
        this.mode=mode;listener=new TcpListener(IPAddress.Loopback,0);listener.Start();
        Task.Run(() => { while(!closed) { try { var client=listener.AcceptTcpClient();Task.Run(() => Handle(client)); } catch { if(closed)return; } } });
    }
    private void Handle(TcpClient client) {
        using(client) { try {
            var stream=client.GetStream();stream.ReadTimeout=3000;
            var header=new StringBuilder();while(header.Length<16384) { int b=stream.ReadByte();if(b<0)return;header.Append((char)b);if(header.ToString().EndsWith("\r\n\r\n"))break; }
            var raw=header.ToString();var length=Regex.Match(raw,@"(?im)^Content-Length:\s*(\d+)");
            if(length.Success)for(int i=0;i<Int32.Parse(length.Groups[1].Value);i++)if(stream.ReadByte()<0)return;
            Interlocked.Increment(ref Requests);var path=raw.Split(' ')[1];
            if(path.EndsWith("/user/auth/login"))Interlocked.Increment(ref Logins);
            if(path.EndsWith("/user/ai/probe"))Interlocked.Increment(ref Probes);
            if(mode=="hang-readiness" || mode=="hang-probe" && path.EndsWith("/user/ai/probe")) { Thread.Sleep(6000);return; }
            if(mode=="body-hang") { var bytes=Encoding.ASCII.GetBytes("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 9999\r\nConnection: close\r\n\r\n{");stream.Write(bytes,0,bytes.Length);stream.Flush();Thread.Sleep(6000);return; }
            if(mode=="redirect") { var bytes=Encoding.ASCII.GetBytes("HTTP/1.1 302 Found\r\nLocation: https://example.invalid/never-contact\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");stream.Write(bytes,0,bytes.Length);return; }
            int status=200;string data;
            if(path.EndsWith("/health/ready"))data="{\"status\":\"ready\",\"database\":\"sky_takeout_ai\"}";
            else if(path.EndsWith("/user/auth/login"))data="{\"token\":\"local-fixture-session\"}";
            else if(path.EndsWith("/user/ai/status"))data="{\"mode\":\"live\",\"keyConfigured\":true,\"provider\":\"deepseek\",\"model\":\"fixture\",\"instanceId\":\"fixture-instance\",\"providerVerified\":"+((verified||mode=="verified")?"true":"false")+"}";
            else if(path.EndsWith("/user/ai/probe") && mode=="provider-auth") { status=503;data="{\"category\":\"AUTH\",\"upstreamStatus\":401,\"retryable\":false}"; }
            else if(path.EndsWith("/user/ai/probe")) { verified=true;data="{\"mode\":\"live\",\"providerVerified\":true,\"instanceId\":\"fixture-instance\",\"message\":\"fixture-only-response\"}"; }
            else { status=404;data="null"; }
            var body=Encoding.UTF8.GetBytes("{\"code\":"+status+",\"message\":\"private-fixture-diagnostic\",\"data\":"+data+"}");
            var prefix=Encoding.ASCII.GetBytes("HTTP/1.1 "+status+" Fixture\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+body.Length+"\r\nConnection: close\r\n\r\n");
            stream.Write(prefix,0,prefix.Length);stream.Write(body,0,body.Length);
        } catch { } }
    }
    public void Dispose() { closed=true;listener.Stop(); }
}
'@

function Invoke-ProbeCase([string]$Mode,[string]$Expected,[int]$ExpectedProbes,[bool]$MissingPassword=$false) {
    $fixture = [SkyProbeFixture]::new($Mode)
    $process = $null
    try {
        $start = [Diagnostics.ProcessStartInfo]::new()
        $start.FileName = $shellPath
        # This flag applies only to the owned test child; no machine/user execution policy is changed.
        $start.Arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+(Join-Path $copiedScripts 'Test-AiLive.ps1')+'" -ApiUrl http://127.0.0.1:'+$fixture.Port+' -TotalTimeoutSeconds 3'
        $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
        $start.EnvironmentVariables.Remove('AI_API_KEY');$start.EnvironmentVariables.Remove('LLM_API_KEY')
        if ($MissingPassword) { $start.EnvironmentVariables.Remove('DEMO_PASSWORD') }
        else { $start.EnvironmentVariables['DEMO_PASSWORD']=[Guid]::NewGuid().ToString('N') }
        $timer=[Diagnostics.Stopwatch]::StartNew();$process=[Diagnostics.Process]::Start($start)
        $outputTask=$process.StandardOutput.ReadToEndAsync();$errorTask=$process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(8000)) { $process.Kill();throw 'Owned local test child exceeded its external safety deadline.' }
        $output=$outputTask.GetAwaiter().GetResult();$errors=$errorTask.GetAwaiter().GetResult();$timer.Stop()
        if ($output -notmatch [regex]::Escape($Expected)) { throw "Case $Mode did not produce its expected safe result. Output: $output" }
        if ($output.Contains('private-fixture-diagnostic') -or $output.Contains('local-fixture-session') -or $errors) { throw 'Fixture diagnostics or session data escaped into output.' }
        if ($fixture.Probes -ne $ExpectedProbes) { throw "Case $Mode sent an unexpected number of Probe requests." }
        if ($MissingPassword -and $fixture.Requests -ne 0) { throw 'Missing credentials must fail before any HTTP request.' }
        $record=Get-Content -LiteralPath (Join-Path $fixtureRoot 'logs/ai-live/probe-result.json') -Raw | ConvertFrom-Json
        if ($record.elapsedMilliseconds -gt 2700) { throw "Case $Mode exceeded its script wall-clock budget." }
        $expectedExit = if ($Expected.StartsWith('LIVE_PROBE=PASS')) { 0 } else { 1 }
        if ($process.ExitCode -ne $expectedExit) { throw 'Probe exit status did not match its safe terminal result.' }
        $results.Add([pscustomobject]@{case=$Mode;missingPassword=$MissingPassword;result='PASS';elapsedMilliseconds=$record.elapsedMilliseconds;localProbeRequests=$fixture.Probes;localHttpRequests=$fixture.Requests;category=$record.category;exitCode=$process.ExitCode})
        Write-Output "PASS case=$Mode missingPassword=$MissingPassword elapsedMs=$($record.elapsedMilliseconds) localProbes=$($fixture.Probes)"
    } finally {
        if ($null -ne $process) { $process.Dispose() }
        $fixture.Dispose()
    }
}

try {
    Invoke-ProbeCase 'success' 'LIVE_PROBE=FAIL category=LOCAL_CREDENTIALS_MISSING' 0 $true
    Invoke-ProbeCase 'hang-readiness' 'LIVE_PROBE=FAIL category=LOCAL_RESPONSE_TIMEOUT' 0
    Invoke-ProbeCase 'body-hang' 'LIVE_PROBE=FAIL category=LOCAL_READ_TIMEOUT' 0
    Invoke-ProbeCase 'hang-probe' 'LIVE_PROBE=FAIL category=LOCAL_RESPONSE_TIMEOUT' 1
    Invoke-ProbeCase 'provider-auth' 'LIVE_PROBE=FAIL category=AUTH' 1
    Invoke-ProbeCase 'success' 'LIVE_PROBE=PASS' 1
    Invoke-ProbeCase 'verified' 'LIVE_PROBE=PASS' 0
    Invoke-ProbeCase 'redirect' 'LIVE_PROBE=FAIL category=LOCAL_REDIRECT' 0
    Write-Output 'LOCAL_PROBE_FAULT_TESTS=PASS tests=8 providerRequests=0'
    if ($ReportPath) {
        [pscustomobject]@{powerShell=$PSVersionTable.PSVersion.ToString();result='PASS';tests=$results.Count;providerRequests=0;scriptTotalTimeoutSeconds=3;results=@($results.ToArray())} |
            ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
    }
} finally {
    # Delete only the exact known files generated by this test; no recursive delete or process-wide kill.
    foreach ($path in @((Join-Path $copiedScripts 'Test-AiLive.ps1'),(Join-Path $copiedScripts 'Local-AiRequest.ps1'),(Join-Path $fixtureRoot 'logs/ai-live/probe-result.json'))) {
        if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path }
    }
}
