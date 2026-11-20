# P12 补充要求 · 验收清单

- [x] 模型版本关联真实 `TrainingTask`，导入接口拒绝非成功 / 非 Worker 任务
- [x] 模型文件路径来自后端 `training_results.manifest_path`，前端不读取文件
- [x] 服务端重新计算 SHA-256，T-2026-001/002 均为 `verified`
- [x] 使用 Ed25519 公钥验签，T-2026-002 `verified`，T-2026-001 `not_verified`
- [x] `development` 开发密钥签名被标记，禁止灰度 / 正式发布
- [x] 未签名模型无法进入灰度 / 正式发布（审核 422）
- [x] `trainingProfile=smoke_test` 仅允许受限内部测试，正式渠道阻断
- [x] 合成数据（`datasetType=synthetic` / 合成来源）阻断正式发布
- [x] 缺少真实测试集评估报告时阻断灰度与正式发布
- [x] 发布前重新检查 P10 数据集资格、人工复核状态与来源许可
- [x] 无真实设备测试记录时阻断正式渠道兼容性检查
- [x] 撤回与回滚保留版本状态、发布记录与只追加审计
- [x] 真实产物（`artifactSource=training_worker`）与 Mock 记录（`demo_record`）可区分
- [x] 前端不决定发布资格，FastAPI 在提交 / 审核 / 发布 / 回滚时重新校验
- [x] 使用 P11-R 真实 Smoke Test 产物完成导入、哈希、验签、审核拦截实测
- [x] 未将 Smoke Test 描述或标记为正式反诈模型
- [x] 模型文件、私钥、SQLite、artifacts 未被 Git 跟踪
