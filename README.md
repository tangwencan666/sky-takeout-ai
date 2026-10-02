# 苍穹智膳 SkyTakeout AI

Java 外卖业务与大模型应用学习项目：先用确定性的后端实现菜单、购物车和订单，再让AI通过受控工具读取真实业务数据。Maven多模块单体 + Vue用户端，适合按阶段阅读和练习。

**验收状态（2026-10-03）：阶段1—19已实现并测试；阶段20的Docker配置已生成且config通过，但两次构建均因Docker Hub网络超时阻塞，尚未完成容器验收。阶段21的本机测试与文档已完成，部署项仍待补验。真实模型API未验证，默认Mock不产生模型调用费用。**

先读 [夜间报告](docs/NIGHTLY_REPORT.md)，学习过程见 [LEARNING](docs/LEARNING.md)，面试准备见 [INTERVIEW](docs/INTERVIEW.md)。

## 技术栈

| 层次 | 版本/实现 |
| --- | --- |
| Java / Maven | Temurin 17.0.20.1 / Maven 3.9.11，本机临时环境 |
| 后端 | Spring Boot 3.5.16、MyBatis-Plus 3.5.17、Jakarta Validation |
| 数据库 | 本机MySQL 8.0.44，InnoDB；Docker方案采用MySQL 8.4但未启动验收 |
| 认证 | BCrypt成本12、JJWT 0.13.0、Interceptor、UserContext |
| 缓存 | Spring Cache + Redis；本机Redis 3.0.504真实测试通过 |
| AI | Spring AI 1.1.8，OpenAI-compatible ChatModel / ChatClient |
| 前端 | Vue 3.5.43、Vite 8.3.2、Element Plus 2.14.7、Axios 1.20.0、Pinia 4.0.3 |
| 本机工具 | Node 24.14.1 / npm 11.11.0 / Git 2.53.0 |

依赖版本以pom.xml和package-lock.json为准。项目引入MyBatis-Plus依赖，实际Mapper主要使用参数化注解SQL，未伪装成完整使用其所有功能。

## 结构与调用链

```text
sky-common/       Result、错误码、业务异常、认证上下文
sky-pojo/         数据记录、输入DTO、组合VO、统计结果
sky-server/       Controller → Service/Impl → Mapper，以及认证/缓存/AI
sky-web/          Vue用户端
sql/              01建库、02—06建表（仅新项目库）
knowledge/        5份明确标记的教学规则
scripts/          可选管理员初始化
Dockerfile        后端构建与运行镜像（未完成构建验收）
docker-compose.yml  MySQL、Redis、backend、frontend、nginx
```

```mermaid
flowchart LR
    UI[Vue用户端] -->|Bearer JWT| C[Controller]
    C --> S[Service / 事务与业务规则]
    S --> M[Mapper固定参数化SQL]
    M --> DB[(sky_takeout_ai)]
    S --> Cache[(Redis公开菜单缓存)]
    C --> AI[AI Service / ChatClient]
    AI --> LLM[Mock或兼容模型]
    LLM --> T[受控只读Tools]
    T --> S
    AI --> R[规则检索 / 来源上下文]
```

三个Maven模块仍在一个JVM内，不是微服务。认证身份只来自JWT；userId不由前端或模型指定。Controller不直接操作Mapper，金额使用BigDecimal，下单时重新查询数据库核价并保存快照。

## 功能与边界

- 普通用户注册登录；独立员工登录；菜单分类、菜品与口味、套餐及组成；购物车合并/减量/清空；订单创建、幂等、详情、历史、取消、再次加入购物车。
- 管理员通过API维护分类/菜品/套餐、顺序推进订单状态、获取AI经营分析。**本版没有管理后台页面，也没有默认管理员和默认菜单。** 初次看到空菜单是正常的。
- 订单状态：PENDING_PAYMENT → WAITING_ACCEPT → PREPARING → DELIVERING → COMPLETED；用户可在前两种状态取消。管理员推进到WAITING_ACCEPT仅为教学模拟，**没有真实支付或退款**。
- AI聊天、只读业务Tools、多轮Memory、预算套餐、SSE、RAG规则问答、管理员聚合统计。优惠券/会员/配送规则是示例知识，没有实现优惠券、积分、评价或真实配送服务。
- 菜品口味有后端维护与返回；当前用户页面直接加购默认空口味，尚未提供完整口味选择器。

