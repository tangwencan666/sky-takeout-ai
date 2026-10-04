param(
    [string]$LegacyEnvPath = $env:SKY_LEGACY_ENV_PATH,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipProbe
)
# Explicit local operation; never runs during ordinary application startup or Maven tests.
$ErrorActionPreference = 'Stop'
# Optional ignored machine settings contain paths only, never credentials. Explicit parameters
# and environment variables take precedence; a fresh clone requires no legacy project.
$localSettingsPath = Join-Path (Split-Path -Parent $PSScriptRoot) '.local/startup.json'
if (Test-Path -LiteralPath $localSettingsPath -PathType Leaf) {
    $localSettings = Get-Content -LiteralPath $localSettingsPath -Raw | ConvertFrom-Json
    if (-not $LegacyEnvPath) { $LegacyEnvPath = [string]$localSettings.LegacyEnvPath }
    if (-not $JavaHome) { $JavaHome = [string]$localSettings.JavaHome }
}
$names = @('AI_API_KEY','AI_BASE_URL','AI_MODEL','AI_MODE')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    if ([string]::IsNullOrWhiteSpace($env:AI_API_KEY)) {
        if ([string]::IsNullOrWhiteSpace($LegacyEnvPath) -or -not (Test-Path -LiteralPath $LegacyEnvPath -PathType Leaf)) { throw 'KEY_SOURCE_MISSING: set AI_API_KEY in the process environment or explicitly pass -LegacyEnvPath; never send a Key to chat.' }
        $legacy = @{}
        foreach ($line in [IO.File]::ReadAllLines($LegacyEnvPath)) {
            if ($line -match '^\s*(?:export\s+)?(LLM_API_KEY|LLM_BASE_URL|LLM_MODEL)\s*=\s*(.*)$') {
                $name = $Matches[1]; $value = $Matches[2].Trim()
                if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                    $value = $value.Substring(1,$value.Length-2)
                } else { $value = ($value -replace '\s+#.*$','').Trim() }
                $legacy[$name] = $value
            }
        }
        $base = $null
        if (-not [Uri]::TryCreate([string]$legacy.LLM_BASE_URL,[UriKind]::Absolute,[ref]$base) -or
            $base.Scheme -ne 'https' -or $base.Host -ne 'api.deepseek.com' -or $base.UserInfo -or $base.Query -or $base.Fragment) {
            throw 'KEY_SOURCE_PROVIDER_MISMATCH: the selected legacy configuration is not the official DeepSeek service; no Key was forwarded.'
        }
        if ([string]::IsNullOrWhiteSpace([string]$legacy.LLM_API_KEY)) { throw 'KEY_MISSING: the authorized file has no LLM_API_KEY.' }
        $env:AI_API_KEY = $legacy.LLM_API_KEY
        $legacy.Clear(); $value=$null; $line=$null
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { throw 'DB_CREDENTIAL_MISSING: DB_PASSWORD must already be loaded securely in this process.' }
    if (-not $SkipProbe -and [string]::IsNullOrWhiteSpace($env:DEMO_PASSWORD)) { throw 'DEMO_CREDENTIAL_MISSING: load the existing development account password as DEMO_PASSWORD; there is no hidden input wait.' }
    # Keep the old project read-only. Its retired model setting is not imported into this project.
    $env:AI_MODE = 'live'
    $env:AI_BASE_URL = 'https://api.deepseek.com'
    $env:AI_MODEL = 'deepseek-flash'
    & (Join-Path $PSScriptRoot 'Stop-Preview.ps1')
    & (Join-Path $PSScriptRoot 'Start-Preview.ps1') -JavaHome $JavaHome -AiMode live
    if (-not $SkipProbe) {
        $global:LASTEXITCODE = 0
        & (Join-Path $PSScriptRoot 'Test-AiLive.ps1') -Stage Probe
        if ($LASTEXITCODE -ne 0) { throw 'LIVE_PROBE_FAILED: see the safe category above; no retry was sent.' }
    }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    if ($legacy) { $legacy.Clear() }
    $previous.Clear(); $value=$null; $line=$null
}
