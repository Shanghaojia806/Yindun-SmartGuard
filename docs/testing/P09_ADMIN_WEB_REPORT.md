# P09 · 管理后台工作台与通用数据管理 · 交付报告

> 项目：银盾智鉴
> 工程：`apps/admin-web`（独立 Vue 3 Web 工程，不属于 Android App）
> 阶段：P09
> 日期：2026-11-19
> 状态：**已完成，待验收**

---

## 1. 新增文件清单

| 文件 | 说明 |
|---|---|
| `apps/admin-web/src/api/models.ts` | 管理端业务模型（用户、设备、工作台、系统管理） |
| `apps/admin-web/src/api/request.ts` | Mock 开关、统一 Mock 调用、延迟与单次失败模拟 |
| `apps/admin-web/src/api/mock/data.ts` | 演示数据（26 用户、16 设备、6 管理员、4 角色、24 审计、6 配置等） |
| `apps/admin-web/src/api/mock/adapter.ts` | Mock 查询 / 分页 / 详情 / 状态变更 |
| `apps/admin-web/src/api/dashboard.ts` | A01 工作台数据服务 |
| `apps/admin-web/src/api/user.ts` | A02 用户管理数据服务 |
| `apps/admin-web/src/api/device.ts` | A03 设备管理数据服务 |
| `apps/admin-web/src/api/system.ts` | A10 系统管理数据服务 |
| `apps/admin-web/src/components/DataTable.vue` | 统一数据表格（Loading / Error / Empty / 分页插槽） |
| `apps/admin-web/src/components/Pagination.vue` | 统一分页组件 |
| `apps/admin-web/src/components/StatusTag.vue` | 统一状态标签（状态枚举 → 文案 / 颜色） |
| `apps/admin-web/src/components/StatCard.vue` | 统计卡片（默认带"演示"标识） |
| `apps/admin-web/src/components/DetailDrawer.vue` | 统一详情抽屉 |
| `docs/testing/P09_ADMIN_WEB_REPORT.md` | 本报告 |

## 2. 修改文件清单

| 文件 | 修改内容 |
|---|---|
| `apps/admin-web/src/api/index.ts` | 导出 Mock 开关、Service 函数与请求层 |
| `apps/admin-web/src/components/index.ts` | 增加 DataTable / Pagination / StatusTag / StatCard / DetailDrawer 出口 |
| `apps/admin-web/src/components/ModulePlaceholder.vue` | 占位文案改为 P09 口径（A04—A09 仅保留入口） |
| `apps/admin-web/src/layouts/AdminLayout.vue` | 新增消息入口占位、演示控制（模拟失败）、更新页脚为 P09 |
| `apps/admin-web/src/views/admin/WorkbenchView.vue` | A01 工作台完整实现（原占位页） |
| `apps/admin-web/src/views/admin/UserManagementView.vue` | A02 用户管理完整实现（原占位页） |
| `apps/admin-web/src/views/admin/DeviceManagementView.vue` | A03 设备管理完整实现（原占位页） |
| `apps/admin-web/src/views/admin/SystemManagementView.vue` | A10 系统管理完整实现（原占位页） |
| `apps/admin-web/src/views/LoginView.vue` | 文案由 P03 更新为 P09（仍为本地模拟登录） |
| `apps/admin-web/src/styles/index.css` | 增加 `.ss-field`、`.ss-section` 等通用布局工具类 |
| `apps/admin-web/src/vite-env.d.ts` | 增加 `VITE_USE_MOCK`、`VITE_MOCK_LATENCY_MS` 类型 |
| `apps/admin-web/.env.example` | 增加 Mock 开关与模拟延迟说明 |

未修改：`package.json`、`vite.config.ts`、`tsconfig.json`、路由与 10 个菜单配置、Android 工程。

## 3. 管理后台整体架构

> 执行前检查：仓库当前尚无提交（`git status` 显示全部文件未跟踪），P09 在未提交的 P00—P08 工作区上增量开发。