## 数据库

唯一新库为 **sky_takeout_ai**，旧库 **sky_take_out** 禁止读写、修改、删除或复用。连接URL固定新库名，不接受DB_NAME覆盖。完整表结构与关系见 [DATABASE_DESIGN](docs/DATABASE_DESIGN.md)。

本机新库和10张表已创建并完成真实连接测试，不需要重新初始化。新机器才需在MySQL客户端依次执行sql/01至06；所有脚本仅CREATE IF NOT EXISTS，没有删除或迁移已有表的逻辑。不要以为IF NOT EXISTS可以自动升级旧表结构。

```powershell
# 仅用于新环境：进入项目根目录，在交互提示中输入数据库密码
mysql -h 127.0.0.1 -P 3306 -u root -p
```

```sql
SOURCE sql/01_create_database.sql;
SOURCE sql/02_auth.sql;
SOURCE sql/03_catalog.sql;
SOURCE sql/04_setmeal.sql;
SOURCE sql/05_cart.sql;
SOURCE sql/06_orders.sql;
SELECT DATABASE();
```

最后应为sky_takeout_ai。不执行DROP DATABASE，也不向旧库执行任何命令。

## 本机启动

在项目根目录打开PowerShell。以下仅设置当前终端，不改系统JAVA_HOME/PATH，也不删除JDK11。

```powershell
$env:JAVA_HOME = 'D:\java\jdk-17.0.20.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:DB_USERNAME = 'root'
$secret = Read-Host '本机MySQL密码' -AsSecureString
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', $secret).Password
$secret.Dispose()
$env:AI_MODE = 'mock'
$env:CACHE_ENABLED = 'true'
$env:RUN_DB_TESTS = 'true'
mvn -B -ntp clean package
# 必须看到37项测试、0失败、0错误、0跳过和全部模块SUCCESS后启动
java -jar sky-server/target/sky-server-0.0.1-SNAPSHOT.jar
```

后端http://127.0.0.1:8080/health。Redis使用现有服务127.0.0.1:6379；日常无Redis可以CACHE_ENABLED=false启动，但完整缓存测试需要Redis。DB_PASSWORD不要打印、保存到文件或发给他人。终端结束后凭据随进程消失；也可用Remove-Item Env:DB_PASSWORD清除。停止正在运行的JAR后再执行mvn clean，Windows会锁定运行中的JAR。

另开终端：

```powershell
cd sky-web
npm ci
npm run dev
```

浏览器http://127.0.0.1:5173，Vite把/api代理到8080。`npm run build`生成dist；不能直接双击index.html代替HTTP服务。登录信息仅在sessionStorage保存，后端仍对每次请求鉴权。

### 创建自己的管理员与菜单

管理员只能通过显式离线命令创建，没有开放注册接口。先按上面的步骤打包并在当前终端配置DB_PASSWORD，再运行：

```powershell
.\scripts\Initialize-Admin.ps1 -Username your_admin_name
```

按隐藏提示输入自选管理员密码（12—64字符，UTF-8不超过72字节）。命令校验新库catalog，存BCrypt哈希，已有同名员工直接失败，不覆盖。该可选初始化分支已编译，未在本机实际创建账号；需要你自行选择凭据后操作。

通过POST /admin/auth/login拿到token后，用HTTP客户端调用以下接口，Header为Authorization: Bearer TOKEN（不要保存真实token到仓库）：

```text
POST /admin/categories
{"name":"热菜","type":1,"sort":0}

POST /admin/dishes
{"categoryId":上一步返回ID,"name":"宫保鸡丁","price":28.00,"description":"教学菜品","image":"","flavors":[]}

PUT /admin/dishes/返回ID/status?status=1
```

新菜品默认下架，必须上架后普通用户才能看到。套餐分类type=2，套餐组成通过dishes数组提交dishId和quantity。此文档示例不会自动执行或向数据库插入演示菜单。

## 环境变量

