# 苍穹智膳学习笔记

> 当前状态：阶段0、阶段1已验收。已获连续自主开发授权，后续每阶段通过测试后记录并提交，再进入下一阶段。历史暂停记录保留，当前结果以文末为准。
>
> 数据库保护约束：旧数据库 `sky_take_out` 不得删除、修改或复用。新项目使用独立数据库 `sky_takeout_ai`，仅在阶段1初始化。

## 阶段0：环境检查（2026-10-03）

### 今天实现了什么

只检查环境、确定初步技术路线，没有生成业务代码、安装软件、修改环境变量或数据库，也没有执行 Git 写操作。
检查前工作目录只有空的 outputs 和 work 目录，没有发现已有项目代码。
项目交付根目录为 outputs/sky-takeout-ai，本文件是当前唯一新增文件。

| 检查项 | 实测结果 | 判断 |
| --- | --- | --- |
| java / javac | 11.0.2 | 需要补充 JDK 17 |
| JAVA_HOME | C:\Program Files\Java\jdk-11.0.2 | Maven 同样使用 Java 11 |
| Maven | 3.9.11 | 版本可用；输出附带 Access is denied，构建尚未验证 |
| Node.js | 24.14.1 | 可用于后续前端开发 |
| npm | 11.11.0 | 命令可用，尚未测试依赖安装 |
| Git | 2.53.0.windows.2 | 命令可用 |
| Docker CLI | 29.7.2 | 已安装，但默认引擎连接失败 |
| Docker Compose | v5.4.0 | 命令可用，不能据此认定容器可运行 |
| MySQL 客户端 | 8.0.44 | 已安装 |
| MySQL 服务 | Running；127.0.0.1:3306 TCP 可连接 | 尚未认证，未确认服务端版本及建库权限 |
| Redis | redis-cli 不在 PATH；127.0.0.1:6379 连接超时 | 当前未验证可用，不能断言整台电脑未安装 |

Docker 的配置文件访问被当前执行环境拒绝，默认 docker_engine 命名管道不存在；com.docker.service 状态为 Stopped。需启动 Docker Desktop 后复查，当前不能判断是否存在其他可用 Docker context。未读取 Docker 配置内容。
检查了常见 JDK 目录，仅发现 JDK 11；没有做全盘扫描。

### 请求链路

本阶段没有 HTTP 接口。检查链路是：PowerShell → 工具可执行文件 → 版本输出；TCP 探测 → 本机端口。
后续传统业务采用：前端 → Controller → Service → Mapper → MySQL。
后续 AI 采用：AI Controller → AiService → LLM → Tool → 业务 Service → Mapper → MySQL。

### 核心代码

本阶段无 Java 核心代码。以下为已经执行过的主要检查命令，可在 PowerShell 复查：

```powershell
java -version
javac -version
mvn -version
node -v
npm -v
docker --version
docker compose version
docker info
docker ps
git --version
mysql --version
redis-cli --version
```

后续需要认证时，使用自己的数据库账号和交互式密码输入，不要把密码发到聊天中或写入命令参数：

```powershell
mysql -h 127.0.0.1 -P 3306 -u <你的数据库账号> -p
```

登录后可用 `SELECT VERSION();` 验证服务端版本。本次没有执行登录或 SQL。

### 为什么这样设计

先验证环境，才能区分工具配置问题和业务代码问题。Spring Boot 3.5 至少需要 Java 17，当前 Java 11 不满足要求。
保留旧 JDK 可以避免影响已有学习项目，后续为本项目设置 JDK 17，并检查 Maven 与 IDE 使用同一版本。
现有 MySQL 可以继续利用，不急于启动第二个 MySQL 容器占用 3306。
Redis 到阶段8才成为运行依赖；Docker 到阶段20完成全栈部署，二者不是现在编写基础后端的必要前提。

### 初步版本计划

- JDK：17 LTS，安装时选择该系列可用补丁版本。
- Maven：保留 3.9.11，项目编码显式使用 UTF-8；当前默认编码为 GBK。
- Spring Boot：拟锁定 3.5.16，阶段1实际验证依赖解析与构建。
- MyBatis-Plus：拟用 3.5.17 的 spring-boot3-starter。
- MySQL：先复用本机 8.0 系列，服务端版本待认证查询。
- Spring AI：拟用 1.1 系列，阶段10核验当时的补丁版本和 Boot 兼容性后锁定 BOM；当前未添加依赖。
- Node.js / npm：保留 24.14.1 / 11.11.0。
- Vue 3、Vite、Element Plus、Axios、Pinia：阶段9核验具体版本，提交 package-lock.json。
- Redis：阶段8选择受支持且兼容 Spring Data Redis 的具体镜像版本，届时验证并锁定。
- Docker / Compose：保留当前已安装版本，先修复引擎可用性。

后续依赖不使用动态 latest 作为可复现构建的版本依据。当前未完成的依赖解析、编译、数据库认证及容器启动，不记为测试通过。

参考：

