# 最终测试记录

日期：2026-10-03（Asia/Shanghai）。命令：RUN_DB_TESTS=true，mvn -B -ntp clean package。外部AI_MODE=live、AI_API_KEY为空，Surefire强制Mock；没有真实模型调用。

| 测试类 | 项数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| com.tang.sky.AiChatTest | 1 | 0 | 0 | 0 |
| com.tang.sky.AiFoundationTest | 2 | 0 | 0 | 0 |
| com.tang.sky.AiSecurityTest | 3 | 0 | 0 | 0 |
| com.tang.sky.AuthIntegrationTest | 5 | 0 | 0 | 0 |
| com.tang.sky.CacheIntegrationTest | 1 | 0 | 0 | 0 |
| com.tang.sky.CartIntegrationTest | 1 | 0 | 0 | 0 |
| com.tang.sky.DatabaseConnectionTest | 1 | 0 | 0 | 0 |
| com.tang.sky.DishIntegrationTest | 2 | 0 | 0 | 0 |
| com.tang.sky.DishToolsTest | 1 | 0 | 0 | 0 |
| com.tang.sky.ExceptionContractTest | 4 | 0 | 0 | 0 |
| com.tang.sky.HealthControllerTest | 1 | 0 | 0 | 0 |
| com.tang.sky.MealPlanTest | 2 | 0 | 0 | 0 |
| com.tang.sky.MemoryTest | 2 | 0 | 0 | 0 |
| com.tang.sky.OrderIntegrationTest | 2 | 0 | 0 | 0 |
| com.tang.sky.PrivateToolsTest | 1 | 0 | 0 | 0 |
| com.tang.sky.RagTest | 2 | 0 | 0 | 0 |
| com.tang.sky.SetmealIntegrationTest | 1 | 0 | 0 | 0 |
| com.tang.sky.SseHttpTest | 1 | 0 | 0 | 0 |
| com.tang.sky.StatisticsIntegrationTest | 2 | 0 | 0 | 0 |
| com.tang.sky.StreamingTest | 2 | 0 | 0 | 0 |

**合计：37项，0失败、0错误、0跳过；四个Maven Reactor模块全部SUCCESS。**

前端npm run build通过。Chrome无头浏览器注册登录、空菜单/购物车/订单、Mock SSE和退出通过，无pageerror；测试账号已精确清理。未验收浏览器有商品下单。

Docker config --quiet通过；build两次因auth.docker.io连接超时失败，未执行up或容器健康检查。真实模型API未验证。

本机现有Redis3.0.504参与缓存测试，MySQL8.0.44参与真实数据库测试；数据库连接catalog由测试断言固定sky_takeout_ai。

Git当前文件及历史扫描未命中当前会话DB_PASSWORD/AI_API_KEY/JWT_SECRET等非空凭据；这不是对所有未知秘密形式的完整检测。SQL脚本不包含旧库操作或DROP/TRUNCATE/ALTER。
