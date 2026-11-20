# 银盾智鉴 · P12 真实训练产物接入验证报告

> 文档编号：P12-REAL-ARTIFACT-REPORT
> 版本：v1.0
> 日期：2026-11-20
> 范围：P11-R 真实 Smoke Test 产物 → 模型仓库导入 → 哈希 / 签名校验 → 审核与发布拦截
> 结论：**真实产物已接入；Smoke Test 产物仅能进入受限内部测试，灰度与正式发布被服务端阻断。**

---

## 0. 一句话结论

本轮使用 P11-R 真实生成的 `T-2026-001`（未签名）与 `T-2026-002`（development 密钥签名）
完成模型导入、服务端 SHA-256 重算、Ed25519 公钥验签、三渠道审核与发布拦截测试。
两个版本在 `internal_test` 渠道可完成提交 / 审核 / 发布；在 `canary`、`production`
渠道均被 `422 MODEL_REVIEW_BLOCKED` 拦截。**未将任何 Smoke Test 产物标记为正式反诈模型。**

## 1. 验证对象

| 训练任务 | 产物清单 | 文件 | 大小 | SHA-256 | 签名状态 |
|---|---|---|---|---|---|
| T-2026-001 | `ml/training/artifacts/T-2026-001/artifact-manifest.json` | `model.pt` | 4957 B | `7627ff557506a26ea1e9a35335cc3d20845110e23d0e442de56af6efc90e0ce7` | 未签名（`missing`） |
| T-2026-002 | `ml/training/artifacts/T-2026-002/artifact-manifest.json` | `model.pt` | 4957 B | `da09c4523fe0541b8294ac2ef50dda74bb66ed7c1561c50ea3658543ea884a9e` | Ed25519 有效，`keyLabel=development` |

两条任务的 `training_profile = smoke_test`，数据集快照
`VER-DS-2026-004-02` 为合成演示来源（`snapshot_source = synthetic_demo`），
评估状态 `not_evaluated`，均不具备正式反诈模型资格。

## 2. 隔离验收方式

为避免污染开发库，验收脚本复制 `services/business-api/data/p11r.db`（含 WAL）到临时目录，
仅清空 7 张模型仓库表，保留真实训练任务与数据集快照，再通过 FastAPI `TestClient`
调用真实接口。脚本位于临时目录，未写入仓库。

## 3. 导入结果

```text
T-2026-001 200 {"modelId":"M-2026-001","modelVersionId":"MV-2026-001-01",
                "modelVersion":"v0.1.0-smoke","trainingTaskId":"T-2026-001",
                "releaseStatus":"draft","artifactId":"ART-T-2026-001-95a1cf29"}
T-2026-002 200 {"modelId":"M-2026-002","modelVersionId":"MV-2026-002-01",
                "modelVersion":"v0.1.0-smoke","trainingTaskId":"T-2026-002",
                "releaseStatus":"draft","artifactId":"ART-T-2026-002-1e22a672"}
```

服务端重新校验后的产物记录：

| artifactId | fileHash | 哈希状态 | 签名状态 | keyLabel | 开发密钥 |
|---|---|---|---|---|---|
| ART-T-2026-001-95a1cf29 | `7627ff...0ce7` | `verified` | `not_verified` | `unconfigured` | false |
| ART-T-2026-002-1e22a672 | `da09c4...4a9e` | `verified` | `verified` | `development` | **true** |

哈希与 P11-R 清单一致；签名结论由公钥验签得出，未采信前端或清单自述。

## 4. 三渠道审核结果

| 版本 | internal_test | canary | production |
|---|---|---|---|
| MV-2026-001-01 | `eligible = true` | `false` | `false` |
| MV-2026-002-01 | `eligible = true` | `false` | `false` |

正式渠道阻断原因（两个版本结构一致，002 额外指出开发密钥）：

1. `关联训练任务成功`：`trainingProfile=smoke_test`，仅允许受限内部测试；
2. `数据集通过必要审核`：训练数据为合成 / 演示来源，禁止正式反诈发布；
3. `数字签名验证`：001 未签名；002 使用开发密钥 `development`；
4. `模型评估完成`：指标来自 Smoke Test 合成数据；
5. `测试集评估有效`：缺少真实测试集评估报告；
6. `目标运行时兼容性`：没有真实设备测试记录。

内部测试渠道中，哈希与签名检查分别为通过 / 未验证，评估、测试集、兼容性标记未验证但不阻断，
符合「受限内部测试」定位。

