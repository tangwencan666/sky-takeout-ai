# 夜间开发报告

日期：2026-10-03（Asia/Shanghai）

## 完成到哪个阶段

**阶段1—19已实现、测试并提交。阶段20的Docker配置已生成，config通过，但镜像下载网络阻塞，构建/启动/健康检查尚未验收。阶段21完成本机最终测试与交接文档，整体部署验收仍未闭环。**

没有为了越过失败修改系统网络、跳过测试、删除数据库或执行git push。已确认旧数据库sky_take_out全程未读写、未复用；唯一操作数据库为sky_takeout_ai。永久JAVA_HOME/PATH未改，JDK11未删除。

## 每阶段commit

| 阶段 | commit | 内容 |
| --- | --- | --- |
| 1 | 0d79d04 | backend初始化、独立新库与真实连接 |
| 2 | b879e11 | 统一响应与异常 |
| 3 | e082ba6 | BCrypt、JWT、认证与上下文 |
| 4 | 4049f35 | 分类、菜品、口味 |
| 5 | 11385ac | 套餐与组成 |
| 6 | ed6ae51 | 购物车与隔离 |
| 7 | 51f31d5 | 订单、核价、幂等、状态 |
| 8 | 63de4a3 | Redis Cache Aside |
| 9 | 2a52d8a | Vue用户端 |
| 10 | cf735eb | Spring AI与Mock模型 |
| 11 | 709cb82 | AI聊天接口 |
| 12 | adfb14f | 菜品只读工具 |
| 13 | aed1466 | 私有订单/购物车工具 |
| 14 | 8a6efcc | 用户隔离Memory |
| 15 | 61d319b | 结构化预算套餐 |
| 16 | 3db2b94 | SSE与前端流式聊天 |
| 17 | 8176b94 | 本地规则RAG |
| 18 | fdf3aef | 管理员聚合分析 |
| 19 | 7933dfc | 认证与AI安全加固 |
| 补充 | 100f0b3 | 切换账号清理前端私有状态 |
| 补充 | 53f224e | 测试强制Mock，防继承终端live模式 |
| 20 | 未提交完成commit | Docker未通过部署验收，文件保留工作区 |
| 21 | 文档检查点 | 本报告与指南单独保存为docs提交；用git log -1查看，不将其当作Docker通过 |

Docker待验收文件：Dockerfile、sky-web/Dockerfile、docker-compose.yml、docker/、.dockerignore及.env.example变更。按阶段规则，这些尚未通过部署测试的改动不伪装成已完成提交。源码阶段与文档检查点已在本地Git保存，**未push**。

## 测试情况

| 检查 | 实际结果 |
| --- | --- |
| mvn clean package，RUN_DB_TESTS=true | 20个测试类、37项，Failures=0、Errors=0、Skipped=0；所有模块SUCCESS |
| MySQL | 真实127.0.0.1:3306，catalog明确为sky_takeout_ai |
| Redis | 真实本机Redis；验证Miss、Hit、不调用Mapper的命中、修改后失效 |
| AI测试 | Spring AI真实调用链+Mock ChatModel；没有真实收费请求 |
| SSE | 随机端口真实HTTP事件delta/done，取消与工具身份清理测试通过 |
| 前端 | npm run build成功，约1.05MB JS体积警告尚待优化 |
| Chrome浏览器 | 注册登录、空菜单/购物车/订单、Mock SSE、退出登录通过；pageErrors=0 |
| 有商品下单浏览器流程 | 尚未验收；后端真实SQL事务测试已覆盖 |
| Docker config | 通过（--quiet不打印展开的秘密） |
| Docker build | 两次失败：auth.docker.io令牌请求连接超时；应用构建尚未开始 |
| Docker up / health | 未执行，不能标通过；本项目没有容器或数据卷 |
| 安全检查 | 当前会话秘密未出现在可跟踪文件/Git历史；SQL无旧库操作、DROP/TRUNCATE/ALTER |

测试明细见TEST_RESULTS.md。浏览器专用账号已精确清理。后端8080和Vite5173验收进程均已停止，需要使用时按README启动。没有删除任何已有数据库。

## 当前可运行功能

传统业务：用户注册登录、独立管理员认证、分类菜品/口味/套餐维护、可售菜单分页、按用户隔离的购物车、订单创建与金额快照、重复请求幂等、历史/详情、取消/再次下单、管理员顺序状态流转。

用户端：Vue注册登录、菜单、购物车、订单和AI页面。当前没有默认管理员和默认菜单，因此首次是空页面；需要你自行创建管理员并通过管理API录入菜单。管理端API已有，但没有管理后台UI；用户端口味选择器尚未实现，直接加购默认空口味。无真实支付。

## 数据库变化

