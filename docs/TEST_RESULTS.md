# 最终测试记录

日期：2026-10-03（Asia/Shanghai）。**本轮Probe与真实AI修复后的最终全量构建于13:12:28完成：83项、0失败、0错误、0跳过，全部Maven模块SUCCESS。Structured Output修复后的最后一次真实复验HTTP200，1859ms，Java核价75.00元且独立数据库查询一致，不超过80元预算。** 首次结构化409及旧轮次记录保留；本轮有限场景通过不等于生产可靠性或全面模型效果评测。

## 当前真实AI修复与验证进展

| 层级 | 当前结果与边界 |
| --- | --- |
| 普通Maven回归 | 最终clean package：83项、0失败/错误/跳过，全部模块SUCCESS；Surefire系统属性与测试进程环境双重强制AI_MODE=mock并清空AI_API_KEY |
| PowerShell脚本故障测试 | Windows PowerShell5.1与PowerShell7各8项，共16项通过；全部本机替身，真实Provider请求0次 |
| 旧脚本阻塞复现 | 原提交脚本执行到缺DEMO_PASSWORD的Read-Host后持续等待；2.5秒内ready1次、登录0次、Probe0次，只停止测试子进程 |
| 最小真实Probe | 直接HTTP与Spring AI链路均通过，独立显式调用，非普通Maven测试 |
| 真实功能 | 浏览器Dish Tool与正常SSE、Memory、RAG、当前用户Order Tool已通过；有限场景，未推广为全部故障与安全路径通过 |
| Structured Output | PASS；修复后最后一次真实请求HTTP200、1859ms，Java75.00元≤80元，独立查询7500分匹配；首次409仍保留，未发生业务写入 |
| 当前运行入口 | Start-DeepSeek已实际运行；用户无需此刻重复启动，今后重启按README使用独立入口 |

脚本故障覆盖缺凭据、响应头挂起、响应体挂起、Probe挂起、认证错误信封、成功、缓存命中和拒绝重定向。默认Probe总预算45秒（预留1秒清理），本机步骤各最多5秒、单次Probe最多40秒，后端生成35秒；失败输出安全类别并停止，不自动重试。超时不证明供应商未计费。

最终结构化方案的菜品ID为118/124/122/127/126，对应宫保鸡丁28元、番茄炒蛋20元、清炒时蔬16元、紫菜蛋花汤8元、米饭3元。候选、去重、可售状态和BigDecimal预算校验均保留；方案解释每道一份及米饭共享限制，没有把模型提供的金额当作可信值，没有加购或下单。此结果证明该次真实方案核验通过，不证明已支持任意份数或满足所有人的食量需求。

本次用户已明确授权只读旧配置中的Key供新项目使用，旧.env没有修改；敏感变量只交给Java后端，Vite不继承AI_API_KEY、DB_PASSWORD或JWT_SECRET。仅使用sky_takeout_ai，旧库sky_take_out未读写；不永久修改系统环境，不在文档或证据中记录真实凭据或私人对话；仅保留本次公开演示提示及非敏感验收回复。逐项真实证据和验证边界见[REAL_AI_VERIFICATION](REAL_AI_VERIFICATION.md)。

## 历史：DeepSeek切换准备时的69项全量结果

当时的全量命令：RUN_DB_TESTS=true，mvn -B -ntp clean package，Java17。为验证收费请求隔离，外部AI_MODE=live、AI_API_KEY使用临时随机合成值、AI_BASE_URL指向本机不可用端口；Surefire在系统属性和测试进程环境双重强制Mock并清空Key。JWT_SECRET为临时环境随机密钥。该批模型测试仅使用Mock或本机协议替身，未调用真实供应商。

