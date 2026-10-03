# 真实AI配置与最小验证

本轮已直接启动官方DeepSeek真实模式，并完成真实Probe、浏览器Dish Tool与SSE、Memory、Structured Output、RAG、Order Tool验证。结构化路径的两次409保留为历史；修复后单次返回真实75元方案并通过Java与独立查库核价。逐项证据与最终结论以[真实AI验收记录](REAL_AI_VERIFICATION.md)为准。

本轮启动与验证已经执行，无需用户现在重复运行。普通Maven回归仍强制`AI_MODE=mock`并清空测试进程中的Provider Key；本机HTTP替身用于协议与失败路径测试，和真实供应商验证分别记录。下面的命令供今后重启使用。

## 配置含义

| 变量 | 本次选择 | 含义 |
| --- | --- | --- |
| AI_MODE | live | 必须为live才使用真实兼容模型；mock仍是离线演示 |
| AI_BASE_URL | https://api.deepseek.com | 官方API根地址，推荐此值；本项目也接受https://api.deepseek.com/v1并规范化路径 |
| AI_MODEL | deepseek-flash | 当前官方Flash模型调用名 |
| AI_API_KEY | 已有进程环境优先，否则来自已授权旧配置 | 只在内存和后端进程环境中使用，不打印 |
| DB_PASSWORD | 必须预先存在于进程环境 | 真实数据库连接凭据 |
| DEMO_PASSWORD | 必须预先存在于进程环境 | Probe登录已有开发账号使用 |

不需要设置`AI_ENABLED`、`AI_PROVIDER`或额外Spring profile。供应商根据`AI_BASE_URL`识别。`.env.example`只是变量说明，Spring Boot不会自动读取它；读取已授权旧配置是`Start-DeepSeek.ps1`的显式操作。

