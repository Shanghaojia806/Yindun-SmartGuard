"""P11-R 业务后端接口测试（真实 HTTP 栈 + 临时 SQLite）。"""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.db import SessionLocal
from app.models import TrainingTask

from conftest import ELIGIBLE_SNAPSHOT, make_task_payload, worker_headers

API = "/api/v1"


def create_task(client: TestClient, **overrides) -> dict:
    response = client.post(f"{API}/admin/training-tasks", json=make_task_payload(**overrides))
    assert response.status_code == 201, response.text
    return response.json()["data"]


def claim(client: TestClient, worker_id: str = "test-worker") -> dict | None:
    response = client.post(
        f"{API}/internal/worker/tasks/claim",
        json={"workerId": worker_id, "leaseSeconds": 120},
        headers=worker_headers(),
    )
    assert response.status_code == 200, response.text
    return response.json()["data"]


def send_event(client: TestClient, task_id: str, event_type: str, payload: dict, event_id: str | None = None) -> dict:
    body = {
        "eventId": event_id or f"{task_id}-{event_type}-test",
        "eventType": event_type,
        "timestamp": "",
        "workerId": "test-worker",
        "payload": payload,
    }
    response = client.post(
        f"{API}/internal/worker/tasks/{task_id}/events", json=body, headers=worker_headers()
    )
    assert response.status_code == 200, response.text
    return response.json()["data"]


def test_health_and_stats(client: TestClient) -> None:
    assert client.get("/health").json()["data"]["status"] == "ok"
    stats = client.get(f"{API}/admin/training-tasks/stats").json()["data"]
    assert stats == {"total": 0, "pending": 0, "running": 0, "succeeded": 0, "failed": 0, "canceled": 0}


def test_create_task_success_and_queries(client: TestClient) -> None:
    task = create_task(client)
    assert task["taskStatus"] == "pending"
    assert task["executionMode"] == "worker"
    assert task["datasetVersionId"] == ELIGIBLE_SNAPSHOT["datasetVersionId"]
    assert task["datasetSplit"]["trainCount"] == 20
    assert task["progress"]["totalEpochs"] == 3

    listed = client.get(f"{API}/admin/training-tasks").json()["data"]
    assert listed["total"] == 1
    assert listed["items"][0]["taskId"] == task["taskId"]

    detail = client.get(f"{API}/admin/training-tasks/{task['taskId']}").json()["data"]
    assert detail["taskId"] == task["taskId"]

    logs = client.get(f"{API}/admin/training-tasks/{task['taskId']}/logs").json()["data"]
    assert logs["total"] >= 1
    assert logs["executionMode"] == "worker"

    metrics = client.get(f"{API}/admin/training-tasks/{task['taskId']}/metrics").json()["data"]
    assert metrics["source"] == "worker"
    assert metrics["metrics"] == []

    result = client.get(f"{API}/admin/training-tasks/{task['taskId']}/result").json()["data"]
    assert result is None


def test_create_task_validation_errors(client: TestClient) -> None:
    missing_name = client.post(
        f"{API}/admin/training-tasks", json=make_task_payload(taskName="   ")
    )
    assert missing_name.status_code == 400
    assert missing_name.json()["error"]["code"] == "TASK_NAME_REQUIRED"

    bad_params = make_task_payload()
    bad_params["trainingParameters"]["epochs"] = 0
    response = client.post(f"{API}/admin/training-tasks", json=bad_params)
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "PARAMETER_INVALID"

    bad_model = client.post(f"{API}/admin/training-tasks", json=make_task_payload(modelType="unknown"))
    assert bad_model.status_code == 400
    assert bad_model.json()["error"]["code"] == "MODEL_TYPE_INVALID"

    not_trainable = client.post(
        f"{API}/admin/training-tasks",
        json=make_task_payload(datasetVersionId="VER-DS-TEST-001-02"),
    )
    assert not_trainable.status_code == 422
    assert not_trainable.json()["error"]["code"] == "DATASET_NOT_TRAINABLE"
    assert not_trainable.json()["error"]["traceId"]

    missing_version = client.post(
        f"{API}/admin/training-tasks",
        json=make_task_payload(datasetVersionId="VER-NOT-EXIST"),
    )
    assert missing_version.status_code == 404
    assert missing_version.json()["error"]["code"] == "DATASET_VERSION_NOT_FOUND"


