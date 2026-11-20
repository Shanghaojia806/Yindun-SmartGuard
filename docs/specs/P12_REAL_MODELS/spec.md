# P12 补充要求 · 真实训练产物接入模型仓库（Spec）

> 状态：待确认（Spec Mode）
> 日期：2026-11-20
> 关联契约：`docs/api/P12_MODEL_CONTRACT.md`、`docs/api/P11_TRAINING_CONTRACT.md`
> 前置成果：P11-R 已完成 FastAPI + SQLite + 独立 PyTorch Worker 真实 Smoke Test 链路

---

## 1. 目标

将 P11-R 真实产出的 Smoke Test 模型导入 P12 模型仓库，并由 FastAPI 服务端完成：

1. 模型版本关联真实 `TrainingTask`；
2. 模型文件从后端产物清单（`artifact-manifest.json`）读取；
3. 服务端重新计算真实文件 SHA-256 并与清单比对；
4. 使用 Ed25519 公钥重新验签（不使用前端结论）；
5. 开发密钥签名只能标记 `development`；
6. 未签名模型禁止灰度 / 正式发布；
7. `trainingProfile=smoke_test` 仅允许受限内部测试流程；
8. 合成数据产物（`datasetType=synthetic` 或快照 `snapshot_source=synthetic_demo`）禁止进入正式反诈发布；
9. 无真实测试集评估报告时阻止灰度 / 正式发布；
10. 发布前重新检查 P10 数据集资格、人工复核、来源许可；
11. 模型兼容性需真实设备测试记录才能进入灰度 / 正式；
12. 撤回 / 回滚保留完整审计记录。

同时保持 Mock 模式可切换，Mock 记录（`isMock=true` / `artifactSource=demo_record`）与真实记录（`isMock=false` / `artifactSource=training_worker`）明确区分。

**本轮不开发真实反诈模型，不修改 Android 工程，不进入 P13。**

---

## 2. 现状与可复用资产

- 后端：`services/business-api`，已有 `training_tasks`、`dataset_version_snapshots`、`training_results`、`worker_events` 等表；
- 产物：`ml/training/artifacts/{taskId}/model.pt` + `artifact-manifest.json`（T-2026-001 未签名、T-2026-002 development 签名）；
- 验签参考实现：`ml/training/verify_manifest.py`（payload = 去掉 `signature/signatureStatus` 后的 `json.dumps(sort_keys=True,separators=(",",":"),ensure_ascii=False)` UTF-8）；
- 前端契约与页面：`src/api/model.ts`、`src/types/model.ts`、`src/views/model/*` 已按 P12 契约实现，但后端接口尚不存在；
- 数据集快照 `VER-DS-2026-004-02`：`dataset_type=multimodal`、`snapshot_source=synthetic_demo`、license/quality/duplicate 合格、`version_status=published`。

---

## 3. 数据模型（新增表，`services/business-api/app/models/model_repo.py`）

| 表 | 关键字段 |
|---|---|
| `models` | model_id(PK)、model_name、model_type、framework、description、model_status、training_task_id、created_at、created_by |
| `model_versions` | model_version_id(PK)、model_id、model_version、dataset_*、training_task_id、artifact_id、model_format、training_profile、dataset_type、dataset_origin、eval_*、release_status、production_ready、publish_blocked_reason、created/reviewed/published/withdrawn 时间与人员 |
| `model_artifacts` | artifact_id(PK)、model_id、model_version_id、artifact_path、artifact_format、file_size_bytes、file_hash、file_hash_status、signature、signature_status、signature_key_label、signature_is_development、manifest_path、artifact_source、training_task_id、created_at/by、last_verified_at |
| `model_release_records` | release_id(PK)、model_id、model_version_id、release_channel、release_status、operator、reviewed_by、submitted/approved/published/withdrawn 时间、rollback_from/to、operation_notes |
| `model_audit_logs` | audit_id(PK)、model_id、model_version_id、operator、operation、operation_label、target_type、target_id、target_label、operated_at、result、notes（只追加） |
| `model_compatibility_tests` | compatibility_id(PK)、model_version_id、platform、os 范围、architecture、inference_runtime、runtime_version、devices_json、memory_mb、accelerators_json、compatibility_status、tested_at/by、notes、is_real_device_test |
| `model_evaluation_reports` | report_id(PK)、model_version_id、dataset_version_id、dataset_type、evaluation_status、metrics_json、evaluated_at/by、source、is_real_test_set |

`init_db()` 通过 `app/models/__init__.py` 导入新模块自动建表；不修改既有 P11-R 表。

---

## 4. 后端实现文件与函数

### 4.1 `app/services/artifact_verify.py`

- `sha256_file(path: Path) -> str`
- `verify_manifest(manifest: dict) -> dict`：返回 `{fileHashStatus, fileSize, manifestSha256, actualSha256, signatureStatus, keyLabel, isDevelopmentKey, detail}`
- `verify_signature(manifest: dict, repo_root: Path) -> {...}`：按 `signature.publicKeyPath` 或配置 `P11R_ED25519_PUBLIC_KEY_PATH` 加载公钥，`public_key.verify()`；失败返回 `failed`，未签名返回 `not_verified`，绝不伪造通过。