- [Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [MyBatis-Plus 安装说明](https://baomidou.com/getting-started/install/)
- [Spring AI 1.1 发布说明](https://spring.io/blog/2025/11/12/spring-ai-1-1-GA-released/)
- [Vite 环境要求](https://vite.dev/guide/)

### 我必须记住的知识

1. java 是运行入口，javac 是编译器；两者都要检查。
2. Maven 的 Java 来源也要检查，不能只看 java -version。
3. 客户端命令存在、服务运行、端口可达、认证成功，是四个不同层次。
4. Docker CLI 和 Docker Engine 是不同组件；只有 CLI 无法运行容器。
5. 阶段验收必须记录未验证项，不能用版本输出代替实际构建结果。

### 面试可能怎么问

一分钟回答：我先检查 JDK、Maven、Node、数据库和容器环境，再搭建项目。发现本机 Maven 实际使用 Java 11，而 Spring Boot 3 需要至少 Java 17，所以要先统一项目 JDK。数据库还要区分端口可达与认证成功，Docker 也要区分客户端已安装与引擎可用。环境通过后再开始写业务，便于定位问题。

1. 为什么 java 和 mvn 的版本输出都要检查？
2. JAVA_HOME 和 PATH 分别影响什么？
3. MySQL 端口能连接，为什么不代表应用一定能访问数据库？
4. docker --version 成功而 docker ps 失败，可能是什么原因？

### 常见错误

- 安装新 JDK 后没更新当前终端或 IDE 的配置。
- 看到 Maven 版本输出就认为依赖下载和编译一定成功。
- 把找不到 redis-cli 等同于电脑一定没有 Redis。
- 把数据库密码写进 Git 或聊天记录。
- 环境尚有阻塞时提前宣称阶段已全部验收。

### 当前验收与下一步

环境检查已完成，但运行环境未全部就绪。下一步先补充 JDK 17，复查 Maven 权限提示并完成最小编译验证，再验证数据库账号权限，之后进入阶段1。Redis 和 Docker 的准备可在对应阶段之前完成。
阶段1只初始化 Maven 模块、Spring Boot 启动程序和数据库基础脚本；Result 与统一异常处理留到阶段2。

## 阶段0.5：环境修复与验证

### 本次真实结果与暂停位置

本次按照用户指定顺序，停在任务1：需要用户手动准备 JDK 17。没有进入阶段1，没有安装软件、修改系统环境变量或删除旧环境。

- `where.exe java` 和 `where.exe javac` 均指向 `C:\Program Files\Java\jdk-11.0.2\bin`。
- `JAVA_HOME` 为 `C:\Program Files\Java\jdk-11.0.2`，PATH 中的 Java 条目也是该目录的 bin。
- `mvn -version` 为 Maven 3.9.11 / Java 11.0.2，仍伴随 `Access is denied`，提示原因尚未查明。
- 检查了 Java、Adoptium、Microsoft、Corretto、Zulu、BellSoft、用户 .jdks、Scoop 等常见路径，以及 D 盘程序目录、下载目录、桌面、用户 Programs。补充搜索限制递归深度为4，未全盘扫描。
- 发现 JetBrains 工具自带 JBR：DataGrip 21.0.9、IDEA 21.0.4、PyCharm 21.0.7；这些不是本项目要求的 Java 17。
- 在上述已检查范围内未发现 JDK 17，不能据此断言所有磁盘都不存在 JDK 17。
- 用户补充 Redis 位置为 `D:\redis\Redis-x64-3.0.504`，Docker Desktop 已手动打开；这两项是用户提供的信息，本轮尚未复测。
- MySQL、Node/npm、Git 本轮尚未复测；阶段0.5最终验收未通过。

### 当前需要用户手动操作

推荐 Eclipse Temurin 17 LTS 的 Windows x64 JDK ZIP 包。它是 OpenJDK 发行版，可与 JDK 11 并存；ZIP 解压到有写权限的目录，无需修改系统环境变量或申请管理员权限。

1. 在 [Temurin 官方下载页](https://adoptium.net/temurin/releases/?version=17&os=windows&arch=x64&package=jdk)选择 Java 17、Windows、x64、JDK、HotSpot、ZIP。
2. 下载并解压，例如放到 `D:\Java`。确认实际 JDK 根目录下同时存在 `bin\java.exe` 与 `bin\javac.exe`。保留原 JDK 11。
3. 在 PowerShell 中执行下面的命令，将示例路径替换为实际路径。只影响当前终端及其启动的子进程，不会永久改变其他项目。

```powershell
$env:JAVA_HOME = 'D:\Java\实际的JDK17文件夹名'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
where.exe java
where.exe javac
java -version
javac -version
mvn -version
```

三个版本输出均须使用17；若仍有拒绝访问信息，也保留该信息供后续排查。此终端的变量不会自动传入已经运行的 Codex 或 IDE。用户完成后提供实际路径与上述版本结果，再继续验证。

### Java 学习要点

- JVM：执行 Java 字节码的虚拟机。
- JRE：运行 Java 程序所需的环境，包含 JVM 和运行库；现代 JDK 的打包方式不一定有单独的 jre 目录。
- JDK：开发工具包，提供编译器、运行环境及诊断工具。
- `javac`：把 Java 源代码编译成字节码；`java`：启动 JVM 运行程序。
- `JAVA_HOME`：约定的 JDK 根目录，不能填成 bin 目录。
- `PATH`：终端查找可执行程序的目录列表，靠前的匹配项通常优先。
- Maven 自身是 Java 程序。标准 Windows Maven 启动脚本优先使用 JAVA_HOME 指定的 Java，而直接输入 java 通常由 PATH 解析。因此 java 显示17，Maven仍可能显示11；构建 toolchain 或 IDE 配置还可能另选编译 JDK。
- Spring Boot 3 的最低 Java 基线是17，本项目统一使用17以减少编译、运行和学习环境之间的差异。

### MySQL 学习要点

MySQL Server 负责存储数据、执行 SQL 与权限检查；mysql 客户端发送命令。Windows Service 让服务端在后台运行。3306 是默认 TCP 端口，可配置。数据库账号标识身份，密码用于认证；认证成功不等于具备建库权限。后续由用户自己在终端输入密码，验证 `SELECT VERSION();`、`SHOW DATABASES;` 和 `SHOW GRANTS;`，涉及角色授权时还需核对有效角色。此阶段不创建业务表。

### Redis 学习要点

Redis Server 是处理缓存请求的服务端，Redis CLI 是命令行客户端；6379 是可配置的默认端口。`redis-cli ping` 向服务端发送 PING，收到 PONG 说明该次连接能完成 Redis 协议请求；不代表已经验证所有读写功能。
现有 Windows Redis 3.0.504 先用于 PING/PONG 和基础命令学习，不删除、不重新安装。它与后续新版本在命令和特性上可能存在差异，正式框架兼容性要另行验证。后续 Docker Redis 可固定版本并统一开发与部署环境；若与本地 Redis 同时运行，应避免宿主机端口冲突。

### Docker 学习要点

Docker CLI 是发送命令的客户端；Docker Desktop 提供桌面管理及 Windows 上的容器运行环境；Docker Engine 是实际管理镜像、网络和容器的服务。Image 是创建容器的模板，Container 是镜像的运行实例，Compose 用声明式配置组织多个服务。
`docker --version` 只查询本地 CLI 版本，`docker info` 需要连接 Engine。上次检查默认引擎管道不存在，因此后者失败；本次用户已打开 Desktop，但尚未复测，不能沿用上次失败判断。后续必须验证 Server 信息和 hello-world 实际运行。

### 阶段0面试题

1. **JDK、JRE、JVM有什么区别？** JVM执行字节码，JRE提供运行环境，JDK在运行能力之外提供编译和开发工具。
2. **java和javac有什么区别？** javac编译源代码，java启动虚拟机运行字节码。
3. **JAVA_HOME有什么作用？** 为Maven等工具指明JDK根目录，本身不会自动改变PATH。
4. **PATH有什么作用？** 让终端按目录顺序查找可执行文件，影响直接输入java时选中哪个版本。
5. **Maven使用哪个JDK？** 标准启动脚本优先看JAVA_HOME，使用mvn -version核实；编译toolchain可以另外指定。
6. **为什么Spring Boot 3需要Java 17？** 它将Java 17作为最低支持基线，旧JDK不满足框架编译和运行要求。
7. **MySQL Server和Client有什么区别？** Server执行SQL和保存数据，Client负责连接并发送请求。
8. **Redis Server和CLI有什么区别？** Server提供缓存服务，CLI是访问它的命令行工具。
9. **redis-cli ping有什么作用？** 发送PING并检查响应，PONG是验证Redis连通性的一种最小检查。
10. **为什么Redis默认使用6379端口？** 这是Redis约定的默认服务端口，并非协议强制要求，可以配置成其他端口。
11. **Docker Desktop和Engine有什么区别？** Desktop是桌面管理和集成环境，Engine负责实际运行与管理容器。
12. **为什么docker --version成功而docker info失败？** 前者只需要CLI，后者需要能够连接正常运行的Engine。
13. **镜像和容器有什么区别？** 镜像是模板，容器是基于模板创建的实例，拥有自身运行状态和可写层。
14. **Docker Compose是干什么的？** 用一份配置统一定义和管理多个容器服务及其网络、卷和启动配置。

### 一分钟复习回答

我按安装、命令、服务、端口、认证几个层次验证环境。例如Java和Maven可能使用不同的JDK，因此需要分别查看版本；MySQL客户端可用不代表账号能登录；Docker命令存在不代表引擎启动。当前找到的项目JDK是11，我先保留旧环境，再通过独立的JDK17和会话环境变量准备新项目。其他组件按顺序复测，全部满足验收条件后才初始化项目。

### 阶段0.5续检：JDK已通过，等待启动Redis

本节更新前面的暂停状态，保留前文作为检查历史。

用户已在自己的 PowerShell 验证：java / javac 为17.0.20.1，发行版 Eclipse Temurin 17.0.20.1+1；Maven 3.9.11 使用 Eclipse Adoptium Java 17.0.20.1，运行路径 `D:\java\jdk-17.0.20.1+1`。该次 Maven 没有 Access is denied。这四项标记为“用户终端验证通过”，本轮遵照用户要求没有重新检查JDK。
JDK切换仅在用户PowerShell会话生效，不代表系统默认Java或当前代理进程环境已永久切换。没有修改永久JAVA_HOME/PATH，没有删除JDK11。

本轮代理实际执行的Redis检查：

- `D:\redis\Redis-x64-3.0.504\redis-server.exe` 存在，大小1553408字节。
- `D:\redis\Redis-x64-3.0.504\redis-cli.exe` 存在，大小482304字节。
- `redis-server.exe --version` 返回 Redis server v=3.0.504，64位。
- `redis-cli.exe --version` 执行退出码0，但当前工具未捕获到版本文本，不能冒充已得到CLI版本号。
- `redis-cli.exe -h 127.0.0.1 -p 6379 ping` 失败，连接被拒绝，没有PONG。
- `Get-Process -Name '*redis*'` 未找到进程；`Get-Service -Name '*redis*'` 未找到服务；`netstat -ano -p tcp` 未发现6379条目。
- 目录已有dump.rdb和配置文件，本轮没有修改、删除这些文件，也没有重新安装Redis。

按用户“需要手动操作就停下来”的要求，本次停在Redis启动步骤。请用户在PowerShell执行：

```powershell
Set-Location 'D:\redis\Redis-x64-3.0.504'
.\redis-server.exe --bind 127.0.0.1 --port 6379
```

该命令以前台方式启动，仅绑定本机；保持窗口打开。在另一个PowerShell执行：

```powershell
& 'D:\redis\Redis-x64-3.0.504\redis-cli.exe' -h 127.0.0.1 -p 6379 ping
```

预期PONG，真实结果待用户操作后复验。若启动报错，提供错误文本即可，不需重复启动或安装服务。

| 组件 | 版本 | 当前验证状态 | 项目用途 |
| --- | --- | --- | --- |
| Java / javac | 17.0.20.1 | 用户终端验证通过 | 后端运行与编译 |
| Maven | 3.9.11，使用Java17 | 用户终端验证通过 | 后端构建 |
| Redis Server | 3.0.504 | 程序存在，未启动，PING失败 | 基础缓存学习 |
| Redis CLI | 版本文本未捕获 | 可执行，尚未连接成功 | Redis命令行验证 |
| Docker / Compose | 前次29.7.2 / v5.4.0 | 本轮未复验，等待Redis步骤完成 | 容器化与编排 |
| MySQL | 前次客户端8.0.44 | 本轮未复验；认证与建库权限待验证 | 业务数据库 |
| Node / npm | 前次24.14.1 / 11.11.0 | 最终复验待执行 | 前端工具链 |
| Git | 前次2.53.0 | 最终复验待执行 | 版本控制 |

本轮只修改本学习文件，没有新增文件。阶段0尚未满足全部验收条件，没有进入阶段1。

### 阶段0.5续检：Redis与Docker通过，等待MySQL认证

本节更新上一轮暂停状态。Java17、javac17、Maven使用Java17继续采用用户终端已验证结果，本轮没有重新检查或永久修改Java配置。

本轮实际执行结果：

1. 现有Redis CLI执行 `-h 127.0.0.1 -p 6379 ping` 返回 `PONG`，退出码0。Redis基础连通性通过。
2. 沙箱内Docker检查出现配置文件访问被拒绝和引擎管道permission denied。经沙箱外执行检查成功，实际context为desktop-linux；没有修改Docker权限或系统配置。
3. `docker version` 同时显示Client和Server，Engine为29.7.2，Desktop为4.88.0；`docker info`显示Server信息及WSL2内核。证明当前引擎可以连接，不是仅有客户端。
4. `docker compose version` 返回v5.4.0；`docker ps`成功。
5. `docker run --rm hello-world` 输出 `Hello from Docker!`，退出码0，之后再次执行docker ps成功。测试容器退出后自动删除。测试证明镜像可用且容器创建、启动、退出成功；未单独证明本轮发生了新的镜像下载。没有操作已有项目容器。
6. MySQL Windows服务为Running；客户端版本8.0.44；TCP主动探测127.0.0.1:3306成功。netstat当前输出未列出3306，因此以实际TCP探测作为端口可达证据。服务端版本、账号登录及建库权限仍未验证。

当前需要用户手动操作：在自己的PowerShell执行以下命令，密码只在该终端输入，不发送到聊天，不写入文件。

```powershell
mysql -h 127.0.0.1 -P 3306 -u root -p
```

登录成功后执行只读验证：

```sql
SELECT VERSION();
SELECT CURRENT_USER();
SHOW DATABASES;
SHOW GRANTS;
```

`SHOW DATABASES`成功不代表有建库权限。检查有效授权是否包含全局 `CREATE` / `ALL PRIVILEGES ON *.*`，或者覆盖目标 `sky_takeout_ai` 数据库的 `CREATE` 权限。如果授权通过角色提供，再核对当前激活角色及其授权。此轮不实际创建数据库或业务表。
用户只需反馈服务端版本、SHOW DATABASES是否成功，以及与CREATE/ALL PRIVILEGES相关的授权行；无需发送密码或完整数据库列表。如有报错，提供报错内容以继续排查。

| 组件 | 当前状态 |
| --- | --- |
| Java17 / javac17 / Maven3.9.11使用Java17 | 用户终端验证通过 |
| Redis3.0.504 | 本轮PING/PONG通过 |
| Docker Engine29.7.2 | 本轮Server检查与hello-world通过 |
| Compose v5.4.0 | 本轮版本命令通过；多服务部署留待后续阶段 |
| MySQL客户端8.0.44 | 服务Running、TCP3306可达；待用户认证与权限验证 |
| Node / npm / Git | 按顺序等待MySQL步骤完成后最终复验 |

阶段0仍未全部通过。本轮仅追加学习笔记，没有进入阶段1。

## 阶段0最终验收

### 今天完成了什么

用户已在自己的终端完成MySQL最终验证：服务端为MySQL Community Server 8.0.44，连接127.0.0.1:3306，当前账号root@localhost；SELECT VERSION()、SHOW DATABASES和SHOW GRANTS均成功。用户确认该账号具有全局CREATE及SELECT、INSERT、UPDATE、DELETE、DROP、INDEX、ALTER、CREATE VIEW、TRIGGER、CREATE USER等权限，并带WITH GRANT OPTION。由此确认登录与CREATE DATABASE权限通过；本轮没有重新登录、索取密码或执行数据库写操作。

代理本轮最终执行：`node -v` 返回v24.14.1，`npm -v` 返回11.11.0，`git --version` 返回2.53.0.windows.2；三个命令退出码均为0。没有升级、降级、重新安装或修改全局配置。

### 最终环境表

| 组件 | 版本 | 验证结果与来源 | 项目用途 |
| --- | --- | --- | --- |
| Java | Eclipse Temurin 17.0.20.1+1 | 通过；用户终端验证，路径D:\java\jdk-17.0.20.1+1 | Spring Boot运行 |
| javac | 17.0.20.1 | 通过；用户终端验证 | Java编译 |
| Maven | 3.9.11 | 通过；用户终端确认使用Java17，未出现Access is denied | 后端构建 |
| MySQL Server / Client | 8.0.44 / 8.0.44 | 通过；代理验证服务与TCP3306，用户验证root认证及全局CREATE权限 | 业务数据库 |
| Redis Server | 3.0.504 | 通过；代理使用现有CLI连接127.0.0.1:6379得到PONG | 基础缓存学习 |
| Docker Engine | 29.7.2 | 通过；代理在沙箱外验证Server、docker ps及hello-world退出码0 | 容器运行 |
| Docker Compose | v5.4.0 | 通过；版本命令正常，多服务编排尚未实施 | 后续多容器编排 |
| Node.js | 24.14.1 | 通过；代理本轮最终复验退出码0 | Vue3开发工具链 |
| npm | 11.11.0 | 通过；代理本轮最终复验退出码0 | 前端依赖管理 |
| Git | 2.53.0.windows.2 | 通过；代理本轮最终复验退出码0 | 版本控制 |

### 保留的约束与验证边界

- 旧数据库 `sky_take_out` 必须保护：不得删除、修改或复用。
- 新项目独立使用 `sky_takeout_ai`；本阶段没有创建它，数据库初始化留到阶段1。
- JDK11保留。JDK17只在用户PowerShell会话中临时切换，没有永久修改系统JAVA_HOME或PATH。后续构建需在相应会话中显式使用已确认的JDK17。
- Redis沿用D:\redis\Redis-x64-3.0.504，没有重新安装或删除。PING通过只证明基础协议连通；正式框架兼容性和缓存功能在后续阶段验证。
- Docker沙箱内访问受限，沙箱外检查正常；没有为解决该限制修改系统权限或重装Docker。
- 本阶段确认开发环境条件，尚未执行项目Maven构建、npm依赖安装或Vue构建，不能将其记录为已通过。

### 文件变化与学习总结

本轮只更新docs/LEARNING.md，没有新增代码文件或修改已有项目、数据库、系统配置。
环境排查顺序是命令可用→服务运行→连接成功→认证和权限确认。不同组件使用不同验证方式：Java检查运行与编译版本、Maven检查实际JDK、Redis使用PING/PONG、Docker使用Server信息和真实容器执行、MySQL由用户在本地完成认证与权限核对。

### 验收结论与下一阶段预告

阶段0已经具备进入阶段1的条件。

阶段0正式结束；未自动进入阶段1。等待用户确认后，阶段1仅进行项目初始化、多模块结构（sky-common、sky-pojo、sky-server、sky-web）、基础依赖和独立数据库sky_takeout_ai的初始化，不触碰旧数据库sky_take_out。

## 阶段1：项目初始化

### 今天实现了什么

在现有outputs/sky-takeout-ai根目录直接增加父POM和sky-common、sky-pojo、sky-server三个模块，保留全部学习记录。本阶段按最新要求不创建sky-web模块。统一com.tang.sky包名、Java17、UTF-8、Spring Boot3.5.16和MyBatis-Plus3.5.17的Boot3 starter。
实现唯一启动类SkyApplication和极简GET /health；新增MVC切片测试及可显式启用的真实DataSource连接测试。提供只创建sky_takeout_ai的SQL脚本、配置示例、Git忽略规则、README及数据库模型规划。没有增加登录、JWT、Redis或AI等后续阶段功能。

### Maven多模块是什么

一个项目拆成多个可单独编译的模块，由Reactor根据依赖顺序统一构建。当前仍是一个单体应用，不是多个部署服务。

### 父pom是什么

父POM用packaging=pom集中维护版本、编译编码与构建配置，并通过modules聚合子模块。本项目父POM继承Spring Boot parent，复用其经过配套的依赖和插件版本。

### module是什么

module声明“把这个子目录加入本次聚合构建”。它不是Java的JPMS module-info模块，也不自动建立模块间依赖。

### dependency是什么

dependency声明“这个模块编译或运行需要某个jar”。server通过dependencies引入common和pojo，Maven据此先构建被依赖模块。

### dependencyManagement是什么

集中规定依赖版本与默认配置，不会自动引入jar。子模块必须再写dependencies才能使用；父工程用它统一内部模块和MyBatis-Plus版本，Boot parent管理Web、MySQL驱动等版本。

### 为什么拆成sky-common、sky-pojo、sky-server

common未来承载Result、常量、异常和Context，pojo承载数据模型，server承载启动和业务。技术上公共代码可以全部放server，但那会使复用方依赖整个运行模块，也不利于区分通用基础设施与业务；本项目按职责分离，避免循环依赖，并保持模块数量有限。
Entity对应持久化数据模型，DTO描述接口输入或层间传输数据，VO描述返回给前端的数据视图。它们不一定逐字段相同，例如密码散列属于持久化信息，不应出现在用户信息VO。pojo目前不提前生成实体，随业务逐步加入。

### Spring Boot启动类是干什么的

main调用SpringApplication.run，创建应用上下文、装配组件并启动内嵌Web服务器。只有server拥有启动入口，其他模块只是普通jar。

### @SpringBootApplication是什么

它组合了SpringBootConfiguration、EnableAutoConfiguration和ComponentScan：定义配置入口、按依赖及条件自动配置、扫描组件。启动类放com.tang.sky根包下，便于找到controller等子包组件。

### application.yml是什么

应用的层级配置文件，维护端口、数据源、应用名等设置。`${DB_PASSWORD:}`从环境读取密码；没有设置时为空，而不是偷偷填入真实密码。主机和端口可变，本阶段库名固定为sky_takeout_ai，防止误连旧库。`.env.example`是文档，不会被Spring Boot自动加载。

### DataSource是什么

获取数据库连接的接口，Boot根据配置创建Hikari数据源并管理连接池。池中的连接通常可复用，使用后必须close归还。只成功创建DataSource对象或启动Web服务不代表连接认证通过，所以单独调用getConnection、isValid和getCatalog做验收。

### JDBC是什么

Java访问关系数据库的标准API。MySQL Connector/J实现该API并负责和MySQL服务端通信，DataSource提供的Connection使用的也是JDBC接口。

### MyBatis和MyBatis-Plus是什么

MyBatis帮助组织SQL和对象映射；MyBatis-Plus在它之上提供通用CRUD等能力，仍使用MyBatis和JDBC。本阶段仅验证Boot3 starter装配，不需要为它建立测试表或伪造Mapper。没有Mapper时扫描警告是预期的。

### 为什么数据库密码不能提交Git

Git保留历史，后续删除明文也不能使已泄露的凭据失效。配置只提交环境变量占位符，密码由用户在本机交互输入，既不发到聊天也不写入README、SQL或POM。

### 请求链路

浏览器 → GET /health → Spring Boot内嵌Tomcat → DispatcherServlet → HealthController → JSON序列化 → HTTP响应。
Tomcat接收网络请求，DispatcherServlet寻找对应路由方法，Controller返回Map，由Jackson转为JSON。这个接口没有业务规则或数据库访问，因此不额外建立空Service。阶段2再统一Result类型。

### 数据库连接链路

Spring Boot → application.yml及环境变量 → DataSource → MySQL Driver（实现JDBC）→ MySQL Server → sky_takeout_ai。
配置决定地址与认证，DataSource负责提供连接，驱动通过JDBC API完成网络协议转换，MySQL校验账号权限并选择数据库。
DatabaseConnectionTest通过try-with-resources确保连接归还，并精确断言catalog为sky_takeout_ai；不创建表或读写业务数据。

### 为什么分开测试

HealthControllerTest只加载MVC层，验证路由、HTTP状态与JSON字段，不需要数据库密码。DatabaseConnectionTest只有RUN_DB_TESTS=true才启用，启用后使用真实数据源，失败即构建失败。默认跳过数据库测试的构建不能作为阶段1完整验收。
用户在根目录输入本机凭据并设置RUN_DB_TESTS=true后执行mvn clean test。详细命令在README中；期望两个测试通过且Skipped=0。

### 文件变化

新增：父pom、三个子pom、两个空源码目录占位文件、SkyApplication、HealthController、application.yml、两个测试类、sql/01_create_database.sql、.env.example、.gitignore、README.md、docs/DATABASE_DESIGN.md。只修改已有docs/LEARNING.md，未覆盖历史段落。

### 数据库状态

本轮没有执行数据库认证或任何SQL。sky_takeout_ai是否已存在需要用户登录后执行SHOW DATABASES确认；确认不存在才执行创建脚本。旧库sky_take_out未被本轮操作修改。阶段1不创建业务表、AI表或system_test。

### 阶段1面试题

1. **Maven多模块有什么好处？** 按职责组织代码，集中管理版本，并按依赖顺序统一构建，减少耦合和重复配置。
2. **parent pom有什么作用？** 子模块继承其中的版本与构建约定；父工程也可以通过modules统一聚合构建。
3. **dependencyManagement和dependencies区别？** 前者规定版本但不引入依赖，后者真正把依赖加入模块。
4. **Spring Boot启动流程？** main调用SpringApplication，创建上下文、读取配置、自动装配并启动内嵌服务器。
5. **@SpringBootApplication包含什么？** Boot配置声明、自动配置和组件扫描三个核心能力。
6. **application.yml有什么作用？** 配置应用运行参数，用环境变量将环境差异与源代码分开。
7. **JDBC是什么？** Java访问关系数据库的标准接口，由数据库厂商驱动实现。
8. **DataSource是什么？** 获取连接的入口，常由连接池实现，降低反复创建连接的成本。
9. **MyBatis和MyBatis-Plus有什么区别？** MyBatis提供SQL映射，Plus在其基础上提供通用CRUD等增强，仍保留SQL控制能力。
10. **为什么密码不能写死在yml？** 源码和Git历史可能传播，环境变量让敏感配置留在运行环境。
11. **Controller是干什么的？** 接收HTTP输入、调用应用逻辑并返回响应，不应承担复杂业务和直接访问Mapper。
12. **HTTP请求如何进入Spring Boot？** 内嵌服务器接收连接，DispatcherServlet匹配Controller映射，返回对象经序列化形成HTTP响应。

### 一分钟架构回答

我的项目采用Java17和Spring Boot3单体架构，通过Maven拆成common、pojo和server三个模块。父POM统一依赖版本和编译配置，common放公共基础能力，pojo放数据模型，server是唯一启动模块。目前建立了健康检查接口，验证HTTP进入Spring MVC并返回JSON；数据库使用MySQL和MyBatis-Plus，密码从环境变量读取，新项目使用独立数据库。MVC测试和真实数据库连接测试分别验证Web契约与数据源连接，后续会在这个骨架上逐阶段增加业务功能。

### 常见错误与下一阶段

不要把modules误认为dependencies；不要给common和pojo运行Boot插件；不要把/health成功当成数据库通过；不要把RUN_DB_TESTS未启用导致的Skipped记作成功；不要误把旧库sky_take_out填入新项目配置。
阶段2预告：Result<T>、BusinessException、GlobalExceptionHandler、基础常量、统一返回结构及参数校验基础。本轮不实施阶段2。

### 本轮实际执行结果与暂停位置

1. 首次在沙箱内执行`mvn -B -ntp clean test`，因无法写入已有Maven本地缓存而失败；不是Java源码编译错误。沙箱外重试成功，未修改系统权限、全局Maven配置或依赖版本。
2. 沙箱外构建显式使用当前进程的`D:\java\jdk-17.0.20.1+1`。父工程、sky-common、sky-pojo、sky-server均SUCCESS。Spring Boot3.5.16和MyBatis-Plus3.5.17依赖解析成功，Java源码以release17编译。
3. Surefire结果：Tests run:2，Failures:0，Errors:0，Skipped:1。HealthControllerTest通过，DatabaseConnectionTest因RUN_DB_TESTS未启用而跳过。没有把跳过记作数据库验证通过。
4. `mvn -B -ntp -DskipTests package`通过，生成可执行server jar；common与pojo尚无类，空jar警告符合本阶段范围。
5. 实际启动server jar，绑定127.0.0.1并使用随机空闲端口49857，日志确认Java17.0.20.1与Spring Boot启动成功。GET /health返回HTTP200及`{"code":200,"message":"SkyTakeout AI is running"}`。检查后只停止本次创建的验证进程，没有留下测试服务或停止其他进程。
6. 初始化本项目独立Git仓库，未修改全局配置、暂存或提交。git status显示新增源码和文档；git check-ignore验证target与.env被忽略，.env.example可跟踪。建议全部验收后提交`feat: initialize sky takeout ai backend`。
7. SQL脚本仅含CREATE DATABASE IF NOT EXISTS sky_takeout_ai，尚未执行；没有数据库表，没有数据库写操作，因此旧库sky_take_out未被本轮修改。

**当前暂停原因：新数据库是否存在、创建结果及真实DataSource连接需要用户在自己的终端安全输入MySQL密码后验证。** README已给出完整操作步骤。下一步先SHOW DATABASES确认目标不存在，再执行脚本；设置DB_PASSWORD和RUN_DB_TESTS=true后，从父工程执行mvn clean test，应得到两个测试通过且Skipped=0。密码不发送到聊天。
阶段1当前完成代码、文档、构建和Web启动验证，数据库两项待完成，不能宣布阶段1全部完成；阶段2未开始。

### 阶段1最终验收（连续开发授权后）

通过当前进程已有DB_PASSWORD临时凭据认证，先查询information_schema确认sky_takeout_ai不存在，再校验脚本仅包含预期CREATE DATABASE语句后执行。SHOW CREATE DATABASE确认utf8mb4/utf8mb4_0900_ai_ci；没有业务表，没有访问或写入旧库。
显式开启RUN_DB_TESTS=true运行mvn clean test：HealthControllerTest与DatabaseConnectionTest均通过，Tests=2、Failures=0、Errors=0、Skipped=0，父工程及三个模块均SUCCESS。DataSource连接catalog严格为sky_takeout_ai。阶段1全部验收通过，进入阶段2。
密码只从环境变量读取，没有放入脚本、配置、源码或日志。Git提交前检查所有候选文件是否包含当前临时凭据，不输出凭据本身。

## 阶段2：统一返回与异常处理

实现Result<T>、ErrorCode、BusinessException和GlobalExceptionHandler。/health现在使用code/message/data信封；HTTP状态同时表达失败类别，前端不必猜测业务返回格式。
调用链：DispatcherServlet → Controller → Result；抛出异常时由RestControllerAdvice转换响应。校验失败不回显原输入，未知异常只返回通用错误，日志仅记录异常类型。Controller不需要重复try-catch。
核心代码：sky-common/common中的Result与错误类型、sky-server/handler/GlobalExceptionHandler。常见错误是所有异常返回HTTP200、把SQL消息或密码字段返回客户端。
实测mvn clean test：6项通过，0失败、0错误、0跳过，包含真实数据库连接。新增测试覆盖业务异常、空输入、非法JSON及未知异常不泄露；没有为了通过而禁用测试。
面试回答：我用统一泛型结果让前端稳定读取code/message/data，通过全局Advice集中处理业务、校验和未知异常，既保留HTTP状态语义，又避免每个Controller重复捕获和泄露底层信息。
面试问题：统一返回与HTTP状态如何配合？Advice何时执行？为什么不直接返回异常message？参数校验应该放哪里？

## 阶段3：用户与JWT认证

新增user与employee表，仅在sky_takeout_ai建表；普通注册只创建USER，管理员不开放匿名注册。Controller → AuthService → AccountMapper → MySQL；登录验证BCrypt散列后签发两小时JWT，后续请求由Interceptor验签、校验账号状态、角色与UserContext，结束及转异步时remove上下文。
BCrypt成本12，限制UTF-8密码不超过72字节，避免截断语义。用户名唯一约束防并发重复，找不到账号也做一次dummy散列校验并统一失败信息。JWT_SECRET可从环境提供Base64密钥；未配置时只生成进程内随机密钥，重启会使旧Token失效，不把固定开发密钥写入源码。
核心代码：AuthModels区分Account/DTO/VO，AccountMapper参数绑定，AuthServiceImpl控制注册和登录，JwtService使用JJWT0.13.0，AuthInterceptor与WebConfig保护用户/管理员路径。服务端不信任客户端userId，不返回密码散列。
测试首次发现servletPath为空导致角色判定漏检，修复为读取Spring已匹配路由，保留越权回归断言。测试包含真实数据库注册与散列、正确/错误/不存在账号、重复注册、无Token、非法/过期Token、普通用户访问后台以及用户上下文隔离。测试数据放事务中回滚。
面试回答：登录先通过BCrypt验证数据库散列，再由后端签发JWT；拦截器校验签名、过期时间、issuer和角色，并重新确认账号可用。当前身份进入线程上下文供业务使用，请求完成必须清理，防止线程池复用导致串用户。管理员权限不由注册请求决定。
面试问题：JWT如何防篡改？为什么不用明文或MD5保存密码？ThreadLocal为何必须remove？JWT重启后是否有效？普通用户如何与管理员隔离？

阶段3最终重测：10项测试全部通过，Failures=0、Errors=0、Skipped=0，四个Reactor工程SUCCESS。
