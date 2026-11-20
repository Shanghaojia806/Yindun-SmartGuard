"""Worker 独立测试：不依赖 FastAPI，只用本地假客户端验证训练流程与产物。"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

import torch

from ml.training.verify_manifest import verify
from ml.training.worker.runner import run_task


class FakeClient:
    worker_id = "fake-worker"

    def __init__(self, cancel_after: int = 0) -> None:
        self.events: list[dict] = []
        self.heartbeats = 0
        self.cancel_after = cancel_after

    def send_event(self, task_id: str, event: dict) -> dict:
        self.events.append(event)
        return {"accepted": True}

    def heartbeat(self, task_id: str, lease_seconds: int) -> dict:
        self.heartbeats += 1
        cancel = bool(self.cancel_after and self.heartbeats >= self.cancel_after)
        return {"cancelRequested": cancel}


def build_context(eligible: bool = True) -> dict:
    return {
        "taskId": "T-TEST-001",
        "taskName": "单元测试 Smoke Test",
        "datasetId": "DS-TEST-001",
        "datasetVersionId": "VER-DS-TEST-001-01",
        "datasetVersion": "v1.0.0",
        "trainingProfile": "smoke_test",
        "parameters": {
            "epochs": 3,
            "batchSize": 8,
            "learningRate": 0.01,
            "optimizer": "adamw",
            "randomSeed": 7,
            "device": "cpu",
            "validationSplit": 15,
            "checkpointInterval": 2,
        },
        "datasetSnapshot": {
            "trainCount": 20,
            "validationCount": 6,
            "testCount": 6,
            "licenseStatus": "allowed",
            "versionStatus": "published",
        },
        "eligibility": {
            "eligible": eligible,
            "blockedReasons": [] if eligible else ["许可状态不是 allowed"],
        },
    }


def event_types(client: FakeClient) -> list[str]:
    return [event["eventType"] for event in client.events]


def test_smoke_training_success(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.delenv("P11R_ED25519_PRIVATE_KEY_PATH", raising=False)
    client = FakeClient()
    status = run_task(client, build_context(), lease_seconds=120, artifacts_root=tmp_path)
    assert status == "succeeded"

    types = event_types(client)
    assert types[0] == "task.started"
    assert types.count("task.metric") == 3
    assert types.count("task.checkpoint") == 2
    assert types[-1] == "task.succeeded"

    task_dir = tmp_path / "T-TEST-001"
    model_path = task_dir / "model.pt"
    manifest_path = task_dir / "artifact-manifest.json"
    assert model_path.exists()
    assert manifest_path.exists()

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    digest = hashlib.sha256(model_path.read_bytes()).hexdigest()
    assert manifest["sha256"] == digest
    assert manifest["fileSize"] == model_path.stat().st_size
    assert manifest["artifactType"] == "smoke_test"
    assert manifest["trainingProfile"] == "smoke_test"
    assert manifest["framework"] == "pytorch"
    assert manifest["signatureStatus"] in {"missing", "signed"}
    assert manifest["productionReady"] is False

    # 独立校验脚本必须通过
    assert verify(manifest_path) == 0

    # 篡改文件后校验必须失败
    model_path.write_bytes(model_path.read_bytes() + b"tampered")
    assert verify(manifest_path) == 1


def test_cancel_stops_before_writing_model(tmp_path: Path) -> None:
    client = FakeClient(cancel_after=1)
    status = run_task(client, build_context(), lease_seconds=120, artifacts_root=tmp_path)
    assert status == "canceled"
    assert event_types(client)[-1] == "task.canceled"
    assert not (tmp_path / "T-TEST-001" / "model.pt").exists()


def test_ineligible_dataset_blocks_training(tmp_path: Path) -> None:
    client = FakeClient()
    status = run_task(client, build_context(eligible=False), lease_seconds=120, artifacts_root=tmp_path)
    assert status == "failed"
    types = event_types(client)
    assert "task.started" not in types
    assert types[-1] == "task.failed"
    assert not (tmp_path / "T-TEST-001" / "model.pt").exists()


def test_training_exception_reports_failed(tmp_path: Path, monkeypatch) -> None:
    import ml.training.worker.runner as runner

    def boom(*args, **kwargs):
        raise RuntimeError("模拟数据生成失败")

    monkeypatch.setattr(runner, "make_synthetic_datasets", boom)
    client = FakeClient()
    status = run_task(client, build_context(), lease_seconds=120, artifacts_root=tmp_path)
    assert status == "failed"
    types = event_types(client)
    assert types[0] == "task.started"
    assert types[-1] == "task.failed"
    failed_event = client.events[-1]
    assert "模拟数据生成失败" in failed_event["payload"]["message"]


def test_synthetic_dataset_is_reproducible() -> None:
    from ml.training.datasets.synthetic import make_synthetic_datasets

    train_a, val_a, _ = make_synthetic_datasets(30, 8, seed=42)
    train_b, val_b, _ = make_synthetic_datasets(30, 8, seed=42)
    assert torch.equal(train_a.features, train_b.features)
    assert torch.equal(train_a.labels, train_b.labels)
    assert torch.equal(val_a.labels, val_b.labels)
