# 苍穹智膳 SkyTakeout AI

基于 Spring Boot + Vue3 + Spring AI 的智能外卖平台。

当前阶段：阶段3用户认证与JWT已实现，测试结果见学习笔记。Vue3、AI均为 **Planned**，尚未实现。

已实测：启用RUN_DB_TESTS后父工程和三个模块的`mvn clean test`成功，两个测试通过且Skipped=0；可执行jar打包及实际HTTP /health验证通过。新库sky_takeout_ai已创建，catalog断言通过。

## 本阶段技术栈

Java17、Spring Boot3.5.16、Maven3.9.11、MyBatis-Plus3.5.17（Boot3 starter）、MySQL8。
Spring Web、Validation、Lombok与Spring Boot Test为基础依赖。Redis、JWT和AI尚未引入。

## 目录与模块

```text
sky-takeout-ai/
├── pom.xml                 # 版本管理和聚合构建
├── sky-common/             # 公共jar，阶段1不增加业务类
├── sky-pojo/               # 数据模型jar，Entity/DTO/VO按后续阶段增加
├── sky-server/             # 唯一的Spring Boot启动模块
│   └── src/
│       ├── main/java/com/tang/sky/
│       │   ├── SkyApplication.java
│       │   └── controller/HealthController.java
│       ├── main/resources/application.yml
│       └── test/java/com/tang/sky/
│           ├── HealthControllerTest.java
│           └── DatabaseConnectionTest.java
├── sql/01_create_database.sql
├── docs/LEARNING.md
├── docs/DATABASE_DESIGN.md
├── .env.example
└── .gitignore
```

依赖方向：sky-server → sky-common、sky-pojo。三个模块共同组成单体应用，只有server启动。

## 本地启动（PowerShell）

先进入本项目根目录（pom.xml所在目录），仅为当前终端设置Java：

```powershell
$env:JAVA_HOME = 'D:\java\jdk-17.0.20.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

### 1. 手动创建新项目数据库

**旧库sky_take_out不得删除、修改或复用。唯一目标是sky_takeout_ai。**
在项目根目录开启MySQL客户端，密码由自己输入：

```powershell
mysql -h 127.0.0.1 -P 3306 -u root -p
```

在mysql提示符执行：

```sql
SHOW DATABASES;
```

确认sky_takeout_ai不存在后，执行：

```sql
SOURCE sql/01_create_database.sql;
SHOW CREATE DATABASE sky_takeout_ai;
EXIT;
```

若已存在同名库，先停止并核对归属，不覆盖、不清空。脚本只建库，不建表；字符集utf8mb4，排序规则utf8mb4_0900_ai_ci。

### 2. 临时设置认证信息

以下方式隐藏键入的密码，避免把密码字面量写进终端历史；密码只传给当前进程环境和子进程：

```powershell
$env:DB_USERNAME = 'root'
$dbCredential = Get-Credential -UserName $env:DB_USERNAME -Message '输入本机MySQL密码，仅用于当前终端'
if ($null -eq $dbCredential) { throw '已取消数据库凭据输入' }
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
Remove-Variable dbCredential
```

不要发送密码到聊天或提交Git。`.env.example`仅用于说明，Spring Boot不会自动读取`.env`。
主机与端口可用DB_HOST、DB_PORT覆盖；本阶段库名固定为sky_takeout_ai，DB_NAME不生效，防止误连旧库。

### 3. 构建与测试

```powershell
# 本轮最终验收必须启用真实数据库测试。
$env:RUN_DB_TESTS = 'true'
mvn clean test
```

应看到父工程及三个模块均SUCCESS，两个测试均通过且Skipped为0。未设置RUN_DB_TESTS时，默认只运行MVC测试，数据库测试会明确跳过；这种结果不能代表数据库验收完成。

### 4. 启动server

```powershell
mvn -DskipTests package
java -jar .\sky-server\target\sky-server-0.0.1-SNAPSHOT.jar
```

或先将模块安装到本地仓库，再单独调用server运行插件：

```powershell
mvn -DskipTests install
mvn -pl sky-server spring-boot:run
```

不要直接给所有模块执行`-am spring-boot:run`，common/pojo没有启动类。需要换端口时，在启动前设置`$env:SERVER_PORT = '18080'`。

在另一个终端请求：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/health
```

