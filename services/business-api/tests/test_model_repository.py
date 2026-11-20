"""P12 模型仓库测试：真实产物导入、哈希重算、Ed25519 验签与发布拦截。"""

from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519
from fastapi.testclient import TestClient

from app.db import SessionLocal
from app.models import TrainingResultRow, TrainingTask

from conftest import make_task_payload

API = "/api/v1"
ROLE = "系统管理员（模拟）"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sign_manifest(manifest: dict, private_key, public_key_path: Path) -> dict:
    unsigned = {
        key: value for key, value in manifest.items() if key not in {"signature", "signatureStatus"}
    }
    payload = json.dumps(
        unsigned, sort_keys=True, separators=(",", ":"), ensure_ascii=False
    ).encode("utf-8")
    signature = private_key.sign(payload)
    signed = dict(manifest)
    signed["signatureStatus"] = "signed"
    signed["signature"] = {
        "algorithm": "ed25519",
        "keyLabel": "dev-test-key",
        "isDevelopmentKey": True,
        "signedAt": "2026-11-20T00:00:00Z",
        "signatureBase64": base64.b64encode(signature).decode("ascii"),
        "publicKeyPath": str(public_key_path),
    }
    return signed


def certify_task(client: TestClient, tmp_path: Path, *, signed: bool, task_name: str) -> str:
    """创建任务并伪造 P11-R 已完成产物（真实文件 + 清单），返回 taskId。"""
    response = client.post(f"{API}/admin/training-tasks", json=make_task_payload(taskName=task_name))
    assert response.status_code == 201, response.text
    task_id = response.json()["data"]["taskId"]

    artifact_path = tmp_path / f"{task_id}-model.pt"
    artifact_path.write_bytes(f"smoke-test-weights-{task_id}".encode("utf-8"))
    data = artifact_path.read_bytes()

    manifest = {
        "artifactId": f"ART-{task_id}",
        "taskId": task_id,
        "artifactType": "smoke_test",
        "fileName": artifact_path.name,
        "artifactPath": str(artifact_path),
        "fileSize": len(data),
        "sha256": sha256_bytes(data),
        "framework": "pytorch",
        "frameworkVersion": "2.15.0",
        "modelFormat": "torch.state_dict",
        "datasetVersionId": "VER-DS-TEST-001-01",
        "createdAt": "2026-11-20T00:00:00Z",
        "trainingProfile": "smoke_test",
        "productionReady": False,
        "publishBlockedReason": "smoke_test",
        "testMetrics": {"accuracy": 0.92, "f1": 0.9},
    }
    if signed:
        private_key = ed25519.Ed25519PrivateKey.generate()
        public_path = tmp_path / f"{task_id}-public.pem"
        public_path.write_bytes(
            private_key.public_key().public_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PublicFormat.SubjectPublicKeyInfo,
            )
        )
        manifest = _sign_manifest(manifest, private_key, public_path)

    manifest_path = tmp_path / f"{task_id}-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")

    session = SessionLocal()
    try:
        task = session.get(TrainingTask, task_id)
        assert task is not None
        task.status = "SUCCEEDED"
        task.execution_mode = "worker"
        task.training_profile = "smoke_test"
        task.completed_at = "2026-11-20T00:00:00Z"
        session.merge(
            TrainingResultRow(
                task_id=task_id,
                result_id=f"R-{task_id}",
                final_metrics_json=json.dumps(
                    {"validationAccuracy": 0.9, "validationPrecision": 0.89, "validationRecall": 0.88, "validationF1": 0.885, "validationLoss": 0.31}
                ),
                manifest_path=str(manifest_path),
                dataset_version="v1.0.0",
                completed_at="2026-11-20T00:00:00Z",
                evaluation_status="not_evaluated",
                training_profile="smoke_test",
            )
        )
        session.commit()
    finally:
        session.close()
    return task_id


def import_task(client: TestClient, task_id: str, model_name: str = "测试模型") -> dict:
    response = client.post(
        f"{API}/admin/model-versions/import",
        json={
            "trainingTaskId": task_id,
            "modelName": model_name,
            "operator": "测试管理员",
            "roleLabel": ROLE,
        },
    )
    assert response.status_code == 200, response.text
    return response.json()["data"]


def test_import_verifies_real_hash_and_signature(client: TestClient, tmp_path: Path) -> None:
    task_id = certify_task(client, tmp_path, signed=True, task_name="签名产物任务")
    version = import_task(client, task_id, "签名模型")

    assert version["modelVersion"].endswith("-smoke")
    assert version["releaseStatus"] == "draft"
    assert version["trainingTaskId"] == task_id
    assert version["artifactSource"] == "training_worker"
    assert version["productionReady"] is False
    assert "smoke_test" in version["publishBlockedReason"]

    artifact = client.get(f"{API}/admin/model-artifacts/{version['artifactId']}").json()["data"]
    assert artifact["fileHashStatus"] == "verified"
    assert len(artifact["fileHash"]) == 64
    assert artifact["signatureStatus"] == "verified"
    assert artifact["signatureIsDevelopment"] is True
    assert artifact["signatureKeyLabel"] == "dev-test-key"
    assert artifact["isMock"] is False

    audits = client.get(
        f"{API}/admin/model-versions/{version['modelVersionId']}/audits"
    ).json()["data"]
    assert any(item["operation"] == "create_version" for item in audits)