| 变量 | 默认值 / 说明 |
| --- | --- |
| DB_HOST / DB_PORT | 127.0.0.1 / 3306；库名固定 |
| DB_USERNAME / DB_PASSWORD | root / 空；真实密码只通过进程环境 |
| SERVER_PORT | 8080 |
| REDIS_HOST / REDIS_PORT / REDIS_PASSWORD | 127.0.0.1 / 6379 / 空 |
| CACHE_ENABLED | false；只缓存公开分类与可售菜品热点列表 |
| JWT_SECRET | 空时每次启动随机；部署使用至少32随机字节的Base64，重启保持一致 |
| AI_MODE | mock；live才启用外部兼容模型 |
| AI_API_KEY / AI_BASE_URL / AI_MODEL | 空 / https://api.deepseek.com / deepseek-chat |
| RUN_DB_TESTS | 未设则跳过需要MySQL/Redis的测试；完整验收必须true |
| ADMIN_INIT_USERNAME / ADMIN_INIT_PASSWORD | 仅显式初始化子进程使用，脚本结束清理 |
| MYSQL_ROOT_PASSWORD / WEB_PORT | 仅Docker；独立容器root密码 / 18088 |

.env.example只有变量说明和占位符；Spring Boot不自动加载.env。真实密码或Key不得写application.yml、源码、测试、文档、.env.example或Git。本次没有调用收费模型；启用live前由你确认供应商及费用。

## API概览

普通JSON响应统一为`{"code":200,"message":"success","data":...}`；HTTP状态与错误码一致。AI SSE使用事件协议，不是统一JSON外壳。以下路径为直接访问8080时的路径，前端统一加/api。

| 模块 | 接口 |
| --- | --- |
| 公共 | GET /health；POST /user/auth/register、/user/auth/login、/admin/auth/login |
| 当前身份 | GET /user/me、/admin/me |
| 分类 | GET /user/categories、/admin/categories；管理员POST /admin/categories，PUT/DELETE /admin/categories/{id} |
| 菜品 | GET /user/dishes、/user/dishes/{id}；管理员同名路径/admin/dishes支持GET/POST及/{id} PUT/DELETE，PUT /{id}/status?status=0或1 |
| 套餐 | GET /user/setmeals、/user/setmeals/{id}；管理员/admin/setmeals同样提供CRUD和上下架 |
| 购物车 | GET/POST/DELETE /user/cart；PATCH /user/cart/{id}/reduce；DELETE /user/cart/{id} |
| 订单 | GET/POST /user/orders；GET /user/orders/{id}；POST /{id}/cancel、/{id}/again；管理员PUT /admin/orders/{id}/status?status=状态 |
| AI | GET /user/ai/status；POST /user/ai/chat、/chat/stream、/meal-plan、/rules |
| 管理员AI | POST /admin/ai/chat，输入message、days（1—90） |

菜品支持categoryId、keyword、page、size；套餐支持categoryId、page、size。分页1开始，size最多100。聊天请求`{"message":"我想吃辣的","conversationId":"自选会话标识"}`；conversationId可省略。预算套餐`{"message":"两个人吃午餐","budget":80}`。购物车`{"itemType":"DISH","itemId":1,"quantity":1,"flavor":""}`。下单`{"requestKey":"自选唯一请求键至少8位","address":"自填地址","contact":"自填联系信息"}`，相同用户相同requestKey幂等返回已有订单。

## AI设计

### Tool Calling

用户端只注册searchDish、getDishDetail、getAvailableDishes、getRecentOrders、getOrderDetail、getShoppingCart、calculateCartPrice。调用链是模型 → Java工具 → 业务Service → Mapper → MySQL，模型不能执行SQL。私有工具从后端ToolContext取得认证身份，模型schema没有userId；查询仍带归属条件。订单工具移除地址、联系人等非必要信息。管理员统计工具独立注册，普通聊天不可调用。

### Memory与流式输出

Memory键为认证userId + conversationId，最近20条消息，空闲30分钟过期，最多1000会话，同会话并发生成返回409，失败半截回复不落历史。重启丢失记忆，尚未持久化到Redis。

SSE POST输出delta/done/error。浏览器fetch带JWT和AbortController，后端有线程池、取消与超时清理，工具线程临时恢复身份后finally清理。默认Mock走真实流协议；真实模型流未验证。

### Structured Output

模型只返回reason和dishIds草案，Java重新查可售状态、去重、验候选ID、BigDecimal核价并检查预算。每个ID表示一份，返回菜品卡片；点击加购仍调用普通API。结构化格式正确不等于业务可信。Mock只按候选与预算演示，不声称理解人数、辣度或过敏原。

### RAG

