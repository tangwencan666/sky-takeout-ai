# 本轮验收证据

日期：2026-10-03。这些文件仅保存测试名称、统计、路径、HTTP状态、视口和结果，不保存密码、JWT、请求头或请求正文。

- `backend-tests.json`：最新Maven Surefire XML提取的逐类统计；69项，0失败/错误/跳过，包含4项数据库就绪测试与7项Provider配置/探针测试。
- `api-acceptance-results.json`：独立后端18080的10组实际HTTP检查。
- `browser-acceptance-results.json`：真实Chrome流程、视口、状态与截图名称；`simulatedFaults`专门标出请求拦截模拟，不当作真实后端故障。
- `frontend-summary.json`：本轮已执行的前端测试与构建结果摘要（非原始stdout）；主JS体积警告保留。
- `preview-repair-results.json`：后续预览修复后demo_user真实Chrome登录/刷新/浏览菜单/加购/下单，22个同源API请求全部200，订单ID58、28元；此订单保留供用户核对。
- `ai-status-browser.json`：新后端真实Mock状态与只读刷新；真实模式/缺Key/状态失败的页面展示使用请求拦截，明确列为simulatedUiStates。AI POST为0，不代表DeepSeek已连通。

浏览器截图见`../screenshots/`；schema快照见`../DATABASE_SCHEMA_ACTUAL.md`。临时验收账号已经清理，因此结果中的订单ID代表当时真实成功的验收记录，不承诺交付时仍可查询。完整原始服务日志留在工作目录，不将可能包含环境细节的Surefire XML原样提交。
