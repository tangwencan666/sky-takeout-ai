# 本轮验收证据

日期：2026-10-03。这些文件保存测试统计、路径、HTTP状态、视口和结果；真实AI证据另含本次公开演示提示、非敏感回复及订单事实。所有证据均不保存密码、Key、JWT、Authorization或原始上游错误正文。

- `backend-tests.json`：最新Maven Surefire XML提取的逐类统计；83项，0失败/错误/跳过，包含数据库就绪、Provider配置/探针、总截止与取消、结构化核价等回归。
- `api-acceptance-results.json`：独立后端18080的10组实际HTTP检查。
- `browser-acceptance-results.json`：真实Chrome流程、视口、状态与截图名称；`simulatedFaults`专门标出请求拦截模拟，不当作真实后端故障。
- `frontend-summary.json`：本轮已执行的前端测试与构建结果摘要（非原始stdout）；主JS体积警告保留。
- `preview-repair-results.json`：后续预览修复后demo_user真实Chrome登录/刷新/浏览菜单/加购/下单，22个同源API请求全部200，订单ID58、28元；此订单保留供用户核对。
- `ai-status-browser.json`：新后端真实Mock状态与只读刷新；真实模式/缺Key/状态失败的页面展示使用请求拦截，明确列为simulatedUiStates。AI POST为0，不代表DeepSeek已连通。

浏览器截图见`../screenshots/`；schema快照见`../DATABASE_SCHEMA_ACTUAL.md`。临时验收账号已经清理，因此结果中的订单ID代表当时真实成功的验收记录，不承诺交付时仍可查询。完整原始服务日志留在工作目录，不将可能包含环境细节的Surefire XML原样提交。

## 真实DeepSeek联调证据

- legacy-probe-readhost-repro.json：旧脚本停在隐藏Read-Host的本机复现，未访问模型。
- deepseek-network.json / deepseek-direct.json：无Key网络层诊断及一次直接HTTP问候（17 token）。
- probe-script-tests-ps5.json / probe-script-tests-ps7.json：两个PowerShell版本各8项本机故障测试。
- deepseek-probe-initial.json / deepseek-probe-second.json / deepseek-probe-final.json：三个构建实例各自一次最小Probe；最终运行实例以final为准。
- deepseek-browser.json：第一轮真实Chrome问答、343个delta、1个done，未使用响应fixture；数组只留前后三个事件，完整计数另存。
- deepseek-initial-checks.json：Memory两轮通过与首次Structured409原始验收结果。
- deepseek-rag-order.json：真实配送规则回答和当前demo_user订单查询。
- deepseek-structured-duplicate.json：第二轮Structured重复ID被拒绝的证据。
- deepseek-structured-final.json：最终真实套餐，五菜75元，Java与独立查询核价一致。
- deepseek-audit-initial.json / deepseek-audit-second.json / deepseek-audit-final.json：按requestId提取的安全工具/请求/校验审计。首次ADAPTER为当时宽泛分类，后续精确为MEAL_PLAN_DISH_ID_DUPLICATE，最终ACCEPTED。
- deepseek-final-browser-status.json：最终构建后再次用真实Chrome登录并核对已验证状态，AI POST为0；截图10，真实回答截图09属于第一轮实例。

最终结论与费用边界见[REAL_AI_VERIFICATION](../REAL_AI_VERIFICATION.md)。本轮没有再次生成订单，读取并核对的demo_user订单58是前次预览修复保留的真实演示订单。