预期JSON：`{"code":200,"message":"success","data":{"status":"SkyTakeout AI is running"}}`，字段顺序不重要。
这个接口只验证Web存活。Hikari数据源可延迟建立连接，因此应用启动及/health成功都不能替代DatabaseConnectionTest。
测试与使用结束后可在原终端执行`Remove-Item Env:DB_PASSWORD`、`Remove-Item Env:RUN_DB_TESTS`清除临时值，或关闭终端。

## 常见问题

- Maven使用Java11：当前终端重新设置JAVA_HOME和PATH；不需要删除旧JDK。
- Access denied for user：核对本机环境变量与账号，不要把密码写入配置。
- Unknown database：新库尚未创建，按上文检查目标后执行脚本。
- No MyBatis mapper was found：阶段1没有Mapper，属于预期提示；后续业务阶段再增加。
- 父POM或依赖下载失败：核实仓库网络、镜像和版本，不擅自切换大版本。
- Maven跳过数据库测试：确认同一终端设置RUN_DB_TESTS=true，并查看Surefire统计。

## 后续计划

阶段2：Result、业务异常、全局异常处理、基础参数校验。后续再按顺序开发业务、Redis、Vue、Spring AI与RAG。
学习说明和当前实测结果见[LEARNING.md](docs/LEARNING.md)，数据库规划见[DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md)。


## 认证接口（阶段3）

- POST /user/auth/register：username、password；仅创建普通用户。
- POST /user/auth/login、POST /admin/auth/login：返回data.token和不含密码的用户VO。
- GET /user/me、GET /admin/me：Authorization: Bearer Token。
- 用户名3至32位字母/数字/下划线，密码8至64字符且UTF-8不超过72字节。
- 可配置JWT_SECRET为至少32字节随机密钥的Base64；默认使用进程内随机密钥，重启需要重新登录。
- 管理员表无默认账号、无默认密码、无匿名注册。阶段3数据库脚本为sql/02_auth.sql。

## 菜单接口（阶段4）

用户GET /user/categories、/user/dishes、/user/dishes/{id}；列表支持categoryId/keyword/page/size。后台/admin/categories支持GET/POST/PUT/{id}/DELETE/{id}，/admin/dishes支持相同CRUD及PUT /{id}/status?status=0或1。所有后台操作要求ADMIN。详情为data.dish与data.flavors，列表为data.records。

阶段5接口：用户GET /user/setmeals及/{id}；管理员/admin/setmeals支持GET/POST/PUT/{id}/DELETE/{id}及PUT /{id}/status?status=1。详情返回data.setmeal与data.dishes；创建body包含categoryId/name/price/description/dishes:[{dishId,quantity}]。

阶段6：GET/POST/DELETE /user/cart，PATCH /user/cart/{id}/reduce，DELETE /user/cart/{id}。添加body为itemType(DISH/SETMEAL)、itemId、flavor、quantity；返回items、totalPrice与checkoutAllowed。

阶段7：POST /user/orders接收requestKey/address/contact；GET /user/orders及/{id}；POST /{id}/cancel、/{id}/again。管理员PUT /admin/orders/{id}/status?status=WAITING_ACCEPT等按顺序流转，仅模拟状态，不接入真实支付。

阶段8：设置CACHE_ENABLED=true启用公开菜单Redis缓存；REDIS_HOST默认127.0.0.1，REDIS_PORT默认6379。支持REDIS_PASSWORD。分类TTL10分钟、菜品第一页TTL120秒；订单核价绕过缓存。

## Vue用户端（阶段9）

进入sky-web执行npm ci、npm run dev，访问终端显示地址；后端默认8080，Vite将/api代理到后端。生产构建npm run build。当前包含注册登录、菜单、购物车及订单页面；需要管理员录入上架商品后菜单才有内容。没有自动塞入虚假业务数据。

阶段10：Spring AI1.1.8已接入，默认AI_MODE=mock，GET /user/ai/status显示模式。真实模式需AI_MODE=live、AI_API_KEY、AI_BASE_URL、AI_MODEL；真实收费API尚未验证。默认DeepSeek兼容地址https://api.deepseek.com，模型deepseek-chat。
