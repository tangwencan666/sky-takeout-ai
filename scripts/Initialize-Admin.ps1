param([Parameter(Mandatory=$true)][string]$Username)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectPath 'sky-server/target/sky-server-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw '请先运行 mvn package。' }
if (-not $env:DB_PASSWORD) { throw '请先在当前终端安全设置 DB_PASSWORD，脚本不读取或保存密码文件。' }
$securePassword = Read-Host '输入新管理员密码（12至64字符，UTF-8不超过72字节）' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    # 子进程只通过临时环境接收密码；命令参数和SQL文件里都不携带密码。
    $env:ADMIN_INIT_USERNAME = $Username
    $env:ADMIN_INIT_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    & java -jar $jarPath --spring.main.web-application-type=none --sky.admin-init=true
    if ($LASTEXITCODE -ne 0) { throw '管理员初始化失败；未覆盖已有账号。' }
} finally {
    Remove-Item Env:ADMIN_INIT_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:ADMIN_INIT_USERNAME -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $securePassword.Dispose()
}