def test_unsigned_artifact_only_allows_internal_test(client: TestClient, tmp_path: Path) -> None:
    task_id = certify_task(client, tmp_path, signed=False, task_name="未签名产物任务")
    version = import_task(client, task_id, "未签名模型")
    artifact = client.get(f"{API}/admin/model-artifacts/{version['artifactId']}").json()["data"]
    assert artifact["signatureStatus"] == "not_verified"

    internal = client.get(
        f"{API}/admin/model-versions/{version['modelVersionId']}/review-check",
        params={"channel": "internal_test", "roleLabel": ROLE},
    ).json()["data"]
    assert internal["eligible"] is True, internal["blockedReasons"]

    formal = client.get(
        f"{API}/admin/model-versions/{version['modelVersionId']}/review-check",
        params={"channel": "production", "roleLabel": ROLE},
    ).json()["data"]
    assert formal["eligible"] is False
    assert any("未签名" in reason for reason in formal["blockedReasons"])
    assert any("smoke_test" in reason for reason in formal["blockedReasons"])

    assert (
        client.post(
            f"{API}/admin/model-versions/{version['modelVersionId']}/submit-review",
            json={"channel": "internal_test", "operator": "测试管理员", "roleLabel": ROLE},
        ).status_code
        == 200
    )
    approved = client.post(
        f"{API}/admin/model-versions/{version['modelVersionId']}/approve",
        json={"channel": "internal_test", "operator": "测试管理员", "roleLabel": ROLE},
    )
    assert approved.status_code == 200, approved.text
    published = client.post(
        f"{API}/admin/model-versions/{version['modelVersionId']}/publish",
        json={
            "channel": "internal_test",
            "deviceScope": "内部测试设备",
            "notes": "受限内部测试",
            "operator": "测试管理员",
            "roleLabel": ROLE,
        },
    )
    assert published.status_code == 200, published.text
    assert "受限内部测试" in published.json()["data"]["operationNotes"]


def test_formal_release_is_blocked_by_server(client: TestClient, tmp_path: Path) -> None:
    task_id = certify_task(client, tmp_path, signed=True, task_name="正式发布拦截任务")
    version = import_task(client, task_id, "拦截模型")
    version_id = version["modelVersionId"]

    assert (
        client.post(
            f"{API}/admin/model-versions/{version_id}/submit-review",
            json={"channel": "canary", "operator": "测试管理员", "roleLabel": ROLE},
        ).status_code
        == 200
    )
    approve = client.post(
        f"{API}/admin/model-versions/{version_id}/approve",
        json={"channel": "canary", "operator": "测试管理员", "roleLabel": ROLE},
    )
    assert approve.status_code == 422, approve.text
    body = approve.json()["error"]
    assert body["code"] == "MODEL_REVIEW_BLOCKED"
    assert "smoke_test" in body["message"]
    assert "真实测试集" in body["message"]

    # 无发布权限角色不通过
    denied = client.post(
        f"{API}/admin/model-versions/{version_id}/publish",
        json={"channel": "internal_test", "roleLabel": "只读审计员"},
    )
    assert denied.status_code == 403
    assert denied.json()["error"]["code"] == "MODEL_PERMISSION_DENIED"


def test_hash_mismatch_blocks_publish_and_audits(client: TestClient, tmp_path: Path) -> None:
    task_id = certify_task(client, tmp_path, signed=True, task_name="哈希篡改任务")
    version = import_task(client, task_id, "篡改模型")

    artifact = client.get(f"{API}/admin/model-artifacts/{version['artifactId']}").json()["data"]
    Path(artifact["artifactPath"]).write_bytes(b"tampered-bytes")

    check = client.get(
        f"{API}/admin/model-versions/{version['modelVersionId']}/review-check",
        params={"channel": "internal_test", "roleLabel": ROLE},
    ).json()["data"]
    assert check["eligible"] is False
    assert any("完整性" in reason for reason in check["blockedReasons"])


