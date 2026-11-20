# 银盾智鉴 · P13 安全与隐私审计

> 文档编号：P13-SECURITY-AUDIT
> 日期：2026-11-20
> 方法：`.gitignore` 与 `git check-ignore` 核验、全仓敏感字符串检索、鉴权与日志代码审查、Android 权限清单审查、发布链路复核。
> 结论：**未发现私钥 / Token / 数据库 / 模型产物进入 Git；但管理端默认不鉴权、Worker Token 为开发默认值，不具备生产安全条件。**

## 1. Git 与敏感文件

| 检查项 | 结果 | 证据 |
|---|---|---|
| Git 跟踪文件数 | 0（仓库尚无提交） | `git ls-files` 为空 |
| 私钥 / `.pem` | 已忽略 | `.gitignore:20 *.pem` |
| `.env.local` / `.env.*` | 已忽略（保留 `.env.example`） | `.gitignore:14-16,44` |
| SQLite 数据库 | 已忽略 | `.gitignore:75 services/business-api/data/` |
| 训练产物 / 模型文件 | 已忽略 | `.gitignore:65 ml/training/artifacts/`、`68-72` |
| `.venv` / `node_modules` / `dist` | 已忽略 | `.gitignore:27,40-41,54` |
| 本地开发私钥 | 存在于 `ml/training/keys/dev_ed25519_private.pem`（119 B），已被忽略且未被 Git 跟踪 | `git check-ignore` 命中 `.gitignore:20 *.pem`；`git ls-files` 为空 |
| 受跟踪文件中的私钥文本 | 未发现 | 对非忽略文件检索 `PRIVATE KEY` 无命中（忽略目录未纳入该检索） |

说明：仓库尚无任何提交，因此不存在“历史提交泄露”问题，但也意味着缺少可审计的版本基线，需在交付时建立首次提交。

## 2. Token 与鉴权

### 2.1 Worker Token

- 仅从环境变量 `P11R_WORKER_TOKEN` 读取，不写入代码、不回显响应。
- 使用 `secrets.compare_digest` 常量时间比较；未配置时返回 `500 WORKER_TOKEN_NOT_CONFIGURED`，错误 Token 返回 `401 WORKER_UNAUTHORIZED`。
- 内部接口位于 `/api/v1/internal/worker/*`，与 `/admin/*` 分离。
- **风险（中）**：`.env.example` 默认值为 `dev-worker-token-change-me`，若直接用于非本地环境可被猜测。生产必须替换为随机长 Token。

### 2.2 管理端鉴权

- `require_admin`：`P11R_ADMIN_TOKEN` 为空时**直接放行**，这适用于本地开发，但不适用于任何共享环境。
- **风险（高，生产阻断项）**：当前 `.env.local` 未设置管理 Token，管理端接口实际无身份认证；前端“系统管理员”为模拟角色，不构成真实 RBAC。
- 管理接口的“角色”通过请求体 `roleLabel` 传入并由服务端比对 `P11R_MODEL_RELEASE_ROLES`，可被伪造，不能替代真实身份体系。

## 3. 日志与错误处理

- 请求日志只记录 `method / path / status / elapsed / traceId`，**不记录 Authorization、Worker Token、请求体**（`app/core/logging.py`）。
- 统一错误响应 `{error:{code,message,traceId}}`，未处理异常由全局 handler 捕获，不返回堆栈。
- 训练日志由 Worker 上报，未发现 Token 或私钥写入日志的路径。

## 4. 屏幕采集与上传

| 检查项 | 结论 |
|---|---|
| INTERNET 权限 | **未声明**，当前 APK 不具备网络上传能力 |
| MediaProjection | 通过系统授权对话框获取，用户可拒绝 |
| 前台服务 | `ScreenCaptureService` 声明 `foregroundServiceType=mediaProjection`，`stopWithTask=true` |
| 停止采集 | 通知栏停止按钮 + 页面停止入口，释放 ImageReader / VirtualDisplay / MediaProjection |
| 系统撤销 | 通过 `MediaProjection.Callback.onStop` 处理并停止服务 |
| 原始录像 | 不落盘、不上传；帧仅在内存中处理并 `close()` |
| 家属实时屏幕 | 无任何入口 |

**边界**：P05 仅完成采集 PoC，没有 OCR / AI 推理；不得描述为“已实现屏幕风险识别”。

## 5. 家庭数据边界

- 家庭、共享、处理、消息均为 Android 本地 Fake 仓库，数据不出设备。
- 家属端只能看到已共享到本地仓库的事件，无法访问老人端原始记录或实时画面。
- 撤销共享只修改本地状态；没有真实 ConsentRecord 服务，跨设备撤销未验证。
- **风险（中）**：一旦接入真实后端，现有前端授权判断必须由服务端重新校验，不能沿用本地 Mock 逻辑。

## 6. 模型发布安全

- 发布 / 审核 / 回滚均在服务端重新计算 11 项清单，前端 `eligible` 不参与放行。
- 文件哈希服务端重算；签名用 Ed25519 公钥验证；开发密钥标记 `development`。
- 未签名、开发密钥、Smoke Test、合成数据、无真实测试报告、无真实设备测试均阻断灰度 / 正式渠道。
- 审计记录只追加，无删除接口。
- **风险（中）**：当前只有开发密钥，无私钥轮换、吊销与生产签名流程；模型文件完整性依赖本地磁盘。

## 7. 其他发现

| 级别 | 问题 | 建议 |
|---|---|---|
| 高 | `P11R_ADMIN_TOKEN` 为空时管理接口无鉴权 | 共享 / 生产环境必须配置 Token 或接入真实身份认证 |
| 中 | Worker Token 使用示例默认值 | 部署时生成随机 Token，写入密钥管理而非仓库 |
| 中 | `roleLabel` 由客户端传入 | 接入真实会话后从服务端身份派生 |
| 中 | 无数据库迁移与备份 | 引入 Alembic、定期备份与恢复演练 |
| 低 | 开发代理暴露 `/api/v1/internal/*` 路径（仍受 Token 保护） | 生产网关仅放行 `/admin/*` 与 `/health` |
| 低 | 训练依赖合成数据与开发签名 | 正式训练前接入真实许可数据与生产密钥 |

## 8. 审计结论

- 私钥、Token、数据库、模型产物、`.env.local` **均未被 Git 跟踪**。
- 端侧当前无网络权限，不存在未经授权的屏幕上传路径。
- 管理端无真实认证、Worker Token 为开发默认值，属于**生产上线阻断项**，但不影响本地开发验收。
- 本轮未执行任何数据删除、仓库清理或密钥轮换。
