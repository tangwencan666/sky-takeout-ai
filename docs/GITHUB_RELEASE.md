# GitHub 发布前检查

日期：2026-10-04（Asia/Shanghai）。本文件记录本地发布准备；远程创建和push以实际GitHub验证结果为准，不将本地commit当成上传成功。

## 安全审计

- 初次审计：master分支，26个提交、387个历史blob，无remote。检查工作区、跟踪文件、所有本地引用及reflog可达历史；没有重写正常历史。
- 实际授权DeepSeek Key与当前基础设施凭据只在内存中用于精确匹配；未打印或保存其值。另扫描Key、JWT、GitHub Token、Authorization及私钥格式。
- 未发现真实凭据、旧项目.env内容或禁止发布的运行产物。两处赋值审查命中属于同一负例登录测试（当前文件及历史版本），用于验证错误密码401，并非真实基础设施凭据。
- `.env`及变体、`.local/`、target、node_modules、dist、IDE配置、日志、PID、临时文件、application-local与application-secrets配置均忽略；`.env.example`保留空值/占位符。忽略规则只预防后续误加，历史另行扫描。
- 运行日志和Surefire报告另行检查，原始文件不提交。没有单文件超过10MiB的发布候选。
- 数据库名仍固定sky_takeout_ai；不读取、修改或删除旧sky_take_out。未修改Sentinel项目或旧.env。

## 公开启动方式

Start-DeepSeek优先使用AI_API_KEY环境变量；LegacyEnvPath是可选参数，也可用SKY_LEGACY_ENV_PATH。JavaHome优先参数/JAVA_HOME，不再硬编码开发者的JDK路径。

原开发机的旧配置路径和JDK路径迁入Git与Docker同时忽略的`.local/startup.json`，只包含路径、不包含凭据。由此保留原本一键启动能力，其他使用者无需旧项目。普通启动和测试不触发付费Probe；显式真实验收保持独立。

README补齐项目简介、功能、架构、目录、启动、环境变量、数据库、AI能力、Redis、安全、Docker、测试、截图与亮点。公开demo_user/demo_admin口令明确标记为“仅用于本地开发演示”，不能用于生产。

## 上传前复测

| 检查 | 结果 |
| --- | --- |
| mvn -B -ntp test，RUN_DB_TESTS=true | 83项，0失败/错误/跳过，4个模块SUCCESS；真实MySQL与Redis，模型强制Mock |
| npm test | 7项通过，0失败/跳过 |
| npm run build | 通过，保留约1.07MB主JS与大于500kB体积提示 |
| 启动脚本优先级与环境恢复 | PowerShell 5.1/7各4项，通过；合成Key、本机替身、真实Provider请求0次、真实服务变更0次 |
| Docker Compose config --quiet | 通过；仅使用临时合成凭据，没有输出展开配置 |
| Docker HealthProbe | JDK17、UTF-8显式编码编译通过 |
| Docker镜像构建及整套部署 | 本次未执行；先前镜像拉取受网络阻塞，不能宣称部署已验证 |

首次后端复测有1项缓存失败，查明6379无Redis监听；启动已有Redis（仅127.0.0.1，关闭持久化）后全量重跑83项通过，没有删测试、跳过断言或改为Mock缓存。首次直接javac采用Windows默认GBK导致中文注释编码错误，显式UTF-8后通过；Dockerfile也显式指定UTF-8。

真实AI证据保留在[REAL_AI_VERIFICATION](REAL_AI_VERIFICATION.md)，本次发布准备不重复发送收费请求。机器可读的发布前检查见[github-release-preflight.json](verification/github-release-preflight.json)。

## 提交范围

保留Maven三模块、Vue前端、SQL与演示seed、knowledge、脚本、文档和非敏感验收截图。将已审查的Dockerfile、Compose、Nginx与HealthProbe配置纳入版本控制，默认模型与当前应用对齐；不把仅语法/编译通过写成部署成功。

上传目标为GitHub的sky-takeout-ai公开仓库，分支main。没有remote时先完成GitHub本人浏览器授权，再检查是否已有同名仓库；不强推，不reset --hard，不发送登录凭据到聊天。