官方更新日志公告`deepseek-chat`、`deepseek-reasoner`于2026-07-24停用；2026-09-10更新将最新Flash调用名设为`deepseek-flash`。因此不要继续使用旧模型名。来源：[DeepSeek更新日志](https://api-docs.deepseek.com/updates/)、[官方API调用说明](https://api-docs.deepseek.com/)。这些是模型名称变更，不需要重新申请或披露已有Key。

## 今后重启的入口

前提：当前目录是本项目根目录，最新后端JAR已经打包，前端依赖已经安装，MySQL与Redis可用，`DB_PASSWORD`与`DEMO_PASSWORD`已安全加载到当前PowerShell的进程环境。数据库仍只使用`sky_takeout_ai`；旧库`sky_take_out`不参与任何步骤。

使用一条命令重启并执行最小Probe。脚本不等待交互输入，缺少所需凭据时立即停止；无需把密码或Key发到聊天中。

```powershell
.\scripts\Start-DeepSeek.ps1
```

Key优先使用当前进程已有的`AI_API_KEY`；未设置时，脚本只读用户已授权的`D:\project632432643\.env`，校验其供应商为官方DeepSeek，再把`LLM_API_KEY`载入内存供本项目后端启动。旧文件不修改，也不沿用旧文件中已经退役的模型名。入口统一使用官方`https://api.deepseek.com`与`deepseek-flash`，结束后恢复调用者原有AI环境变量，不把Key保存到本项目文件。

入口先调用`Stop-Preview.ps1`校验记录的项目路径、进程命令和创建时间，再停止本项目记录的预览进程；身份不匹配会拒绝，不清理未知服务。随后`Start-Preview.ps1`固定启动8080与15173，并设置同源`/api`代理目标。数据库及前端代理就绪后才报告`http://127.0.0.1:15173`可用，然后执行Probe。缺少JWT_SECRET时只为本次后端生成临时随机值，重启后需重新登录。

敏感启动变量只交给Java后端；启动Vite时移除`AI_API_KEY`、`DB_PASSWORD`、`JWT_SECRET`等敏感环境变量，再恢复父进程环境。脚本不永久修改Windows、JAVA_HOME或PATH，也不删除JDK11。登录JWT只保存在Probe内存，不输出到终端或保存到仓库。

普通Mock预览仍可使用`Start-Preview.ps1 -AiMode mock`。`Start-Preview.ps1`本身和网页“刷新状态”不会调用模型；`Start-DeepSeek.ps1`默认显式执行最小Probe，因此属于真实模型操作。`-SkipProbe`仅跳过Probe，不能据此宣称供应商已验证。

## Probe究竟验证什么

对已运行的服务，也可独立执行`.\scripts\Test-AiLive.ps1 -Stage Probe`。脚本只接受Probe阶段，不读取Provider Key；先检查数据库就绪、登录与认证后的`GET /user/ai/status`，确认live、Key已配置且是官方DeepSeek地址，再最多发送一次认证后的`POST /user/ai/probe`。

- 用户消息固定为“你好”，输出上限64 token。
- 不携带业务Tools、聊天Memory或RAG上下文。
- 不进行自动重试，不自动继续复杂Agent验收。
- 同一后端进程成功后缓存Probe结果；再次运行脚本会读到已验证状态，不追加问候请求。
- 失败立即停止，不自动换模型或连续发起收费请求。失败结果也不能被记作通过。
- 缺少`DEMO_PASSWORD`立即输出`LIVE_PROBE=FAIL category=LOCAL_CREDENTIALS_MISSING`，不会进入密码输入等待或请求模型。启动入口会更早以`DEMO_CREDENTIAL_MISSING`停止。
- 各阶段输出不含秘密的进度；失败返回非零退出码，成功正常返回。

| 等待范围 | 上限 |
| --- | --- |
| 整个Probe脚本 | 默认45秒，其中预留1秒用于取消、清理和记录结果 |
| 本机就绪、登录、初次状态读取 | 各5秒，同时受剩余总预算约束 |
| 单次Probe HTTP请求 | 最多40秒，同时受剩余总预算约束 |
| 后端真实生成 | 35秒总截止，优先返回明确错误类别 |
| 成功后的状态复核 | 最多3秒，同时受剩余总预算约束 |

45秒是Probe脚本预算，不包含之前的Java与Vite启动等待。本机HTTP请求禁用代理和自动重定向；PowerShell 5.1与7均使用明确的取消与等待上限，覆盖响应头和响应体等待。

成功后终端会显示`LIVE_PROBE=PASS`和模型名；首次成功通常是`greetingRequestsSent=1`，同一进程已成功时为0。脚本只在Git忽略的`logs/ai-live/probe-result.json`保存非敏感验证元数据，不保存API Key、JWT、密码或完整对话。

失败输出`LIVE_PROBE=FAIL category=...`，区分本机凭据、数据库、认证、代理与模型供应商问题；供应商类别包括DNS、TCP、TLS、AUTH、BALANCE、MODEL_NOT_FOUND和TOTAL_TIMEOUT等，不统一掩盖成“AI不可用”。不打印原始异常正文或模型回复。

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

## 当前逐项进展

这些功能由独立真实请求和浏览器流程验收，Probe脚本不会自动执行。当前进展如下；最终修复与复验统一记录在[REAL_AI_VERIFICATION](REAL_AI_VERIFICATION.md)。

| 项目 | 当前证据状态 |
| --- | --- |
| 最小Probe | 真实DeepSeek调用通过 |
| Dish Tool与SSE | 真实浏览器菜品工具与流式回答通过 |
| Memory | 真实多轮验证通过 |
| RAG | 真实规则问答验证通过 |
| Order Tool | 真实订单查询验证通过 |
| Structured Output | 最终真实HTTP200，Java核价75.00元≤80元，独立查询7500分一致 |

各项应遵循下列判据，不能仅凭基础Probe成功或Mock结果代替：

1. **Dish Tool**：询问“30元以内想吃辣的”；观察模型是否调用只读菜品工具，并逐项核对当前可售菜品、价格与口味信息。
2. **Memory**：同一conversationId先说“我想吃辣的”，再说“30块以内”；验证第二次结合上下文，同时验证其他账号不能读取该会话。
3. **Structured Output**：输入“两个人，预算80”；核对真实菜品ID、Java重新查询和BigDecimal金额，确保合计不超预算。
4. **RAG**：依次询问配送、退款、优惠券和食品安全规则，检查来源文档与答案对应；示例规则不能被包装成商家承诺。
5. **Order Tool**：仅查询当前登录用户订单；验证其他用户订单请求被Java权限层阻止，不把模型自行拒绝当成全部安全证据。
6. **SSE**：验证真实模型分块、正常结束、客户端停止、异常与超时；中断回答不作为完整Memory记录。

每次记录模型、当前进程、请求结果、工具名称及通过依据；不记录Key、密码、JWT或完整敏感Prompt。普通下单、加购、取消继续使用用户确认后的REST按钮，AI不直接写业务数据。

## 当前结论

真实模式已启动，基础Probe及上述六类功能均已取得真实通过证据；最终Chrome再次确认当前进程显示真实模型已验证。当前验证是有限场景，不构成全面模型效果评测；取消、异常、越权及超时应按各自自动化、本机替身或真实请求的证据区分记录，不能从正常SSE通过推导出所有失败路径也经过真实供应商验证。普通测试继续与真实模型调用隔离，本轮无需用户重复启动。
