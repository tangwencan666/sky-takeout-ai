# 真实AI配置与最小验证

当前已完成Provider、启动脚本、最小Probe和网页状态展示的准备，**真实DeepSeek尚未请求、尚未验收**。API Key只在用户自己已打开的PowerShell中，代理工具不会读取该终端的秘密，也不需要用户重新提供Key。

本指南把普通开发测试与真实模型调用分开。普通Maven回归强制`AI_MODE=mock`，本机HTTP协议替身仅验证Provider适配器，不代表真实供应商已连通。下面的命令供用户在持有Key的终端明确执行；文档本身不会自动运行它们。

## 配置含义

| 变量 | 本次选择 | 含义 |
| --- | --- | --- |
| AI_MODE | live | 必须为live才使用真实兼容模型；mock仍是离线演示 |
| AI_BASE_URL | https://api.deepseek.com | 官方API根地址，推荐此值；本项目也接受https://api.deepseek.com/v1并规范化路径 |
| AI_MODEL | deepseek-flash | 当前官方Flash模型调用名 |
| AI_API_KEY | 当前PowerShell中已加载的值 | 不输出、不重输、不保存到项目文件 |

不需要设置`AI_ENABLED`、`AI_PROVIDER`或额外Spring profile。供应商根据`AI_BASE_URL`识别；`Start-Preview.ps1 -AiMode live`会保留并传递当前进程已经加载的Key，不会静默改回Mock。`.env.example`只是变量说明，Spring Boot不会自动读取它。