```
页面视图（src/views/admin/*）
   ↓ 只调用 Service，不拼接请求、不硬编码大段业务数据
API Service（src/api/dashboard.ts · user.ts · device.ts · system.ts）
   ↓ 依据 IS_MOCK 二选一
Mock Adapter（src/api/mock/adapter.ts → data.ts）   或   真实请求（src/api/http.ts）
   ↓
统一 Mock 调用（src/api/request.ts：延迟、失败模拟、演示数据日志）
```

- 布局沿用 P03：左侧 10 个一级菜单（分组 + 折叠）、顶部面包屑 / 消息入口 / 管理员信息 / 退出登录、内容区 `max-width: 1440px`。
- 设计系统沿用 P02 tokens（`--ss-*`），未新增品牌色，未使用渐变、玻璃拟态或夸张阴影。
- 通用组件统一从 `@/components` 导入；状态文案集中在 `StatusTag.vue`，分页集中在 `Pagination.vue`。

## 4. A01 工作台完成情况

- 欢迎区：产品名、当前管理员（模拟会话）、当前日期、系统运行说明与"演示数据模式 / 本地模拟登录"标签。
- 6 张统计卡：注册用户总数 26、老人 18、家属 8、已绑定家庭关系 6、设备总数 16、异常设备 5；每张均有"演示"角标。
- 待办任务：待审核数据集 3、待处理用户反馈 5、待审核模型版本 2、待处理设备异常 5（由演示设备实时计算），标注演示并可跳转对应模块。
- 系统概况：模块接入状态（真实接口 / 训练执行器标为"未接入"）、最近操作记录（演示）、最近任务占位、版本与数据来源说明。
- 统计数字由 Mock 用户 / 设备数组实时计算，保证页面内自洽。

## 5. A02 用户管理完成情况

- 列表字段：用户 ID、名称、角色、脱敏联系方式、账户状态、注册时间、家庭绑定、最近活跃、操作。
- 搜索筛选：名称关键字、用户 ID、角色、账户状态、家庭绑定；支持回车查询与重置。
- 详情抽屉：基础信息、角色与家庭关系、操作记录（占位）。
- 操作：查看详情；启用 / 禁用二次确认（仅修改内存 Mock 数据）；注销为二次确认占位（不删除数据）。
- 页面显著标注"演示数据（Mock）"，联系方式为脱敏格式。

## 6. A03 设备管理完成情况

- 列表字段：设备 ID、关联用户、品牌、型号、系统（Android/HarmonyOS）、系统版本、App 版本、设备状态、最近活跃、异常信息。
- 筛选：设备 ID、型号、品牌、系统版本、App 版本、状态（品牌 / 版本下拉选项由 Mock 服务下发）。
- 详情抽屉：基本设备信息、关联账户、版本与兼容性、异常记录；并明确提示"本轮不提供远程控制能力"。
- 无远程控制、无远程屏幕采集入口；兼容性仅展示演示状态。

## 7. A10 系统管理完成情况

- 管理员管理：6 条演示管理员数据（名称、角色、状态、最近登录时间）。
- 角色权限：超级管理员、普通管理员、数据审核人员、模型管理人员 4 个角色及权限对照，明确标注"未启用真实 RBAC"。
- 操作审计：关键字筛选 + 分页（24 条演示日志）。
- 系统配置：6 项配置占位，修改按钮禁用并说明"P10+ 开放"。
- 页面顶部提示：不启用真实权限、不修改系统配置、不执行备份。

## 8. A04—A09 占位路由检查

| 编号 | 路径 | 结果 |
|---|---|---|
| A04 数据集管理 | `/admin/datasets` | 可访问，显示占位提示与计划能力 |
| A05 训练管理 | `/admin/training` | 可访问 |
| A06 模型版本 | `/admin/models` | 可访问 |
| A07 规则管理 | `/admin/rules` | 可访问 |
| A08 知识库 | `/admin/knowledge` | 可访问 |
| A09 风险反馈 | `/admin/risk-feedback` | 可访问 |

六个页面均保留菜单入口与独立路由，未提前实现业务功能，不发起接口请求。

## 9. 通用组件清单

PageHeader、FilterPanel、DataTable、DataTableEmpty、LoadingState、ErrorState、ConfirmDialog、DetailDrawer、StatusTag、Pagination、StatCard（及既有 ModulePlaceholder）。