| 测试类 | 项数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| com.tang.sky.ai.rag.KnowledgeChunkTest | 1 | 0 | 0 | 0 |
| com.tang.sky.ai.security.AiAuditTest | 1 | 0 | 0 | 0 |
| com.tang.sky.AiChatTest | 1 | 0 | 0 | 0 |
| com.tang.sky.AiFailureHttpTest | 1 | 0 | 0 | 0 |
| com.tang.sky.AiFoundationTest | 2 | 0 | 0 | 0 |
| com.tang.sky.AiProviderConfigurationTest | 4 | 0 | 0 | 0 |
| com.tang.sky.AiProviderServiceTest | 3 | 0 | 0 | 0 |
| com.tang.sky.AiSecurityTest | 3 | 0 | 0 | 0 |
| com.tang.sky.AuthIntegrationTest | 5 | 0 | 0 | 0 |
| com.tang.sky.BusinessTransactionIntegrationTest | 2 | 0 | 0 | 0 |
| com.tang.sky.CacheFailureTest | 1 | 0 | 0 | 0 |
| com.tang.sky.CacheIntegrationTest | 1 | 0 | 0 | 0 |
| com.tang.sky.CartIntegrationTest | 1 | 0 | 0 | 0 |
| com.tang.sky.CatalogApiIntegrationTest | 1 | 0 | 0 | 0 |
| com.tang.sky.DatabaseConnectionTest | 1 | 0 | 0 | 0 |
| com.tang.sky.DishIntegrationTest | 3 | 0 | 0 | 0 |
| com.tang.sky.DishToolsTest | 1 | 0 | 0 | 0 |
| com.tang.sky.ExceptionContractTest | 5 | 0 | 0 | 0 |
| com.tang.sky.HealthControllerTest | 1 | 0 | 0 | 0 |
| com.tang.sky.JwtConfigurationTest | 2 | 0 | 0 | 0 |
| com.tang.sky.LocalProviderTest | 1 | 0 | 0 | 0 |
| com.tang.sky.MealPlanTest | 2 | 0 | 0 | 0 |
| com.tang.sky.MemoryTest | 2 | 0 | 0 | 0 |
| com.tang.sky.OrderIntegrationTest | 3 | 0 | 0 | 0 |
| com.tang.sky.PrivateToolsTest | 1 | 0 | 0 | 0 |
| com.tang.sky.ReadinessControllerTest | 4 | 0 | 0 | 0 |
| com.tang.sky.RagTest | 6 | 0 | 0 | 0 |
| com.tang.sky.SetmealIntegrationTest | 1 | 0 | 0 | 0 |
| com.tang.sky.SseHttpTest | 1 | 0 | 0 | 0 |
| com.tang.sky.StatisticsIntegrationTest | 2 | 0 | 0 | 0 |
| com.tang.sky.StreamingTest | 6 | 0 | 0 | 0 |

**共31个测试类、69项，0失败、0错误、0跳过；4个Maven Reactor模块全部SUCCESS。**

以上保留当时69项统计。当前[机器可读统计](verification/backend-tests.json)已更新为最新83项，直接提取最终Surefire XML的逐类计数，不提交含运行环境信息的原始XML。测试包括单元/替身/真实基础设施/HTTP，不全部统称为真实模型或数据库测试。

## 历史：当时额外执行的验收