### 4.2 `app/services/model_repository.py`

- `import_from_training_task(session, payload, operator) -> dict`
  1. 任务存在且 `status=SUCCEEDED`、`execution_mode=worker`，否则 `409 MODEL_IMPORT_INVALID`；
  2. `training_results.manifest_path` 必须存在，解析仓库根相对路径并读取清单；
  3. 校验清单 `taskId` 与任务一致；
  4. 解析 `artifactPath`，文件必须真实存在；
  5. 重新计算 SHA-256 与大小，写入 `file_hash_status=verified/failed`；
  6. 公钥验签，写入 `signature_status=verified/failed/not_verified`、`signature_key_label`、`signature_is_development`；
  7. 生成模型与版本（`v0.x.0-smoke`，`release_status=draft`），数据集类型/来源取自快照；
  8. `training_profile=smoke_test` 或 `dataset_origin=synthetic` → `production_ready=false`；
  9. 写 `create_version` 审计；哈希/签名失败额外写 `hash_verify_failed` / `signature_verify_failed` 审计（result=blocked/failed）；
  10. 同一任务重复导入返回已有版本（幂等，不重复建记录）。
- `evaluate_review(session, version, channel, role_label) -> ModelReviewResult`：11 项检查（见 §5）。
- `submit_review / approve_review / reject_review / publish / withdraw / rollback`：状态机 + 服务端重算审核清单 + 追加 `model_release_records` 与 `model_audit_logs`。
- `stats / options / list_models / get_model / list_versions / get_version / artifacts / releases / audits / compare`.
- 序列化函数输出与 `src/types/model.ts` 完全一致（camelCase；时间使用 `display_time`）。

### 4.3 `app/api/admin_models.py`（挂在 `/api/v1/admin`）

| 方法 | 路径 |
|---|---|
| GET | `/admin/models/stats` |
| GET | `/admin/models/options` |
| GET | `/admin/models` |
| GET | `/admin/models/{model_id}` |
| GET | `/admin/models/{model_id}/versions` |
| GET | `/admin/models/{model_id}/versions/{version_id}` |
| GET | `/admin/model-artifacts/{artifact_id}` |
| GET | `/admin/model-artifacts?modelId=` |
| GET | `/admin/model-versions/{version_id}/review-check?channel=&roleLabel=` |
| POST | `/admin/model-versions/import` |
| POST | `/admin/model-versions/{version_id}/submit-review` |
| POST | `/admin/model-versions/{version_id}/approve` |
| POST | `/admin/model-versions/{version_id}/reject` |
| POST | `/admin/model-versions/{version_id}/publish` |
| POST | `/admin/model-versions/{version_id}/withdraw` |
| POST | `/admin/models/{model_id}/rollback` |
| POST | `/admin/model-versions/{version_id}/compatibility-tests` |
| GET | `/admin/models/{model_id}/releases` |
| GET | `/admin/model-versions/{version_id}/releases` |
| GET | `/admin/models/{model_id}/audits` |
| GET | `/admin/model-versions/{version_id}/audits` |
| GET | `/admin/model-versions/compare?baseVersionId=&targetVersionId=` |

错误码沿用 P12 契约（`MODEL_*`、`ARTIFACT_NOT_FOUND`、`ROLLBACK_*`、`WITHDRAW_REASON_REQUIRED`），新增 `MODEL_IMPORT_INVALID` / `MODEL_ALREADY_PUBLISHED`。

### 4.4 审核清单（服务端每次实时计算）

| key | internal_test | canary / production |
|---|---|---|
| training_status | 任务成功且 worker 执行，否则阻断 | 同左 |
| dataset_version | 快照存在、published、未撤销 | 同左 |
| dataset_review | source=clear、license=allowed、quality=passed、duplicate=resolved | 同左 |
| leakage_review | 泄漏风险需人工复核时要求 approved | 同左 |
| artifact_exists | 记录 + 文件真实存在 | 同左 |
| file_integrity | 重新计算 SHA-256 必须一致 | 同左 |
| signature | 签名验证通过；`missing` 仅内部测试放行（标记未签名） | 必须 verified 且非 development key |
| evaluation | `not_evaluated` 阻断；`demo` 放行并标注 | `demo` / `smoke_test` 阻断 |
| test_evaluation | 有真实测试集报告才通过；否则不阻断内部测试 | 必须有 `is_real_test_set=true` 的评估报告 |
| runtime_compatibility | 无记录不阻断，仅提示 | 必须有真实设备测试记录且全部 `compatible` |
| publish_permission | 角色含「系统管理员 / 发布管理员」 | 同左 |

额外硬阻断（所有渠道）：`training_profile=smoke_test`、`dataset_origin=synthetic`、`artifactType=smoke_test` 的版本**永不**允许 canary / production（`MODEL_RELEASE_BLOCKED`，原因写入 blockedReasons）。

### 4.5 状态机与审计