## 10. Mock 与真实 API 的区分方式

1. `src/api/request.ts` 的 `IS_MOCK` 由 `VITE_USE_MOCK` 控制（默认 `true`）。
2. 每个 Service 内部只有一个分支：Mock 走 `mockCall`，真实走 `http.get/post/patch`，页面无需改动。
3. `mockCall` 统一输出 `[api] 演示数据（Mock）` 日志并返回克隆数据，避免页面直接修改 Mock 状态。
4. UI 层固定标注：工作台"演示数据模式"、统计卡"演示"角标、列表页"演示数据（Mock）"标签、系统配置"演示"标签。
5. 顶部"演示控制"可模拟下一次接口失败，仅影响 Mock 请求，便于验证 Error 状态。

## 11. 搜索、筛选、分页测试结果

在真实浏览器中实测：

- 用户名称搜索"周" → 1 条（周桂英 U-2026-0001），重置恢复 26 条。
- 搜索"不存在" → 空状态文案正常展示。
- 用户分页：共 26 条 / 3 页，点击第 2 页显示 U-2026-0011—U-2026-0020。
- 设备状态筛选"异常" → 5 条（DEV-2026-0003/0005/0008/0011/0015）。
- 操作审计搜索"张明远" → 24 条过滤为 5 条。

结论：搜索、筛选、分页均正常。

## 12. TypeScript 类型检查结果

执行 `npm run typecheck`（`vue-tsc --noEmit`）：**通过，0 error**。

## 13. Web 生产构建结果

执行 `npm run build`：**成功**，产物输出至 `apps/admin-web/dist/`。主包 `index-BlFMea97.js` 约 1,062 kB，样式约 363 kB；各业务页面已按路由拆分（Workbench / User / Device / System 独立 chunk）。

## 14. 本地运行和页面测试结果

启动 `npm run dev` 后使用浏览器实测：

| 测试项 | 结果 |
|---|---|
| 登录占位页 | 通过（显示模拟登录警告与开发入口） |
| A01 工作台 | 通过（欢迎区、6 卡、待办、模块状态、版本） |
| A02 用户管理 | 通过（列表 26 条、搜索、空态、详情抽屉、禁用确认取消） |
| A03 设备管理 | 通过（16 条、异常筛选 5 条、详情与异常记录） |
| A04—A09 占位页 | 通过（以 A04 抽查，其余路由同构） |
| A10 系统管理 | 通过（4 个 Tab、4 角色、审计搜索、配置按钮禁用） |
| 左侧导航跳转 | 通过（菜单切换、面包屑同步） |
| 详情抽屉 | 通过（用户 / 设备） |
| 二次确认弹窗 | 通过（禁用确认，取消不改变数据） |
| Loading 状态 | 通过（2.5s 模拟延迟下捕获"正在加载工作台演示数据"） |
| Empty 状态 | 通过（搜索无结果） |
| Error 状态 | 通过（模拟 503，显示"已模拟一次接口失败"，重试恢复） |
| 刷新与路由恢复 | 通过（刷新后仍为 `/admin/users` 且数据正常） |
| 桌面布局 | 约 1280×720 视口下侧栏、表格、卡片布局正常；已实现 1180/900/720 响应式断点 |

## 15. 尚未接入的真实后端功能

- 真实登录认证与会话凭证（仍为本地 sessionStorage 模拟）。
- 真实 RBAC 权限校验与角色下发。
- FastAPI 用户 / 设备 / 工作台 / 审计接口。
- 用户注销、设备解绑等写操作的服务端流程。
- 数据集上传、训练执行器、模型发布 / 回滚、规则与知识库业务。
- 设备远程控制（按需求永久禁止管理端远程开启采集）。
- 系统配置写入、数据备份与恢复。

## 16. P09 验收结论

P09 范围内的工作台、用户管理、设备管理、系统管理已完成前端实现，A04—A09 保留可访问占位页；通用组件与 API/Mock 分层落地，类型检查与生产构建通过，主要交互与四态在浏览器中实测通过。

**结论：P09 达到本轮验收标准。** 所有业务数据均为明确标注的演示数据，未接入真实后端，未修改 Android 工程，未进入 P10。