| 检查 | 实际结果 |
| --- | --- |
| npm test | 7项通过，0失败/跳过；会话延迟响应/401及格式边界 |
| npm run build | 通过；主JS约1MB，保留大于500kB提示 |
| 独立运行后端HTTP | 10组全部通过：认证/JWT、用户隔离、菜单、核价下单、幂等、状态、AI应用、RAG、SSE |
| Chrome真实业务 | 14组流程检查通过：选微辣、重复加购、套餐组成、64元下单、详情、取消、再来一单、Mock流与规则等；JS运行异常0 |
| 浏览器错误展示 | AI503、菜单网络失败、401由Playwright请求拦截模拟；错误密码401另是真实后端返回 |
| 视口 | 菜单/购物车/订单详情/AI预算/RAG/订单列表 × 1920×1080、1440×900、1366×768、390×844，共24项无横向溢出；15张截图 |
| SSE正常流 | 浏览器实际HTTP收到39个delta与1个done，模型为Mock |
| SSE故障 | 后端受控模型测试真实HTTP断连、失败隔离，另测空闲超时/取消/上下文清理 |
| Provider协议 | 真正OpenAiChatModel连接本机HTTP替身；同步、多段SSE、500只一次请求；未访问供应商 |
| RAG四問 | 配送/优惠券/退款/食品问题首位来源正确；长文切片和元数据检查通过 |
| Docker config | 通过；显式DDL与seed顺序 |
| Nginx语法 | 使用已有镜像离线nginx -t通过；不是目标部署成功 |
| Docker build | 本轮重试失败：auth.docker.io令牌请求连接超时，尚未进入应用编译 |
| Docker up / health | 未执行 / 未验证 |
| 真实模型 | 用户已在自己的PowerShell加载Key；待从该终端启动live并完成最小探针，尚未发起收费请求 |

真实基础设施：MySQL8.0.44，仅catalog=sky_takeout_ai；Redis3.0.504。初次全量验收服务18080、Vite15173；后续预览修复已统一为8080与15173。完整菜单CRUD主要由Service+真实数据库集成验证，独立HTTP脚本未覆盖每个管理CRUD。订单创建/菜品创建有真实约束失败回滚证据，套餐没有额外故障注入。

浏览器/HTTP随机账号及其订单、购物车已按记录定向清理，开发演示数据保留。旧库未读写。本轮服务保留供体验，进程退出后按README启动。证据索引见[verification](verification/README.md)，完整限制见[AUDIT](AUDIT.md)。

可跟踪工作区、Git历史与运行日志的已知会话秘密扫描通过；这不是未知凭据的全面检测或生产安全认证。公开开发演示口令是用户明确授权的演示数据，不是基础设施密码或API Key。


## 预览登录修复复验

新增4项数据库就绪检查后，最终clean package为62项、0失败/错误/跳过。实际Chrome使用demo_user重新打开15173，登录、JWT刷新、16张餐品卡、微辣加购28元、确认下单和详情通过；订单ID58保留。22次同源API全为200，无网络失败或JS异常。直接8080/health/ready及代理15173/api/health/ready均返回新库ready；启动脚本端口占用保护通过。详情见[PREVIEW_FIX](PREVIEW_FIX.md)。

## 历史：DeepSeek切换准备复验

新增7项Provider配置和探针测试后，全量打包69项通过、0失败/错误/跳过。原本地HTTP协议测试也增加/v1不重复、固定你好、64 token、无工具、成功缓存只调用一次的线级断言；本机替身绝不被标记为真实Provider已验证。普通测试通过Surefire系统属性与fork进程环境双重隔离，收费探针仅由独立脚本明确运行。

Start-Preview的缺Key保护通过（没有创建进程）；Stop-Preview的WhatIf及实际停止已核验进程通过；Test-AiLive在Mock后端完成本机认证后立即拒绝继续，没有发送Probe。三个脚本在PowerShell5.1与7解析通过。

真实Chrome重新打开15173，以demo_user登录并读取新后端状态：mode=mock、providerVerified=false、model=deepseek-flash。刷新状态不触发AI POST。另通过浏览器请求拦截模拟待验证、已验证、缺Key、状态503四种UI状态，文案和禁用逻辑通过；这些是页面展示测试，不是DeepSeek真实请求成功。AI POST总数0，JS异常0。[浏览器记录](verification/ai-status-browser.json)

当前JAR与页面准备完成，用户终端持有的Key没有传给代理进程，故暂保留Mock预览；真实探针及Dish Tool→Memory→Structured Output→RAG→Order Tool→SSE尚未运行。操作见[真实AI验证](AI_LIVE_VERIFICATION.md)。没有访问旧数据库或Sentinel配置。