官方更新日志公告`deepseek-chat`、`deepseek-reasoner`于2026-07-24停用；2026-09-10更新将最新Flash调用名设为`deepseek-flash`。因此不要继续使用旧模型名。来源：[DeepSeek更新日志](https://api-docs.deepseek.com/updates/)、[官方API调用说明](https://api-docs.deepseek.com/)。这些是模型名称变更，不需要重新申请或披露已有Key。

## 在同一个PowerShell操作

前提：当前目录是本项目根目录，本轮最新后端JAR已经打包，前端锁定依赖已经安装，MySQL与Redis可用。数据库仍只使用`sky_takeout_ai`；旧库`sky_take_out`不参与任何步骤。

先检查当前终端能继承已有Key；只判断是否存在，不打印它。如果判断失败，回到之前已加载Key的终端执行，不要把Key发送到聊天、命令输出或仓库。

```powershell
$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\20982\Documents\Codex\2026-10-03\files-pasted-by-the-user-java\outputs\sky-takeout-ai'

if ([string]::IsNullOrWhiteSpace($env:AI_API_KEY)) {
    throw '请回到已经加载AI_API_KEY的PowerShell执行；不要打印或发送Key。'
}

# 仅当数据库密码尚未加载时，使用隐藏输入。
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    $databaseSecret = Read-Host '本机MySQL密码' -AsSecureString
    try {
        $env:DB_PASSWORD = [Net.NetworkCredential]::new('', $databaseSecret).Password
    } finally {
        $databaseSecret.Dispose()
    }
}
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'root' }

$env:AI_MODE = 'live'
$env:AI_BASE_URL = 'https://api.deepseek.com'
$env:AI_MODEL = 'deepseek-flash'

./scripts/Stop-Preview.ps1
./scripts/Start-Preview.ps1 -JavaHome 'D:\java\jdk-17.0.20.1+1' -AiMode live
./scripts/Test-AiLive.ps1 -Stage Probe
```

`Stop-Preview.ps1`先校验记录的项目路径、进程命令和创建时间，再停止本项目记录的预览进程；遇到身份不匹配会拒绝，不会清理未知服务。`Start-Preview.ps1`只设置当前子进程环境，不永久修改Windows、JAVA_HOME或PATH，也不删除JDK11。脚本先验证数据库及前端代理就绪，再报告`http://127.0.0.1:15173`可用。若当前没有JWT_SECRET，启动脚本只为这次后端生成临时随机值；因此重启后需重新登录。

`Test-AiLive.ps1`默认使用`demo_user`登录。如果同一终端未加载DEMO_PASSWORD，脚本会以`Read-Host -AsSecureString`隐藏输入开发账号密码；这不是MySQL密码或API Key。登录JWT只保存在脚本内存，不输出到终端，不保存到仓库。

## Probe究竟验证什么

脚本目前只接受`-Stage Probe`。它先只读检查数据库就绪和认证后的`GET /user/ai/status`，确认是live、Key已配置且是官方DeepSeek地址，再执行认证后的`POST /user/ai/probe`。

- 用户消息固定为“你好”，输出上限64 token。
- 不携带业务Tools、聊天Memory或RAG上下文。
- 不进行自动重试，不自动继续复杂Agent验收。
- 同一后端进程成功后缓存Probe结果；再次运行脚本会读到已验证状态，不追加问候请求。
- 失败立即停止，不自动换模型或连续发起收费请求。失败结果也不能被记作通过。

成功后终端会显示`LIVE_PROBE=PASS`和模型名；首次成功通常是`greetingRequestsSent=1`，同一进程已成功时为0。脚本只在Git忽略的`logs/ai-live/probe-result.json`保存非敏感验证元数据，不保存API Key、JWT、密码或完整对话。

网络超时可能使客户端无法确定上游是否已经处理请求；不要把“没有收到成功响应”理解为“供应商一定没有计费”，也不要盲目重跑。先查看安全错误类别和供应商记录，再决定下一步。

## 网页状态与范围

打开用户端并登录，在AI助手点击“刷新状态”。该按钮只读取后端状态，不调用模型。

| 网页文案 | 可得出的结论 |
| --- | --- |
| 真实模型 · 待验证 | 已选live；尚未记录当前进程的真实Probe成功 |
| 真实模型 · 已验证 | 当前后端进程的基础问候Probe已成功；不代表所有AI功能已经验收 |
| Mock 演示 | 当前是离线Mock；真实供应商未参与 |
| 状态获取失败 | 无法读取配置与验收状态；不能推断成Mock或真实成功 |

状态同时显示模型名和Provider。Key未配置时真实模型请求按钮不可用。`providerVerified`的范围是`process-probe`；`instanceId`区分后端进程，重启后需要重新验证。连接本机HTTP协议替身时Provider为`local-test`，不会标记为真实供应商验证成功。

## Probe通过后再逐项验收

以下是后续独立验收顺序，当前脚本不会执行。每一步都需要真实返回与业务证据，不能用Mock通过结果代替。

1. **Dish Tool**：询问“30元以内想吃辣的”；观察模型是否调用只读菜品工具，并逐项核对当前可售菜品、价格与口味信息。
2. **Memory**：同一conversationId先说“我想吃辣的”，再说“30块以内”；验证第二次结合上下文，同时验证其他账号不能读取该会话。
3. **Structured Output**：输入“两个人，预算80”；核对真实菜品ID、Java重新查询和BigDecimal金额，确保合计不超预算。
4. **RAG**：依次询问配送、退款、优惠券和食品安全规则，检查来源文档与答案对应；示例规则不能被包装成商家承诺。
5. **Order Tool**：仅查询当前登录用户订单；验证其他用户订单请求被Java权限层阻止，不把模型自行拒绝当成全部安全证据。
6. **SSE**：验证真实模型分块、正常结束、客户端停止、异常与超时；中断回答不作为完整Memory记录。

每次记录模型、当前进程、请求结果、工具名称及通过依据；不记录Key、密码、JWT或完整敏感Prompt。普通下单、加购、取消继续使用用户确认后的REST按钮，AI不直接写业务数据。

## 当前结论

配置与独立验收入口已准备完成，普通测试仍与真实模型调用隔离。用户在已加载Key的PowerShell启动后端并完成基础Probe后，再继续真实模型的后续六项验收；本文件不宣称真实模型链路已经通过。