def test_withdraw_and_rollback_keep_audit_history(client: TestClient, tmp_path: Path) -> None:
    first = certify_task(client, tmp_path, signed=True, task_name="回滚基线任务")
    second = certify_task(client, tmp_path, signed=True, task_name="回滚目标任务")
    third = certify_task(client, tmp_path, signed=True, task_name="回滚新版本任务")
    v1 = import_task(client, first, "回滚模型")
    v2 = import_task(client, second, "回滚模型")
    v3 = import_task(client, third, "回滚模型")
    model_id = v1["modelId"]
    assert v1["modelId"] == v2["modelId"] == v3["modelId"]

    def approve_and_publish(version_id: str) -> None:
        assert (
            client.post(
                f"{API}/admin/model-versions/{version_id}/submit-review",
                json={"channel": "internal_test", "operator": "测试管理员", "roleLabel": ROLE},
            ).status_code
            == 200
        )
        assert (
            client.post(
                f"{API}/admin/model-versions/{version_id}/approve",
                json={"channel": "internal_test", "operator": "测试管理员", "roleLabel": ROLE},
            ).status_code
            == 200
        )
        assert (
            client.post(
                f"{API}/admin/model-versions/{version_id}/publish",
                json={"channel": "internal_test", "operator": "测试管理员", "roleLabel": ROLE},
            ).status_code
            == 200
        )

    approve_and_publish(v1["modelVersionId"])

    # 撤回必须填写原因
    missing = client.post(
        f"{API}/admin/model-versions/{v1['modelVersionId']}/withdraw",
        json={"reason": "", "operator": "测试管理员", "roleLabel": ROLE},
    )
    assert missing.status_code == 400
    assert missing.json()["error"]["code"] == "WITHDRAW_REASON_REQUIRED"

    withdrawn = client.post(
        f"{API}/admin/model-versions/{v1['modelVersionId']}/withdraw",
        json={"reason": "误报率过高", "impact": "停止内部分发", "operator": "测试管理员", "roleLabel": ROLE},
    )
    assert withdrawn.status_code == 200, withdrawn.text
    assert withdrawn.json()["data"]["releaseStatus"] == "withdrawn"

    audits_before = client.get(f"{API}/admin/models/{model_id}/audits").json()["data"]
    releases_before = client.get(f"{API}/admin/models/{model_id}/releases").json()["data"]

    # 重新发布 v2 作为回滚源，v3 作为回滚目标
    approve_and_publish(v2["modelVersionId"])
    assert (
        client.post(
            f"{API}/admin/model-versions/{v3['modelVersionId']}/submit-review",
            json={"channel": "internal_test", "operator": "测试管理员", "roleLabel": ROLE},
        ).status_code
        == 200
    )
    assert (
        client.post(
            f"{API}/admin/model-versions/{v3['modelVersionId']}/approve",
            json={"channel": "internal_test", "operator": "测试管理员", "roleLabel": ROLE},
        ).status_code
        == 200
    )

    rollback = client.post(
        f"{API}/admin/models/{model_id}/rollback",
        json={
            "fromVersionId": v2["modelVersionId"],
            "toVersionId": v3["modelVersionId"],
            "notes": "回退到新版本",
            "operator": "测试管理员",
            "roleLabel": ROLE,
        },
    )
    assert rollback.status_code == 200, rollback.text
    record = rollback.json()["data"]
    assert record["rollbackFrom"] == v2["modelVersionId"]
    assert record["rollbackTo"] == v3["modelVersionId"]

    source = client.get(
        f"{API}/admin/models/{model_id}/versions/{v2['modelVersionId']}"
    ).json()["data"]
    target = client.get(
        f"{API}/admin/models/{model_id}/versions/{v3['modelVersionId']}"
    ).json()["data"]
    assert source["releaseStatus"] == "rolled_back"
    assert target["releaseStatus"] == "published"

    audits_after = client.get(f"{API}/admin/models/{model_id}/audits").json()["data"]
    releases_after = client.get(f"{API}/admin/models/{model_id}/releases").json()["data"]
    assert len(audits_after) > len(audits_before)
    assert len(releases_after) > len(releases_before)
    assert any(item["operation"] == "rollback" for item in audits_after)
    assert any(item["operation"] == "withdraw" for item in audits_after)


def test_dataset_snapshot_endpoint_returns_server_snapshot(client: TestClient, tmp_path: Path) -> None:
    """P13：模型版本数据集卡片必须读服务端快照，而不是 P10 Mock 数据。"""
    task_id = certify_task(client, tmp_path, signed=False, task_name="数据集快照任务")
    version = import_task(client, task_id, "快照模型")

    response = client.get(
        f"{API}/admin/model-versions/{version['modelVersionId']}/dataset-snapshot"
    )
    assert response.status_code == 200, response.text
    data = response.json()["data"]
    assert data["dataSource"] == "dataset_version_snapshot"
    assert data["versionId"] == "VER-DS-TEST-001-01"
    assert data["datasetId"]
    assert "checks" in data and len(data["checks"]) >= 7
    assert data["eligible"] is True, data["blockedReasons"]


def test_duplicate_import_is_idempotent(client: TestClient, tmp_path: Path) -> None:
    task_id = certify_task(client, tmp_path, signed=False, task_name="幂等导入任务")
    first = import_task(client, task_id, "幂等模型")
    second = import_task(client, task_id, "幂等模型")
    assert first["modelVersionId"] == second["modelVersionId"]
    models = client.get(f"{API}/admin/models").json()["data"]
    assert models["total"] == 1
