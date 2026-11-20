"""pytest 全局配置：测试使用独立临时 SQLite 库，不污染本地开发数据。"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path

_TEMP_DIR = Path(tempfile.mkdtemp(prefix="p11r-test-"))
os.environ["P11R_DATABASE_URL"] = f"sqlite:///{(_TEMP_DIR / 'test.db').as_posix()}"
os.environ["P11R_WORKER_TOKEN"] = "test-worker-token"
os.environ["P11R_ADMIN_TOKEN"] = ""
os.environ["P11R_WORKER_LEASE_SECONDS"] = "120"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.db import Base, engine, init_db  # noqa: E402
from app.main import app  # noqa: E402

WORKER_TOKEN = "test-worker-token"

ELIGIBLE_SNAPSHOT = {
    "datasetId": "DS-TEST-001",
    "datasetName": "测试·合成演示数据集",
    "datasetType": "text",
    "datasetVersionId": "VER-DS-TEST-001-01",
    "datasetVersion": "v1.0.0",
    "sampleCount": 30,
    "trainCount": 20,
    "validationCount": 5,
    "testCount": 5,
    "sourceStatus": "clear",
    "licenseStatus": "allowed",
    "qualityStatus": "passed",
    "duplicateStatus": "resolved",
    "leakageRisk": "grouped",
    "manualReviewStatus": "not_required",
    "versionStatus": "published",
    "snapshotSource": "synthetic_demo",
    "note": "测试用合成快照",
}

BLOCKED_SNAPSHOT = {
    **ELIGIBLE_SNAPSHOT,
    "datasetVersionId": "VER-DS-TEST-001-02",
    "datasetVersion": "v2.0.0",
    "licenseStatus": "unknown",
    "qualityStatus": "pending",
    "duplicateStatus": "pending",
    "versionStatus": "draft",
}


@pytest.fixture()
def client() -> TestClient:
    Base.metadata.drop_all(bind=engine)
    init_db()
    with TestClient(app) as test_client:
        test_client.post("/api/v1/admin/dataset-snapshots", json=ELIGIBLE_SNAPSHOT)
        test_client.post("/api/v1/admin/dataset-snapshots", json=BLOCKED_SNAPSHOT)
        yield test_client


def worker_headers() -> dict[str, str]:
    return {"X-Worker-Token": WORKER_TOKEN}


def make_task_payload(**overrides) -> dict:
    payload = {
        "taskName": "单元测试训练任务",
        "taskDescription": "pytest",
        "modelType": "text_risk_classifier",
        "taskType": "risk_binary",
        "datasetId": "DS-TEST-001",
        "datasetVersionId": "VER-DS-TEST-001-01",
        "trainingParameters": {
            "epochs": 3,
            "batchSize": 8,
            "learningRate": 0.001,
            "optimizer": "adamw",
            "randomSeed": 42,
            "device": "cpu",
            "validationSplit": 15,
            "checkpointInterval": 2,
        },
    }
    payload.update(overrides)
    return payload
