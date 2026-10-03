# DeepSeek 真实联调与验收

日期：2026-10-03，Windows 本机；前端 `http://127.0.0.1:15173`，后端 `http://127.0.0.1:8080`，数据库仅 `sky_takeout_ai`。本文件记录真实请求与本机替身测试的边界，不将两者混为一谈。

## 阻塞位置与诊断证据

接管时，前后端、直接及代理 `/health/ready` 均正常；认证后的 AI 状态为 live / deepseek-flash / keyConfigured=true / providerVerified=false。Java 线程没有正在执行的 Probe 或上游 HTTP 等待，没有到外部443的后端连接。近三轮真实启动日志均无 AI 请求审计。

旧 `Test-AiLive.ps1` 在缺少 `DEMO_PASSWORD` 时，会停在无截止的隐藏 `Read-Host`。用原提交脚本和无收费本机替身复现：执行轨迹到达 Read-Host，2.5秒后仍等待；只收到一次 ready 请求，登录0次、Probe0次。只终止了专门创建的测试子进程。这明确证明了脚本层、模型调用之前的阻塞机制，与用户现象及运行日志吻合；未读取用户原 PowerShell 内存，不能声称恢复了其完整历史输入。

网络诊断不携带Key：DNS21ms、TCP443连接22ms、TLS39ms且证书校验通过（TLS1.3），未认证 `/models` 在109ms返回401，证明请求已到达官方认证入口。Windows系统代理配置指向本机7897，但直接网络正常；没有依据将故障归因于代理。新本机Probe传输显式禁用代理和重定向。

随后只做一次直接HTTP“你好”：HTTP200、deepseek-flash、864ms、5输入+12输出=17 token。因此没有证据支持替换Spring AI；保留Spring AI 1.1.8，并修复超时边界和错误可观测性。修复后经过Spring AI的最小Probe也已通过（1390ms模型调用、64输出token上限、工具0次）。

## 官方配置及凭据来源

重新查阅官方文档，并由真实200响应验证：Base URL为 `https://api.deepseek.com`，接口为 `POST /chat/completions`，模型为 `deepseek-flash`，JSON使用OpenAI-compatible的messages格式，通过Bearer Authorization认证。实际Key没有写入本文件。