def test_dataset_checks(client: TestClient) -> None:
    checks = client.get(f"{API}/admin/datasets/DS-TEST-001/training-checks").json()["data"]
    assert len(checks) == 2
    eligible = [item for item in checks if item["versionId"] == ELIGIBLE_SNAPSHOT["datasetVersionId"]][0]
    assert eligible["eligible"] is True
    assert {check["key"] for check in eligible["checks"]} == {
        "source",
        "license",
        "quality",
        "duplicate",
        "version_status",
        "manual_review",
        "split",
    }

    single = client.get(
        f"{API}/admin/dataset-versions/{ELIGIBLE_SNAPSHOT['datasetVersionId']}/training-check",
        params={"datasetId": "DS-TEST-001"},
    ).json()["data"]
    assert single["eligible"] is True


def test_worker_token_required(client: TestClient) -> None:
    no_token = client.post(f"{API}/internal/worker/tasks/claim", json={"workerId": "w", "leaseSeconds": 60})
    assert no_token.status_code == 401
    wrong = client.post(
        f"{API}/internal/worker/tasks/claim",
        json={"workerId": "w", "leaseSeconds": 60},
        headers={"X-Worker-Token": "wrong"},
    )
    assert wrong.status_code == 401


def test_full_worker_flow_success(client: TestClient) -> None:
    task = create_task(client)
    context = claim(client)
    assert context is not None
    assert context["taskId"] == task["taskId"]
    assert context["eligibility"]["eligible"] is True

    assert claim(client) is None  # 已被领取，不应重复派发

    send_event(client, task["taskId"], "task.started", {"message": "start"})
    send_event(client, task["taskId"], "task.log", {"message": "epoch start"})
    send_event(
        client,
        task["taskId"],
        "task.metric",
        {
            "epoch": 1,
            "trainLoss": 0.5,
            "validationLoss": 0.6,
            "trainAccuracy": 0.8,
            "validationAccuracy": 0.7,
            "validationPrecision": 0.71,
            "validationRecall": 0.69,
            "validationF1": 0.7,
        },
    )
    send_event(
        client,
        task["taskId"],
        "task.checkpoint",
        {"epoch": 1, "checkpointPath": "ml/training/artifacts/x/checkpoint.pt", "validationLoss": 0.6},
    )
    send_event(
        client,
        task["taskId"],
        "task.succeeded",
        {
            "finalMetrics": {"epoch": 1, "trainLoss": 0.5},
            "bestCheckpoint": {"epoch": 1, "checkpointPath": "ml/training/artifacts/x/checkpoint.pt"},
            "modelArtifactPath": "ml/training/artifacts/x/model.pt",
            "manifestPath": "ml/training/artifacts/x/artifact-manifest.json",
        },
    )

    detail = client.get(f"{API}/admin/training-tasks/{task['taskId']}").json()["data"]
    assert detail["taskStatus"] == "succeeded"
    assert detail["startedAt"] and detail["completedAt"]

    metrics = client.get(f"{API}/admin/training-tasks/{task['taskId']}/metrics").json()["data"]
    assert len(metrics["metrics"]) == 1
    assert metrics["metrics"][0]["validationF1"] == 0.7

    result = client.get(f"{API}/admin/training-tasks/{task['taskId']}/result").json()["data"]
    assert result is not None
    assert result["simulated"] is False
    assert result["modelArtifactPath"].endswith("model.pt")

    # 非法状态流转：已完成任务不允许再上报指标
    bad = client.post(
        f"{API}/internal/worker/tasks/{task['taskId']}/events",
        json={
            "eventId": "late-event",
            "eventType": "task.metric",
            "payload": {},
            "workerId": "test-worker",
        },
        headers=worker_headers(),
    )
    assert bad.status_code == 409
    assert bad.json()["error"]["code"] == "TASK_STATE_INVALID"