创建新库sky_takeout_ai和10张表：user、employee、category、dish、dish_flavor、setmeal、setmeal_dish、shopping_cart、orders、order_detail。SQL脚本为新库专用CREATE IF NOT EXISTS，没有破坏性迁移。多数测试事务回滚；缓存/HTTP/浏览器测试只精确清理自己创建的数据。未设置默认管理员、未灌入展示菜单。

旧库sky_take_out受保护，不读写、不删、不复用。AI会话未持久化建表；RAG仅本地向量存储。

## AI功能状态

| 能力 | 已实现与已测试 | 真实模型状态 |
| --- | --- | --- |
| ChatClient / Prompt | Mock协议、错误隔离、模式标记 | 未验证 |
| Dish / Order / Cart Tools | 真实业务Service、schema、身份隔离、只读边界 | 自动选工具未验证 |
| Memory | 多轮Prompt包含历史；用户隔离、窗口与失败处理 | 多轮语义效果未验证 |
| Structured Output | Java转换、真实ID/状态/金额/预算检查；Mock真实菜单候选 | 模型推荐质量未验证 |
| SSE | Mock模型流 + 实际HTTP + 浏览器显示 | 供应商流未验证 |
| RAG | 五份示例规则、词法向量、Top-K检索与来源测试 | 真实问答与语义Embedding未验证 |
| 管理员分析 | 真实SQL汇总、口径校验、ADMIN权限、Mock解释 | 自然语言分析质量未验证 |

默认AI_MODE=mock。RAG采用2048维本地词法哈希Embedding，是教学基线，不冒充语义模型。所有AI工具只读；下单、加购、取消走普通API。Prompt Injection测试验证应用边界，不是证明模型免疫攻击。

## 遇到的问题及解决

1. Maven缓存位于工作区之外：在已授权范围申请工具运行权限后完成依赖获取与构建，未更改全局Maven配置。
2. 路由角色判断曾依赖servletPath：用MVC解析后的匹配路径判定，回归普通用户访问管理员接口为403。
3. JWT缺角色字段：补空值检查，返回401，不泄露500细节。
4. 异步工具身份：用后端ToolContext显式传递，并finally清理，防线程串用户。
5. 前端退出后私有状态残留：身份变更时清空订单/购物车/地址与聊天组件。
6. 测试可能继承live模式：Surefire统一Mock并置空Key，外部live模式回归仍37项通过。
7. Windows运行JAR导致mvn clean文件锁失败：先停止本轮验收后端，再重新clean package通过。
8. 内置浏览器工具报kernel assets路径错误：改用已安装Playwright与Chrome无头验收，没有安装浏览器或修改用户浏览器设置。
9. Docker Hub网络超时：重试一次仍失败，保留配置并如实标未验收，没有改系统代理/DNS或换未知镜像源。

## 尚未完成

- Docker镜像构建、五服务启动和健康验收，是当前阶段20阻塞项。
- 真实模型API、模型自动选工具和真实流、真实结构化效果评测；本次不使用付费服务。
- 有商品下单的浏览器全流程；用户口味选择与管理员UI。
- 语义Embedding、持久化Chat Memory、评价/支付/优惠券/会员业务、生产TLS与防爆破、模型预算监控、性能压测；它们不是本版已完成能力。
- 可选管理员初始化已编译，但实际创建账号分支未执行，留给你选择账号密码。

## 需要你手动做的事情

1. 如要继续阶段20，先确保Docker Desktop能访问官方Docker Hub的auth.docker.io；不用把密码或API Key发给我。网络恢复后执行README的config/build/up并补健康检查。
2. 要体验本机完整点餐，按README在当前PowerShell隐藏输入数据库密码，运行管理员初始化脚本，使用管理API创建少量菜品并上架。无需改动旧库。
3. 若要验证真实AI，先自行确认供应商和费用，再设置当前进程AI_API_KEY与AI_MODE=live；不要保存到项目文件。

## 明天先学习什么

先运行并理解一次普通下单：CartService → OrderServiceImpl → Mapper → 事务。再解释为什么金额、用户身份与状态不能交给前端或模型。第三步看ToolIdentity和MealPlanService，把这套确定性规则与AI调用边界连起来。INTERVIEW.md有36个题目，但应对照代码练习，不只背答案。

## 项目如何启动

详细可执行命令见README.md“本机启动”。先临时设置Java17与DB_USERNAME/DB_PASSWORD，确保MySQL和Redis运行，RUN_DB_TESTS=true后执行mvn clean package；通过后java -jar sky-server/target/sky-server-0.0.1-SNAPSHOT.jar。另开终端进入sky-web，npm ci、npm run dev，访问http://127.0.0.1:5173。默认Mock可用，不需要AI Key。

构建后的本机JAR在sky-server/target/，前端静态包在sky-web/dist/；两者属于构建产物，未纳入Git。所有真实凭据均未写入本报告、README、源码或Git。
