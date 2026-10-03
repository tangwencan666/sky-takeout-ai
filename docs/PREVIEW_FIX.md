# 15173预览登录故障排查与修复

时间：2026-10-03 10:53—11:03（Asia/Shanghai）。用户报告点击登录后顶部显示“服务暂时不可用”。

## 实际证据与定位范围

接手时同时存在两份本项目后端：8080为旧的Maven spring-boot:run进程，18080为前一次验收JAR；15173为Vite开发服务。分别直接发起演示账号登录，8080稳定返回500、18080返回200，而两个/health均返回200。

对旧8080进程进行一次短时JVM异常记录并再次触发登录，取得明确异常链：MySQL拒绝root@localhost认证（using password: YES）→CannotGetJdbcConnectionException→MyBatisSystemException。失败发生在AccountMapper.findUser，尚未进入密码验证和JWT签发。这证明该进程的数据库认证配置不可用；没有读取或输出其真实密码。

**本次新Chrome会话在修复前访问15173时，登录及菜单请求已经返回200。** 当时该Vite仍连向健康的18080。因此没有证据声称抓到了用户此前那一次15173失败请求，也不把“当时必然代理到了8080”写成确定事实。确认修复的是实际存在的异常并行后端及容易产生端口/临时环境混用的启动方式。

内置预览控制工具两次初始化失败（kernel assets路径错误），所以使用本机真实Chrome与现有Playwright打开同一15173地址执行UI操作，没有用MockMvc、接口脚本或请求拦截代替浏览器业务流程。

## 修复

1. 停止已核实属于本项目的旧8080开发进程、其Maven父进程及18080验收实例；未停止MySQL或Redis服务。
2. 新增[Start-Preview.ps1](../scripts/Start-Preview.ps1)：同一次调用使用当前终端环境中的DB_PASSWORD启动后端，JWT密钥缺失时在进程内临时生成；固定15173的/API_PROXY_TARGET到本次8080。没有永久改环境变量，没有写密码文件，也没有把凭据放入进程命令行。
3. 新增GET /health/ready：必须真实获取数据库连接，catalog严格等于sky_takeout_ai，再执行SELECT 1才返回200 ready。数据库认证、连接、查询或库名错误返回503安全提示；原/health仍只表示Web存活。
4. 启动脚本先检查端口未被占用；后端ready及15173/api/health/ready都成功才宣布启动完成。失败仅停止本次创建的子进程。已有实例时脚本拒绝重启，不杀未知进程。

## 修复后真实浏览器复验

使用用户指定的开发演示账号，在Chrome重新打开并刷新http://127.0.0.1:15173：

| 步骤 | 结果 |
| --- | --- |
| demo_user登录 | POST /api/user/auth/login=200；没有顶部错误提示 |
| 浏览菜单 | 12道菜、4套餐加载；分类/菜品/套餐请求全部200 |
| 登录后刷新 | GET /api/user/me=200，实际携带JWT，恢复身份与菜单 |
| 加购物车 | 空购物车加入宫保鸡丁1份、微辣，28.00元；POST/GET cart均200 |
| 确认下单 | 浏览器填写虚构测试信息并点击确认；POST /api/user/orders=200 |
| 订单详情 | 订单ID58，金额28.00，PENDING_PAYMENT，保留微辣快照；GET详情200 |

订单号：2452af8e-60db-4a24-8097-ca1b3c91a544。此笔是用户授权的本地验收订单，没有真实支付，保留在demo_user订单列表供核对；没有清空或删除原有3笔样例订单。

共22次API请求全部200，URL均为http://127.0.0.1:15173/api/...；0网络失败、0页面JS异常。浏览器与API同源，由Vite转发，不需要浏览器跨域直连8080；这条成功链路没有CORS故障。JWT由后端签发并在刷新/菜单/购物车/订单实际验签，没有放宽认证。

截图：[实际订单详情](screenshots/08-preview-repair-order.png)。机器可读证据：[preview-repair-results.json](verification/preview-repair-results.json)，只保存路径、状态、Authorization是否存在的布尔值，不保存Token或请求正文。

新增4项数据库就绪回归，最终mvn clean package为**62项、0失败/错误/跳过**，四模块SUCCESS。前端源码未改，浏览器重新实测；没有重复把旧前端构建当作本次登录证据。端口占用拒绝保护也已实际验证。

## 当前运行与后续启动

当前唯一预览配对为15173→/api→8080，两个ready接口均200且数据库为sky_takeout_ai。启动时PID写入被Git忽略的logs/preview/processes.json，日志也在该目录；没有在记录中保存凭据。

在已通过隐藏输入设置DB_PASSWORD的终端中执行：

```powershell
./scripts/Start-Preview.ps1 -JavaHome 'D:\java\jdk-17.0.20.1+1'
```

脚本使用已有构建产物和依赖；需要更新代码时先停止已确认的本项目实例再构建。再次访问旧页面可刷新并重新登录，后端重启可能使原Token失效，此时应401并重新登录，而不是绕过JWT。

旧数据库sky_take_out未读写；所有查询和本次订单写入均属于sky_takeout_ai。没有写入数据库密码、改变系统JAVA_HOME/PATH或执行git push。