官方当前公开模型为deepseek-flash与deepseek-v4-pro；旧deepseek-chat/reasoner已公告停用。官方支持 `reasoning_effort=none` 关闭thinking，以及 `response_format={"type":"json_object"}`；JSON模式必须在提示中明确要求JSON。来源：[官方入门](https://api-docs.deepseek.com/)、[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)、[更新日志](https://api-docs.deepseek.com/updates/)、[JSON模式](https://api-docs.deepseek.com/guides/json_mode/)。

按本次用户明确授权，只读旧 `D:\project632432643\.env`：LLM_API_KEY存在，LLM_BASE_URL为官方根地址，LLM_MODEL仍为deepseek-chat。只在内存中取Key供新项目后端继承，使用核实后的新模型配置；没有修改或复制旧.env。Vite进程不继承Key、数据库密码或JWT密钥。

## 修复内容与超时

- Probe不再进入隐藏密码等待；缺DEMO_PASSWORD立即输出 `LIVE_PROBE=FAIL category=LOCAL_CREDENTIALS_MISSING`。
- 脚本总预算45秒（预留1秒用于旧HTTP栈清理），本机步骤5秒，单次Probe至多40秒；独立Task等待截止、连接取消、响应体大小限制，0次自动重试。成功输出PASS，失败输出安全分类与阶段，退出码0/1。
- Provider连接5秒、响应30秒，整次同步/流式模型调用35秒；包含工具多轮，持续返回小块数据也不能重置总截止。SSE空闲40秒、HTTP emitter45秒，前端等待55秒。
- 同步模型调用由最多4个工作线程隔离，无排队；超时中断，清理UserContext。Probe并发立即返回IN_PROGRESS，成功缓存按当前进程生效。
- 增加AI start/结束审计；仅保留requestId、模型、工具名/次数、耗时、分类和上游状态。区分DNS、TCP、TLS、代理、连接/读取/总超时、401/402/403/404/400/429/5xx、模型不存在、JSON、适配及业务校验失败。不记录完整上游正文或凭据。
- 上游401仍作为本地AI503返回安全分类，不注销有效的本地登录。传统菜单、购物车、订单不依赖模型成功。
- 结构化套餐改用专用提示和JSON模式：菜单已由Java预加载，不再重复要求模型调用菜单工具；明确只生成一个方案、每个ID一份、ID唯一，temperature=0。保留候选ID、重复ID、可售状态和BigDecimal核价全部校验，不信任模型totalPrice，不自动重试。

超时只能保证本地等待有界及尝试取消上游；供应商是否已处理并计费不能由本地超时推断，因此禁止盲目重发。

## 实际功能验收

| 功能 | 结果与证据 |
| --- | --- |
| 直接HTTP / Spring AI Probe | PASS；分别一次固定你好，非空真实回复，模型deepseek-flash |
| Dish Tool Calling | PASS；Chrome准确输入“两个人，预算80，想吃辣一点。”，对应requestId真实调用getAvailableDishes一次；回答中的7个菜名及价格均存在菜单，组合核算52/60/68元均不超过80 |
| Memory | PASS；同conversationId两轮“我想吃辣的。”“30块以内。”，第二轮保留辣味需求；不同conversationId/用户隔离由本机MemoryTest断言，未重复收费验证隔离机制 |
| Structured Output | PASS；最终真实请求HTTP200，1859ms；返回ID118/124/122/127/126，Java重新查询并计算75.00元≤80元，独立逐菜查询核对7500分一致；无重复、无业务写入 |
| RAG | PASS；真实回答配送超时问题，来源delivery-policy.md，与知识文件对应；Embedding仍为local-hash，生成回答为真实DeepSeek |
| Order Tool | PASS；getRecentOrders由JWT当前用户身份调用，返回demo_user订单ID58、待支付、28元；模型将内部ID称为“订单号”，未返回实际业务UUID单号；身份不由模型传入 |
| SSE | PASS；真实Chrome收343个delta和1个live done，无error、无JS异常。异常/取消/慢滴/总截止使用本机受控替身验证，不故意制造收费Provider故障 |
| 浏览器 | PASS；15173真实登录demo_user，页面显示“真实模型 · 已验证 / deepseek-flash”，收到指定问题真实回答；未使用响应fixture |

首次结构化409没有保留原响应消息或模型草案，因此不能反推具体拒绝分支。后续新增仅包含拒绝枚举、候选数量、预算与Java重算金额的安全校验审计；不为了验收通过而关闭校验或默默更改模型选择。

第二次结构化409明确记录DISH_ID_DUPLICATE；无法从该枚举推断重复的是哪道菜。补充每道一份、唯一ID和单一方案约束后，最后一次请求通过。实际套餐：宫保鸡丁28元、番茄炒蛋20元、清炒时蔬16元、紫菜蛋花汤8元、米饭3元，合计75元。回复明确说明当前接口每道一份、米饭共享的限制；没有将两个重复ID静默合并来伪造通过。

最终运行实例为89b117fa-3792-4114-8d85-247c54794b60，最小Probe1445ms通过。13:13:51再次用真实Chrome登录demo_user确认“真实模型 · 已验证 / deepseek-flash”，AI POST为0。此前含真实回答的浏览器截图属于第一轮实例，重启后没有为重复截图再次收费生成。

截图：[真实DeepSeek浏览器](screenshots/09-real-deepseek.png)。机器可读证据见[验收索引](verification/README.md)。工具证明依据带requestId的 `AI tool` 日志；结构化菜单预加载的普通 `Tool getAvailableDishes` 日志不算模型工具调用。

## 测试与费用控制

最终mvn clean package于13:12:28完成：83项、0失败、0错误、0跳过，4个模块全部SUCCESS。新增慢滴流测试曾发现取消实现缺陷，修复后保留上游取消断言并通过；没有删除失败断言。脚本在PowerShell5.1/7各8项、共16项本机故障测试通过，真实Provider请求0次。结构化测试同时覆盖显式JSON与唯一ID提示、单次模型调用、重复ID拒绝、最新数据库价格重新核算和超预算拒绝。

普通Maven通过Surefire系统属性与fork环境双重强制Mock并清空Key。真实API验收与mvn test分开，启动应用和刷新状态不自动收费；明确执行Start-DeepSeek才可选择启动后独立Probe。已通过场景不盲目重跑；结构化失败后先保存证据、修复，再单独复验。

本轮显式调用计数：直接HTTP1次；三个构建实例各最小Probe1次；功能请求8次（浏览器1、Memory2、Structured3含两次拒绝、RAG1、Order1）。没有自动重试。工具后的模型续答由Spring AI在同一次功能请求内完成，此计数不是供应商账单的HTTP轮数或总token；仅直接HTTP明确记录17 token。

诊断边界：嵌套DNS异常在外层已识别为TCP时可能显示TCP；连接重置可能归为ADAPTER；同步错误正文慢滴可能最终以TOTAL_TIMEOUT结束。均受总截止约束，不将分类覆盖描述为所有网络故障的完备证明。

## 安全与运行方式

没有修改旧数据库sky_take_out、Sentinel项目或旧.env。本轮真实AI验收仅调用只读工具，演示账号未加购或创建/取消订单；Maven数据库测试按既有隔离夹具执行并清理。数据库密码、API Key与JWT不进入代码、文档、Git或诊断结果。完整stdout日志和原始Surefire XML保持Git忽略。

最终验收完成后保持15173与8080的live实例运行，用户只需打开页面；需要重新启动时，已安全加载DB_PASSWORD及DEMO_PASSWORD的终端可执行 `./scripts/Start-DeepSeek.ps1`，无需重新提供Key。没有永久修改系统JAVA_HOME/PATH，也没有git push。源文件、Git历史与40份运行日志已扫描实际授权Key及当前会话凭据，没有发现匹配；完整运行日志和.env均未纳入提交。
