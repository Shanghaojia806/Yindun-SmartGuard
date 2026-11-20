# P12 补充要求 · 任务清单

> 依赖：`docs/specs/P12_REAL_MODELS/spec.md` 确认后执行。

## T1 后端数据模型
- [x] 新建 `services/business-api/app/models/model_repo.py`（7 张表）
- [x] `app/models/__init__.py` 导入新表，确保 `init_db()` 自动建表
- 验收：空库启动后表结构存在，P11-R 既有表不受影响

## T2 产物校验服务
- [x] 新建 `app/services/artifact_verify.py`
- [x] `app/core/config.py` 增加 `ed25519_public_key_path`，`.env.example` 同步
- 验收：对 T-2026-001 返回 `not_verified`；T-2026-002 返回 `verified` 且 `isDevelopmentKey=true`

## T3 模型仓库服务
- [x] 新建 `app/services/model_repository.py`：导入、序列化、统计、选项、列表、审核清单、状态机动作、审计
- 验收：导入重复任务幂等；`smoke_test` / 合成数据 / 开发密钥 / 无测试集报告在 canary、production 全部阻断

## T4 管理端 API
- [x] 新建 `app/api/admin_models.py` 并在 `app/main.py` 注册
- 验收：`GET /api/v1/admin/models/stats` 等返回 `{data:...}`；错误统一 `{error:{code,message,traceId}}`

## T5 兼容性记录
- [x] `POST /admin/model-versions/{id}/compatibility-tests` 写真实设备测试记录
- 验收：写入 `compatible` 后正式发布仍因 smoke_test / 合成数据阻断；无记录时正式渠道不通过

## T6 后端测试
- [x] 新增 `services/business-api/tests/test_model_repository.py`
- [x] 运行 `..\..\.venv\Scripts\python.exe -m pytest -q`
- 验收：新旧测试全部通过

## T7 真实产物联调
- [x] 启动 FastAPI（8000），导入 T-2026-001、T-2026-002
- [x] 内部测试提交/审核/发布各跑通一条
- [x] canary / production 审核与发布均被拦截并记录审计
- [x] 撤回一条并检查审计/发布记录完整
- 验收：输出命令与响应证据，写入报告

## T8 前端接入
- [x] `src/api/model.ts` 新增 `importModelVersion`
- [x] `src/types/model.ts` 新增 `ModelImportInput`
- [x] `ModelRepositoryView.vue` 增加导入弹窗与动态来源提示
- [x] `ModelVersionDetailView.vue` 去掉硬编码「演示」文案，按数据来源动态展示
- [x] `ModelDetailView.vue` 审计提示动态化
- 验收：Mock 模式行为不变；真实模式可完成导入/审核/发布/拦截

## T9 前端构建与实测
- [x] `npm run typecheck`
- [x] `npm run build`
- [x] 浏览器验证真实模式主要流程
- 验收：命令输出与页面截图/观察结果记录

## T10 文档
- [x] 更新 `docs/api/P12_MODEL_CONTRACT.md`
- [x] 新增 `docs/testing/P12_REAL_ARTIFACT_REPORT.md`
- 验收：包含真实产物测试证据、阻断原因、未实现能力说明

## T11 边界复核
- [x] 确认未提交模型文件、私钥、SQLite、artifacts 到 Git
- [x] 确认未将 Smoke Test 描述为反诈模型成果
- [x] 完成后停止，等待验收
