# P11-R 训练执行器（Python Worker）

独立的 PyTorch CPU 训练执行器：领取 FastAPI 队列中的任务、执行训练、上报日志 / 指标 /
检查点 / 结果，并写出模型产物与完整性清单。

> 当前阶段只实现 **CPU Smoke Test**：
> `trainingProfile=smoke_test`、`datasetType=synthetic`、`artifactType=smoke_test`。
> 它不包含任何真实诈骗样本，也不是反诈模型，产物禁止进入正式发布流程。

## 目录结构

```
ml/training/
├── worker/
│   ├── main.py        # 入口：--once / --loop / --max-tasks
│   ├── client.py      # FastAPI HTTP 客户端（Worker Token 只放在请求头）
│   ├── runner.py      # 训练流程与状态上报
│   └── artifacts.py   # SHA-256、manifest、Ed25519 签名
├── datasets/synthetic.py  # 固定种子的合成二分类数据
├── models/smoke_model.py  # 最小 MLP
├── callbacks/reporter.py  # 事件 ID、心跳、取消检查
├── artifacts/{taskId}/    # 运行产物（已在 .gitignore 中排除）
├── verify_manifest.py     # 独立哈希 / 签名校验脚本
├── tests/                 # 不依赖 FastAPI 的 Worker 测试
└── requirements.txt
```

## 环境准备

```powershell
# 仓库根目录
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r ml\training\requirements.txt
$env:P11R_API_BASE_URL = "http://127.0.0.1:8000/api/v1"
$env:P11R_WORKER_TOKEN  = "<与后端 P11R_WORKER_TOKEN 一致>"
$env:P11R_WORKER_ID     = "worker-cpu-01"
```

## 启动

```powershell
# 只执行一个任务后退出（适合验证链路）
python -m ml.training.worker.main --once

# 持续轮询
python -m ml.training.worker.main --loop --interval 2
```

执行流程：

1. `POST /internal/worker/tasks/claim` 领取任务；
2. `GET /internal/worker/tasks/{taskId}/context` 重新获取最新资格状态；
3. 资格不通过 → 直接上报 `task.failed`，不开始训练；
4. 上报 `task.started`，用固定种子生成合成数据，训练 MLP；
5. 每个 Epoch 上报 `task.metric` 与 `task.log`，并发送心跳；
6. 心跳返回 `cancelRequested=true` → 安全停止并上报 `task.canceled`；
7. 按 `checkpointInterval` 保存检查点并上报 `task.checkpoint`；
8. 训练结束保存 `model.pt`，计算 SHA-256，写 `artifact-manifest.json`；
9. 上报 `task.succeeded`。

## 产物与校验

```
ml/training/artifacts/{taskId}/
├── model.pt
├── checkpoint-epoch-XXX.pt
└── artifact-manifest.json
```

```powershell
python -m ml.training.verify_manifest ml/training/artifacts/T-2026-001/artifact-manifest.json
```

脚本会重新计算 SHA-256、比对文件大小、验证 Ed25519 签名（如有），
并提示 Smoke Test 产物不得进入正式发布。

## 签名

- 私钥只从 `P11R_ED25519_PRIVATE_KEY_PATH` 指向的本地文件读取（`*.pem` 已被 .gitignore 排除）；
- 也可放在 `ml/training/keys/dev_ed25519_private.pem`（默认开发密钥位置）；
- 未配置私钥时清单写入 `signatureStatus=missing`，绝不伪造签名；
- 本地开发密钥通过 `P11R_ED25519_KEY_LABEL=development` 明确标记。

## 测试

```powershell
# 仓库根目录
python -m pytest ml\training\tests -q
```

测试不需要启动 FastAPI：使用本地假客户端验证训练、取消、失败、产物哈希与校验脚本。
