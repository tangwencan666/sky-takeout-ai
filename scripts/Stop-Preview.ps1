[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param()

$ErrorActionPreference = 'Stop'
$projectPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$recordPath = Join-Path $projectPath 'logs/preview/processes.json'
if (-not (Test-Path -LiteralPath $recordPath -PathType Leaf)) {
    Write-Host 'No preview process record exists; no process was stopped.'
    return
}
try { $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json }
catch { throw 'The preview process record is invalid; no process was stopped.' }
if (-not [string]::Equals([string]$record.projectPath, $projectPath, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The preview record belongs to a different project; no process was stopped.'
}
$startedAt = [DateTimeOffset]::MinValue
if (-not [DateTimeOffset]::TryParse([string]$record.startedAt, [ref]$startedAt)) {
    throw 'The preview record has no valid start time; no process was stopped.'
}

function Get-ProcessSnapshot([int]$CandidateId) {
    try { return Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $CandidateId" -ErrorAction Stop }
    catch { throw 'Cannot verify the recorded process safely; no unverified process will be stopped.' }
}

function Get-ExpectedCommandPattern([string]$Path, [bool]$Backend) {
    # Accept Windows path separators in either form, but require the complete argument, not a substring.
    $escapedPath = (($Path -split '[\\/]') | ForEach-Object { [regex]::Escape($_) }) -join '[\\/]'
    $argument = '(?:"' + $escapedPath + '"|' + $escapedPath + ')(?=\s|$)'
    $prefix = '^\s*(?:"[^"]+"|\S+)\s+'
    if ($Backend) { return $prefix + '-jar\s+' + $argument }
    return $prefix + $argument
}

function Assert-ProcessIdentity($Snapshot, $Expected) {
    if (-not [string]::Equals([string]$Snapshot.Name, $Expected.Executable, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Recorded $($Expected.Label) PID does not match the expected executable; refusing to stop it."
    }
    if (-not $Snapshot.CommandLine -or -not [regex]::IsMatch([string]$Snapshot.CommandLine, $Expected.Pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
        throw "Recorded $($Expected.Label) PID does not run this project's exact entry point; refusing to stop it."
    }
    if (-not $Snapshot.CreationDate) {
        throw "Cannot verify the recorded $($Expected.Label) process creation time; refusing to stop it."
    }
    $created = ([DateTime]$Snapshot.CreationDate).ToUniversalTime()
    # Start-Preview records time after readiness; both children must belong to that bounded startup interval.
    if ($created -gt $startedAt.UtcDateTime.AddSeconds(2) -or $created -lt $startedAt.UtcDateTime.AddMinutes(-5)) {
        throw "Recorded $($Expected.Label) PID has been reused or its creation time is inconsistent; refusing to stop it."
    }
}

$backendId = 0
$frontendId = 0
if (-not [int]::TryParse([string]$record.backendPid, [ref]$backendId) -or $backendId -le 0 -or
    -not [int]::TryParse([string]$record.frontendPid, [ref]$frontendId) -or $frontendId -le 0 -or $backendId -eq $frontendId) {
    throw 'The preview record has invalid process IDs; no process was stopped.'
}
$expectedProcesses = @(
    [pscustomobject]@{ Label = 'frontend'; Id = $frontendId; Executable = 'node.exe'; Pattern = (Get-ExpectedCommandPattern (Join-Path $projectPath 'sky-web/node_modules/vite/bin/vite.js') $false) },
    [pscustomobject]@{ Label = 'backend'; Id = $backendId; Executable = 'java.exe'; Pattern = (Get-ExpectedCommandPattern (Join-Path $projectPath 'sky-server/target/sky-server-0.0.1-SNAPSHOT.jar') $true) }
)

# Verify every live candidate before stopping either child, avoiding a partial stop on a known mismatch.
$verified = @()
foreach ($expected in $expectedProcesses) {
    $snapshot = Get-ProcessSnapshot $expected.Id
    if ($null -eq $snapshot) {
        Write-Host "Recorded $($expected.Label) process has already exited."
        continue
    }
    Assert-ProcessIdentity $snapshot $expected
    $verified += [pscustomobject]@{ Expected = $expected; CreationDate = $snapshot.CreationDate }
}
foreach ($candidate in $verified) {
    $expected = $candidate.Expected
    $current = Get-ProcessSnapshot $expected.Id
    if ($null -eq $current) { continue }
    Assert-ProcessIdentity $current $expected
    if ($current.CreationDate -ne $candidate.CreationDate) {
        throw 'A recorded process changed during verification; refusing to stop the replacement.'
    }
    if ($PSCmdlet.ShouldProcess("$($expected.Label) preview PID $($expected.Id)", 'Stop verified project process')) {
        Stop-Process -Id $expected.Id -Force -ErrorAction Stop
        Write-Host "Stopped verified $($expected.Label) preview process PID=$($expected.Id)."
    }
}
