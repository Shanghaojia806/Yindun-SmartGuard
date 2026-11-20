"""CPU Smoke Test 训练执行流程。

严格边界：
- trainingProfile = smoke_test，datasetType = synthetic，artifactType = smoke_test；
- 不加载真实诈骗样本，不宣称任何反诈识别能力；
- 只验证 Worker 领取、PyTorch 训练、日志/指标/检查点上报、产物与清单生成。
"""

from __future__ import annotations

import random
from pathlib import Path
from typing import Any

import torch
from torch import nn

from ..callbacks.reporter import TrainingReporter
from ..datasets.synthetic import TensorSplit, make_synthetic_datasets
from ..models.smoke_model import SmokeClassifier, build_optimizer
from .artifacts import ARTIFACTS_ROOT, relative_path, write_manifest
from .client import LeaseLostError, WorkerClient


def _evaluate(model: nn.Module, split: TensorSplit, criterion: nn.Module) -> dict[str, float]:
    model.eval()
    with torch.no_grad():
        logits = model(split.features)
        loss = float(criterion(logits, split.labels).item())
        predictions = logits.argmax(dim=1)
        accuracy = float((predictions == split.labels).float().mean().item())
        true_positive = int(((predictions == 1) & (split.labels == 1)).sum().item())
        false_positive = int(((predictions == 1) & (split.labels == 0)).sum().item())
        false_negative = int(((predictions == 0) & (split.labels == 1)).sum().item())
    precision = true_positive / (true_positive + false_positive) if (true_positive + false_positive) else 0.0
    recall = true_positive / (true_positive + false_negative) if (true_positive + false_negative) else 0.0
    f1 = (2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0
    return {
        "loss": loss,
        "accuracy": accuracy,
        "precision": precision,
        "recall": recall,
        "f1": f1,
    }


def _train_epoch(
    model: nn.Module,
    split: TensorSplit,
    criterion: nn.Module,
    optimizer: torch.optim.Optimizer,
    batch_size: int,
    generator: torch.Generator,
) -> dict[str, float]:
    model.train()
    order = torch.randperm(len(split), generator=generator)
    total_loss = 0.0
    correct = 0
    seen = 0
    for start in range(0, len(split), max(1, batch_size)):
        index = order[start : start + max(1, batch_size)]
        features = split.features[index]
        labels = split.labels[index]
        optimizer.zero_grad()
        logits = model(features)
        loss = criterion(logits, labels)
        loss.backward()
        optimizer.step()
        total_loss += float(loss.item()) * len(index)
        correct += int((logits.argmax(dim=1) == labels).sum().item())
        seen += len(index)
    return {
        "loss": total_loss / max(1, seen),
        "accuracy": correct / max(1, seen),
    }


def _ensure_eligible(context: dict[str, Any]) -> list[str]:
    reasons: list[str] = []
    eligibility = context.get("eligibility") or {}
    if not eligibility.get("eligible"):
        reasons.extend(eligibility.get("blockedReasons") or ["数据集资格校验未通过"])
    snapshot = context.get("datasetSnapshot") or {}
    if snapshot.get("licenseStatus") != "allowed":
        reasons.append("数据集许可状态不是 allowed")
    if snapshot.get("versionStatus") != "published":
        reasons.append("数据集版本不是 published")
    if context.get("trainingProfile") != "smoke_test":
        reasons.append("当前 Worker 仅支持 trainingProfile=smoke_test")
    return reasons


def run_task(
    client: WorkerClient,
    context: dict[str, Any],
    lease_seconds: int = 120,
    artifacts_root: Path = ARTIFACTS_ROOT,
) -> str:
    task_id = str(context["taskId"])
    reporter = TrainingReporter(client, task_id, lease_seconds)

    reasons = _ensure_eligible(context)
    if reasons:
        reporter.failed("训练前资格校验失败：" + "；".join(reasons))
        return "failed"

    parameters = context.get("parameters") or {}
    snapshot = context.get("datasetSnapshot") or {}
    seed = int(parameters.get("randomSeed", 42))
    epochs = int(parameters.get("epochs", 20))
    batch_size = int(parameters.get("batchSize", 32))
    learning_rate = float(parameters.get("learningRate", 0.001))
    checkpoint_interval = int(parameters.get("checkpointInterval", 5))

    task_dir = artifacts_root / task_id
    task_dir.mkdir(parents=True, exist_ok=True)

    try:
        reporter.started()
        reporter.log(
            "训练环境：PyTorch {} / 设备 CPU / trainingProfile=smoke_test / datasetType=synthetic".format(
                torch.__version__
            )
        )
        reporter.log(
            "合成数据拆分：训练 {} / 验证 {} / 测试 {}，固定随机种子 {}".format(
                snapshot.get("trainCount", 0),
                snapshot.get("validationCount", 0),
                snapshot.get("testCount", 0),
                seed,
            )
        )

        torch.manual_seed(seed)
        random.seed(seed)
        train_split, validation_split, test_split = make_synthetic_datasets(
            int(snapshot.get("trainCount", 28)),
            int(snapshot.get("validationCount", 6)),
            seed,
        )

        model = SmokeClassifier()
        criterion = nn.CrossEntropyLoss()
        optimizer = build_optimizer(str(parameters.get("optimizer", "adamw")), model, learning_rate)

        best_loss = float("inf")
        best_epoch = 0
        best_state: dict[str, torch.Tensor] | None = None
        last_validation: dict[str, float] = {}
        final_train: dict[str, float] = {}

        for epoch in range(1, epochs + 1):
            if reporter.heartbeat():
                reporter.log("收到取消请求，正在安全停止训练并释放资源", "WARNING")
                reporter.canceled("训练执行器已按取消请求安全停止，未继续写入产物")
                return "canceled"

            generator = torch.Generator().manual_seed(seed + epoch)
            final_train = _train_epoch(model, train_split, criterion, optimizer, batch_size, generator)
            last_validation = _evaluate(model, validation_split, criterion)

            reporter.metric(
                epoch=epoch,
                train_loss=final_train["loss"],
                validation_loss=last_validation["loss"],
                train_accuracy=final_train["accuracy"],
                validation_accuracy=last_validation["accuracy"],
                precision=last_validation["precision"],
                recall=last_validation["recall"],
                f1=last_validation["f1"],
            )
            reporter.log(
                "Epoch {}/{}：train_loss={:.4f} val_loss={:.4f} val_f1={:.4f}".format(
                    epoch, epochs, final_train["loss"], last_validation["loss"], last_validation["f1"]
                )
            )

            if last_validation["loss"] < best_loss:
                best_loss = last_validation["loss"]
                best_epoch = epoch
                best_state = {key: value.detach().clone() for key, value in model.state_dict().items()}

            if epoch % checkpoint_interval == 0 or epoch == epochs:
                checkpoint_path = task_dir / f"checkpoint-epoch-{epoch:03d}.pt"
                torch.save(
                    {
                        "epoch": epoch,
                        "model_state_dict": model.state_dict(),
                        "optimizer_state_dict": optimizer.state_dict(),
                        "validationLoss": last_validation["loss"],
                        "trainingProfile": "smoke_test",
                    },
                    checkpoint_path,
                )
                reporter.checkpoint(epoch, relative_path(checkpoint_path), last_validation["loss"])

        # 用验证集最优权重作为最终模型
        if best_state is not None:
            model.load_state_dict(best_state)
        test_metrics = _evaluate(model, test_split, criterion)

        model_path = task_dir / "model.pt"
        torch.save(
            {
                "model_state_dict": model.state_dict(),
                "inputDim": 16,
                "hiddenDim": 32,
                "numClasses": 2,
                "trainingProfile": "smoke_test",
                "datasetVersionId": context.get("datasetVersionId", ""),
                "bestEpoch": best_epoch,
            },
            model_path,
        )

        manifest_path, manifest = write_manifest(
            task_id=task_id,
            model_path=model_path,
            dataset_version_id=str(context.get("datasetVersionId", "")),
            framework_version=torch.__version__,
            extra={
                "taskName": context.get("taskName", ""),
                "bestEpoch": best_epoch,
                "testMetrics": {
                    "loss": round(test_metrics["loss"], 6),
                    "accuracy": round(test_metrics["accuracy"], 6),
                    "f1": round(test_metrics["f1"], 6),
                },
            },
        )
        reporter.log(
            "模型产物已写出：{}（sha256={}…）".format(relative_path(model_path), manifest["sha256"][:16])
        )
        reporter.log(
            "产物清单：{}，signatureStatus={}".format(
                relative_path(manifest_path), manifest["signatureStatus"]
            )
        )

        reporter.succeeded(
            final_metrics={
                "epoch": epochs,
                "trainLoss": round(final_train.get("loss", 0.0), 6),
                "validationLoss": round(last_validation.get("loss", 0.0), 6),
                "trainAccuracy": round(final_train.get("accuracy", 0.0), 6),
                "validationAccuracy": round(last_validation.get("accuracy", 0.0), 6),
                "validationPrecision": round(last_validation.get("precision", 0.0), 6),
                "validationRecall": round(last_validation.get("recall", 0.0), 6),
                "validationF1": round(last_validation.get("f1", 0.0), 6),
            },
            best_checkpoint={
                "epoch": best_epoch,
                "checkpointPath": relative_path(
                    task_dir / f"checkpoint-epoch-{best_epoch:03d}.pt"
                ),
                "validationLoss": round(best_loss, 6),
            },
            model_artifact_path=relative_path(model_path),
            manifest_path=relative_path(manifest_path),
            dataset_version=str(context.get("datasetVersion", "")),
        )
        return "succeeded"

    except LeaseLostError:
        reporter.log("任务租约已失效，立即停止训练", "ERROR")
        return "lease_lost"
    except Exception as exc:  # noqa: BLE001 - 任何训练异常都要上报失败
        try:
            reporter.failed(f"训练执行失败：{type(exc).__name__}: {exc}")
        except Exception:  # noqa: BLE001
            pass
        return "failed"
