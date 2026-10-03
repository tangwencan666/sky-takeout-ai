# 最终测试记录

日期：2026-10-03（Asia/Shanghai）。本轮最终命令：RUN_DB_TESTS=true，mvn -B -ntp clean package，Java17；AI_MODE=mock，AI_API_KEY为空，JWT_SECRET为临时环境随机密钥。Surefire同样强制Mock，未调用真实供应商。

| 测试类 | 项数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| com.tang.sky.ai.rag.KnowledgeChunkTest | 1 | 0 | 0 | 0 |
| com.tang.sky.ai.security.AiAuditTest | 1 | 0 | 0 | 0 |
| com.tang.sky.AiChatTest | 1 | 0 | 0 | 0 |
| com.tang.sky.AiFailureHttpTest | 1 | 0 | 0 | 0 |
| com.tang.sky.AiFoundationTest | 2 | 0 | 0 | 0 |
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

**共29个测试类、62项，0失败、0错误、0跳过；4个Maven Reactor模块全部SUCCESS。**

统计直接提取最终Surefire XML，仅保存逐类统计，不提交含运行环境信息的原始XML。见[机器可读统计](verification/backend-tests.json)。其中包括单元/替身/真实基础设施/HTTP测试，不把全部62项统称为真实模型或数据库测试。

## 额外执行的验收

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
| 真实模型 | 等待API Key进行真实验证 |

真实基础设施：MySQL8.0.44，仅catalog=sky_takeout_ai；Redis3.0.504。初次全量验收服务18080、Vite15173；后续预览修复已统一为8080与15173。完整菜单CRUD主要由Service+真实数据库集成验证，独立HTTP脚本未覆盖每个管理CRUD。订单创建/菜品创建有真实约束失败回滚证据，套餐没有额外故障注入。

浏览器/HTTP随机账号及其订单、购物车已按记录定向清理，开发演示数据保留。旧库未读写。本轮服务保留供体验，进程退出后按README启动。证据索引见[verification](verification/README.md)，完整限制见[AUDIT](AUDIT.md)。

可跟踪工作区、Git历史与运行日志的已知会话秘密扫描通过；这不是未知凭据的全面检测或生产安全认证。公开开发演示口令是用户明确授权的演示数据，不是基础设施密码或API Key。


## 预览登录修复复验

新增4项数据库就绪检查后，最终clean package为62项、0失败/错误/跳过。实际Chrome使用demo_user重新打开15173，登录、JWT刷新、16张餐品卡、微辣加购28元、确认下单和详情通过；订单ID58保留。22次同源API全为200，无网络失败或JS异常。直接8080/health/ready及代理15173/api/health/ready均返回新库ready；启动脚本端口占用保护通过。详情见[PREVIEW_FIX](PREVIEW_FIX.md)。