knowledge文档 → 400字符Chunk（80重叠）→ 本地2048维词法Embedding → SimpleVectorStore → Top-3（相似度阈值0.10）→ 带来源上下文 → 模型。Embedding是离线字符/双字符哈希基线，**不是训练过的语义模型**。回答返回sources，没检索到依据则说明未知。示例规则不是商户承诺；实时菜品价格和订单必须走Tool，不能由文档代替。

### 经营统计

固定1—90天窗口，按上海日期和订单创建日期。趋势含所有状态；销量与金额仅当前已完成订单。单品销量不拆解套餐；金额不等于实际支付收入。Service验ADMIN，SQL固定参数化，返回原始汇总方便核对。

## 缓存与安全

分类TTL10分钟，可售菜品热点第一页TTL120秒，key前缀sky:menu:v1。Cache Aside，变更后事务提交再失效；核价和事务内读取不走缓存。失效失败/并发回填仍可能短暂陈旧，不宣称强一致。

安全设计、测试覆盖与上线缺口见 [SECURITY](docs/SECURITY.md)。AI只读工具、后端归属检查、金额核验是确定性边界；Prompt不能保证模型绝对不受注入。每身份AI生成12次/分钟，SSE45秒超时、最多16000字符。学习版没有完整登录防爆破、生产TLS、支付、审计或监控，不应直接开放公网。

## Docker方案（待网络恢复后验收）

Compose包含mysql、redis、backend、frontend、nginx。独立volume和网络，不挂载宿主数据库目录，不发布3306/6379，只绑定127.0.0.1:18088。Nginx /api代理移除前缀，关闭SSE缓冲。后端镜像非root运行。

先在当前终端用隐藏输入设置独立MYSQL_ROOT_PASSWORD、DB_PASSWORD，并生成JWT_SECRET；不要复用本机root密码。示例生成密钥（不打印）：

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:AI_MODE = 'mock'
# 通过Read-Host -AsSecureString设置MYSQL_ROOT_PASSWORD和DB_PASSWORD后再执行
# 使用--quiet避免展开后的配置把环境密码打印出来
docker compose config --quiet
docker compose build
docker compose up -d --wait
# 应再验证http://127.0.0.1:18088/api/health和网页
```

同一MySQL volume再次启动必须使用首次初始化时的容器密码。sql目录只在空volume首次启动时自动执行，参见[MySQL官方入口脚本](https://github.com/docker-library/mysql/blob/master/8.4/docker-entrypoint.sh)。默认Compose项目名sky-takeout-ai。停止使用`docker compose down`，**不要添加-v，也不要删除数据库volume**。

当前config已通过，build在auth.docker.io拉取基础镜像令牌时超时两次，没有执行up，没有创建本项目容器或数据卷。不要把已有hello-world通过当成本项目部署验收。基础镜像为官方Maven/Temurin、Node、MySQL、Redis、Nginx标签；首次成功拉取后可进一步锁定digest。

## 测试与当前证据

- 后端全量验收：RUN_DB_TESTS=true，mvn clean package，37项、0失败、0错误、0跳过，全部模块SUCCESS；真实MySQL与Redis参与。
- Maven Surefire强制AI_MODE=mock；即使外部终端设live，也不让普通测试调用收费模型。明确真实模型集成尚未验证。
- 前端npm run build成功；Element Plus全量引入带约1.05MB JS体积警告，尚未按需拆包。
- Chrome浏览器验证注册登录、空菜单/购物车/订单、Mock SSE和退出登录，无pageerror。未做有商品下单的浏览器全流程；后端真实事务测试覆盖下单与核价。
- Docker config通过，build网络阻塞，up及健康检查未执行。
- 当前会话凭据扫描覆盖可跟踪文件和Git历史；SQL检查无旧库操作及破坏性DDL。扫描不代表能发现所有未知秘密格式。

## 学习亮点与下一步

先沿着下单事务读代码，再读JWT身份链、缓存失效、只读Tool权限、结构化核价、Memory和RAG。亮点是业务事实与模型文本分离，以及明确记录Mock能力边界；不要在简历上声称真实支付、微服务、生产高并发或真实模型效果已经验证。

完整学习记录保留于LEARNING.md，INTERVIEW.md提供36个问答。下一步优先恢复Docker Hub连通性并完成部署验收，再自行创建管理员和少量菜单练习完整操作。后续真实模型效果评测、管理后台页面、口味选择器、语义Embedding、生产安全和可观测性均需独立安排。