def test_event_idempotency(client: TestClient) -> None:
    task = create_task(client)
    claim(client)
    event_id = "dup-event-1"
    first = send_event(client, task["taskId"], "task.log", {"message": "hello"}, event_id)
    assert first["duplicate"] is False
    second = send_event(client, task["taskId"], "task.log", {"message": "hello"}, event_id)
    assert second["duplicate"] is True

    logs = client.get(f"{api_logs(task)}").json()["data"]
    assert [item["message"] for item in logs["items"]].count("hello") == 1


def api_logs(task: dict) -> str:
    return f"{API}/admin/training-tasks/{task['taskId']}/logs"


def test_cancel_pending_and_running(client: TestClient) -> None:
    pending = create_task(client)
    canceled = client.post(f"{API}/admin/training-tasks/{pending['taskId']}/cancel").json()["data"]
    assert canceled["taskStatus"] == "canceled"

    running = create_task(client)
    claim(client)
    requested = client.post(f"{API}/admin/training-tasks/{running['taskId']}/cancel").json()["data"]
    assert requested["taskStatus"] == "running"
    assert requested["cancelRequestedAt"]

    send_event(client, running["taskId"], "task.canceled", {"message": "stopped"})
    detail = client.get(f"{API}/admin/training-tasks/{running['taskId']}").json()["data"]
    assert detail["taskStatus"] == "canceled"

    conflict = client.post(f"{API}/admin/training-tasks/{running['taskId']}/cancel")
    assert conflict.status_code == 409
    assert conflict.json()["error"]["code"] == "TASK_STATE_INVALID"


def test_worker_cannot_cancel_without_request(client: TestClient) -> None:
    task = create_task(client)
    claim(client)
    response = client.post(
        f"{API}/internal/worker/tasks/{task['taskId']}/events",
        json={
            "eventId": "self-cancel",
            "eventType": "task.canceled",
            "payload": {},
            "workerId": "test-worker",
        },
        headers=worker_headers(),
    )
    assert response.status_code == 409
    assert response.json()["error"]["code"] == "CANCEL_NOT_REQUESTED"


def test_retry_only_failed_task(client: TestClient) -> None:
    task = create_task(client)
    claim(client)
    send_event(client, task["taskId"], "task.failed", {"message": "模拟失败"})

    retried = client.post(f"{API}/admin/training-tasks/{task['taskId']}/retry")
    assert retried.status_code == 201, retried.text
    new_task = retried.json()["data"]
    assert new_task["attempt"] == 2
    assert new_task["retryOfTaskId"] == task["taskId"]

    original = client.get(f"{API}/admin/training-tasks/{task['taskId']}").json()["data"]
    assert original["retriedByTaskId"] == new_task["taskId"]

    again = client.post(f"{API}/admin/training-tasks/{task['taskId']}/retry")
    assert again.status_code == 409


def test_lease_timeout_marks_failed(client: TestClient) -> None:
    task = create_task(client)
    claim(client)
    session = SessionLocal()
    try:
        row = session.get(TrainingTask, task["taskId"])
        row.lease_expires_at = "2000-01-01T00:00:00Z"
        session.commit()
    finally:
        session.close()

    stats = client.get(f"{API}/admin/training-tasks/stats").json()["data"]
    assert stats["failed"] == 1
    assert stats["running"] == 0

    detail = client.get(f"{API}/admin/training-tasks/{task['taskId']}").json()["data"]
    assert detail["taskStatus"] == "failed"
    assert "租约超时" in detail["errorMessage"]


def test_heartbeat_and_lease_lost(client: TestClient) -> None:
    task = create_task(client)
    claim(client, worker_id="worker-a")
    ok = client.post(
        f"{API}/internal/worker/tasks/{task['taskId']}/heartbeat",
        json={"workerId": "worker-a", "leaseSeconds": 120},
        headers=worker_headers(),
    )
    assert ok.status_code == 200
    assert ok.json()["data"]["cancelRequested"] is False

    lost = client.post(
        f"{API}/internal/worker/tasks/{task['taskId']}/heartbeat",
        json={"workerId": "worker-b", "leaseSeconds": 120},
        headers=worker_headers(),
    )
    assert lost.status_code == 409
    assert lost.json()["error"]["code"] == "LEASE_LOST"