- `draft → pending_review → approved → published → withdrawn / rolled_back`；`reject → rejected`；
- 发布前重新执行审核清单，`eligible=false` 返回 `422 MODEL_RELEASE_BLOCKED`；
- 回滚：源必须 `published`，目标必须 `approved/published` 且回滚前清单通过；
- 撤回 / 回滚只追加记录，不删除、不覆盖历史；审计覆盖 create/import、submit、approve、reject、publish、withdraw、rollback、hash/signature 失败、兼容性失败。

---

## 5. 配置

`app/core/config.py` 新增：

- `ed25519_public_key_path: str = ""`（为空时回退 `ml/training/keys/dev_ed25519_public.pem`）
- `.env.example` 补充 `P11R_ED25519_PUBLIC_KEY_PATH=`（公钥可入库，私钥永不入库）

---

## 6. 前端改动（最小化，保持 P11/P12 页面）

1. `src/api/model.ts`：新增 `importModelVersion(input)`，真实模式 `POST /admin/model-versions/import`；Mock 模式抛 `ApiError(409,'MODEL_IMPORT_INVALID','演示模式不支持导入真实产物')`。
2. `src/types/model.ts`：新增 `ModelImportInput` 类型（`trainingTaskId/modelName/description/operator/roleLabel`）。
3. `src/views/model/ModelRepositoryView.vue`：
   - 真实模式显示「从训练任务导入」按钮，弹窗列出 `options.trainingTasks` 中 `succeeded` 的任务（可输入模型名称），成功后跳转版本详情；
   - 顶部提示按 `IS_MOCK` 切换：Mock 显示 `MODEL_DEMO_NOTE`，真实显示「数据来自 FastAPI 与真实训练产物，Smoke Test 产物禁止正式发布」；
   - 统计 hint「含演示发布记录」改为按数据动态文案。
4. `src/views/model/ModelVersionDetailView.vue`：将硬编码「演示」文案改为基于 `artifact.artifactSource` / `trainingTask.executionMode` / `IS_MOCK` 的动态文案（状态提示、文件信息、训练来源、评估来源、发布对话框、成功消息）；发布按钮文案去掉「（演示）」。真实 Smoke Test 版本额外展示「仅限受限内部测试」提示。
5. `src/views/model/ModelDetailView.vue`：审计提示按数据来源动态。
6. Mock 模式全部保持原行为。

---

## 7. 测试与验收

### 7.1 后端 pytest（`services/business-api/tests/test_model_repository.py`）

- 用临时 SQLite（`tmp_path` + `create_engine`）隔离；
- 造 `SUCCEEDED` 任务 + 真实临时产物文件 + 清单（分别构造：正确哈希/错误哈希、有效签名/无签名）；
- 校验导入：哈希重算、签名验签、开发密钥标记；
- 校验审核：internal_test 可放行未签名 Smoke Test，canary/production 因 smoke_test + 无测试集报告 + 开发密钥被阻断；
- 校验状态机：draft 不能发布、approve 需 pending_review、publish 重算拦截、withdraw 必填原因、回滚校验；
- 校验审计只追加（撤回/回滚后记录数增加、原记录不变）。

### 7.2 真实产物联调（仓库现有 T-2026-001 / T-2026-002）

用 `services/business-api/data/p11r.db` 启动 FastAPI，执行：

1. 导入 T-2026-001（未签名）：`fileHashStatus=verified`、`signatureStatus=not_verified`；
2. 导入 T-2026-002（development 签名）：`signatureStatus=verified`、`keyLabel=development`；
3. 两条版本提交内部测试审核 → 审核通过 → 内部测试发布成功（受限流程）；
4. 对 canary / production 调用 review-check 与 publish，均返回阻断，且 blockedReasons 包含 smoke_test / 合成数据 / 无真实测试集报告 / 开发密钥；
5. 撤回其中一条，检查审计与发布记录完整；
6. 复核独立脚本：`python -m ml.training.verify_manifest ml/training/artifacts/T-2026-002/artifact-manifest.json`。

### 7.3 前端

- `npm run typecheck`、`npm run build`；
- 真实模式浏览器实测：模型仓库列表、导入、版本详情（哈希/签名/审核清单）、内部测试发布、正式发布被拦截；
- Mock 模式回归：列表/详情/审核/发布仍可操作。

### 7.4 文档

- 更新 `docs/api/P12_MODEL_CONTRACT.md`（导入接口、兼容性接口、真实/演示区分、渠道矩阵）；
- 新增 `docs/testing/P12_REAL_ARTIFACT_REPORT.md`：记录两次真实导入、哈希、验签、拦截证据与测试命令结果。

---

## 8. 明确不做

- 不加载真实诈骗数据，不训练真实反诈模型；
- 不伪造哈希、签名、评估报告或设备测试；
- 不允许 Smoke Test 版本进入 canary / production；
- 不修改 Android 工程，不自动进入下一阶段。

---

## 9. 需要确认

请确认以上方案（尤其是：**Smoke Test 版本允许在受限内部测试渠道发布，但灰度/正式渠道永久阻断；未签名版本仅内部测试放行**），确认后将按 `tasks.md` 执行。