## 5. 内部测试发布与撤回

```text
submit  200 pending_review
approve 200 approved
publish 200 published
release RL-63f8631c4953  internal_test  approved   「提交受限内部测试审核」
release RL-62af8ee6f2a6  internal_test  published  「[受限内部测试] 发布（Smoke Test 产物，禁止正式反诈发布）」
withdraw 200 withdrawn
audits: withdraw, publish, approve, submit_review, create_version（均 success）
```

撤回后原发布记录保留并标记 `withdrawn`，审计记录完整、只追加。

## 6. 服务端拦截证据

```text
POST /model-versions/MV-2026-001-01/approve  channel=canary
→ 422 {"error":{"code":"MODEL_REVIEW_BLOCKED","message":"审核清单存在阻断项：…
   trainingProfile=smoke_test…；训练数据为合成/演示来源…；模型未签名…"}}

POST /model-versions/MV-2026-001-01/approve  channel=production
→ 422 {"error":{"code":"MODEL_REVIEW_BLOCKED","message":"审核清单存在阻断项：…"}}
```

提交审核本身允许进入 `pending_review`，但审核通过与发布必须重新通过服务端清单；
前端无法绕过。

## 7. 独立清单校验

```text
$ .venv\Scripts\python.exe -m ml.training.verify_manifest ml\training\artifacts\T-2026-001\artifact-manifest.json
PASS：文件大小一致（4957 字节）
PASS：SHA-256 一致（7627ff55…0ce7）
INFO：signatureStatus=missing，未签名产物不得进入正式发布流程
INFO：该产物为 CPU Smoke Test 结果，不是反诈模型，禁止正式发布。

$ .venv\Scripts\python.exe -m ml.training.verify_manifest ml\training\artifacts\T-2026-002\artifact-manifest.json
PASS：文件大小一致（4957 字节）
PASS：SHA-256 一致（da09c452…4a9e）
PASS：Ed25519 签名有效（keyLabel=development）
WARN：使用开发密钥签名，不能作为生产发布依据
INFO：该产物为 CPU Smoke Test 结果，不是反诈模型，禁止正式发布。
```

## 8. 自动化与构建

| 命令 | 结果 |
|---|---|
| `python -m pytest -q`（services/business-api） | **18 passed**，1 warning（httpx 弃用提示） |
| `npm run typecheck`（apps/admin-web） | 通过，0 错误 |
| `npm run build` | 通过，2333 modules，built in 23.23s |

后端测试覆盖：真实产物导入与哈希、未签名仅内部、正式渠道 422、发布权限 403、
文件篡改阻断、撤回与回滚审计、重复导入幂等。

## 9. 页面验证

真实模式（`VITE_USE_MOCK=false`）下已验证：

- 模型仓库列出 `M-2026-001`、`M-2026-002`，来源显示「训练任务 T-2026-00x（真实 Worker · smoke_test）」；
- 导入弹窗仅列出成功且 Worker 执行的训练任务，并提示 Smoke Test 限制；
- 版本详情显示服务端重算的哈希与签名状态，`development` 密钥明确标记；
- 审核页展示 11 项清单：文件完整性通过、Ed25519 development 通过（内部测试）、
  测试集评估与运行时兼容性「未验证」；
- 正式发布清单为阻断状态，内部测试清单可提交。

## 10. 限制与未完成项

- 没有真实反诈模型、真实测试集评估报告、真实设备兼容性记录与生产签名密钥，
  因此不存在可进入灰度 / 正式的版本，本轮**未做也不能做**正式发布。
- 版本详情的数据集卡片仍从 P10 Mock 数据读取，可能显示「数据集版本记录不可用」；
  发布资格判断由服务端基于 `dataset_version_snapshots` 完成，不受该展示影响。
- 前端角色仍为单一模拟管理员；权限不足（403）由后端测试覆盖，未做多角色 UI 实测。
- `T-2026-004` 同样为真实 Smoke Test 产物，可复用同一导入流程，本轮未重复导入。

## 11. 边界声明

- 未将模型文件、私钥、SQLite 或 artifacts 纳入 Git：
  `.gitignore` 已忽略 `ml/training/artifacts/`、`*.pem`、`services/business-api/data/`。
- 未修改 Android 工程，未进行任何真实设备分发或远程卸载。
- Smoke Test 产物只作为端到端链路验证材料，不作为反诈模型成果对外描述。
