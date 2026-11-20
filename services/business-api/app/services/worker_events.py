"""Worker 事件处理：幂等、状态机校验、日志 / 指标 / 检查点 / 结果落库。"""

from __future__ import annotations

import json
import re
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..core.errors import ApiError
from ..core.time import utc_iso
from ..models import (
    TrainingCheckpointRow,
    TrainingLog,
    TrainingMetricRow,
    TrainingResultRow,
    TrainingTask,
    WorkerEvent,
)
from ..schemas.worker import WORKER_EVENT_TYPES, WorkerEventIn
from ..services.dataset_eligibility import evaluate_snapshot
from ..models import DatasetVersionSnapshot

# 常见敏感串脱敏：Bearer Token、URL 内嵌凭证与 key=value 形式的密钥
_REDACTION_RULES = [
    (re.compile(r"(?i)bearer\s+[a-z0-9._\-]+"), "Bearer ***"),
    (re.compile(r"(?i)(token|secret|password|apikey|api_key)=([^\s&;]+)"), r"\1=***"),
    (re.compile(r"([A-Za-z]:\\\\[^\s\"']+)"), "<local-path>"),
]


def sanitize(message: str) -> str:
    cleaned = message
    for pattern, replacement in _REDACTION_RULES:
        cleaned = pattern.sub(replacement, cleaned)
    return cleaned[:2000]


def worker_task_context(session: Session, task: TrainingTask) -> dict[str, Any]:
    """提供给 Worker 的任务上下文；每次领取与重新获取都重新计算资格。"""
    snapshot = session.execute(
        select(DatasetVersionSnapshot).where(
            DatasetVersionSnapshot.dataset_version_id == task.dataset_version_id
        )
    ).scalar_one_or_none()
    eligibility = (
        evaluate_snapshot(snapshot)
        if snapshot is not None
        else {"eligible": False, "checks": [], "blockedReasons": ["服务端缺少数据集版本快照"]}
    )
    return {
        "taskId": task.task_id,
        "taskName": task.task_name,
        "datasetId": task.dataset_id,
        "datasetVersionId": task.dataset_version_id,
        "datasetVersion": task.dataset_version,
        "model": json.loads(task.model_config_json or "{}"),
        "parameters": json.loads(task.parameters_json or "{}"),
        "datasetSnapshot": json.loads(task.dataset_snapshot_json or "{}"),
        "requestedAt": task.created_at,
        "attempt": task.attempt,
        "trainingProfile": task.training_profile,
        "artifactDir": f"ml/training/artifacts/{task.task_id}",
        "cancelRequested": bool(task.cancel_requested_at),
        "eligibility": eligibility,
    }


def _require_running(task: TrainingTask, event: WorkerEventIn) -> None:
    if event.workerId and task.worker_id and event.workerId != task.worker_id:
        raise ApiError(409, "WORKER_MISMATCH", "事件来源与任务领取的 Worker 不一致")
    if task.status != "RUNNING":
        raise ApiError(
            409,
            "TASK_STATE_INVALID",
            f"任务当前状态为 {task.status}，不允许接收 {event.eventType} 事件",
        )


def _upsert_metric(session: Session, task_id: str, payload: dict[str, Any]) -> None:
    try:
        epoch = int(payload["epoch"])
        row = session.execute(
            select(TrainingMetricRow).where(
                TrainingMetricRow.task_id == task_id, TrainingMetricRow.epoch == epoch
            )
        ).scalar_one_or_none()
        values = {
            "train_loss": float(payload["trainLoss"]),
            "validation_loss": float(payload["validationLoss"]),
            "train_accuracy": float(payload["trainAccuracy"]),
            "validation_accuracy": float(payload["validationAccuracy"]),
            "validation_precision": float(payload["validationPrecision"]),
            "validation_recall": float(payload["validationRecall"]),
            "validation_f1": float(payload["validationF1"]),
        }
    except (KeyError, TypeError, ValueError) as exc:
        raise ApiError(400, "EVENT_PAYLOAD_INVALID", f"指标事件字段不合法：{exc}") from exc

    if row is None:
        row = TrainingMetricRow(task_id=task_id, epoch=epoch, recorded_at=utc_iso(), **values)
        session.add(row)
    else:
        for key, value in values.items():
            setattr(row, key, value)
        row.recorded_at = utc_iso()


def _write_result(session: Session, task: TrainingTask, payload: dict[str, Any]) -> None:
    final_metrics = payload.get("finalMetrics") or {}
    best_checkpoint = payload.get("bestCheckpoint") or {}
    row = session.get(TrainingResultRow, task.task_id)
    if row is None:
        row = TrainingResultRow(task_id=task.task_id)
    row.result_id = str(payload.get("resultId") or f"R-{task.task_id}")
    row.final_metrics_json = json.dumps(final_metrics, ensure_ascii=False)
    row.best_checkpoint_json = json.dumps(best_checkpoint, ensure_ascii=False)
    row.model_artifact_path = str(payload.get("modelArtifactPath") or "")
    row.manifest_path = str(payload.get("manifestPath") or "")
    row.dataset_version = task.dataset_version
    row.completed_at = utc_iso()
    row.evaluation_status = str(payload.get("evaluationStatus") or "not_evaluated")
    row.evaluation_summary = str(
        payload.get("evaluationSummary")
        or "CPU Smoke Test 产物，未进行反诈能力评估，不允许进入正式发布流程。"
    )
    row.simulated = False
    row.training_profile = task.training_profile
    session.add(row)


def handle_event(session: Session, task_id: str, event: WorkerEventIn) -> dict[str, Any]:
    if event.eventType not in WORKER_EVENT_TYPES:
        raise ApiError(400, "EVENT_TYPE_INVALID", f"不支持的事件类型：{event.eventType}")
    if not event.eventId:
        raise ApiError(400, "EVENT_ID_REQUIRED", "事件必须携带唯一 eventId")

    task = session.get(TrainingTask, task_id)
    if task is None:
        raise ApiError(404, "TRAINING_TASK_NOT_FOUND", "未找到该训练任务")

    existing = session.get(WorkerEvent, event.eventId)
    if existing is not None:
        return {
            "taskId": task_id,
            "taskStatus": task.status.lower(),
            "accepted": True,
            "duplicate": True,
        }

    payload = event.payload or {}
    now = utc_iso()

    if event.eventType == "task.started":
        _require_running(task, event)
        task.heartbeat_at = now
        task.last_event_at = now
        task.started_at = task.started_at or now
        session.add(
            TrainingLog(
                log_id=f"LOG-{task_id}-STARTED-{now}",
                task_id=task_id,
                timestamp=now,
                level="INFO",
                message=sanitize("训练执行器已开始执行 CPU Smoke Test"),
                source="worker",
            )
        )

    elif event.eventType == "task.log":
        _require_running(task, event)
        level = str(payload.get("level", "INFO")).upper()
        if level not in {"INFO", "WARNING", "ERROR", "DEBUG"}:
            level = "INFO"
        message = sanitize(str(payload.get("message", "")))
        if not message:
            raise ApiError(400, "EVENT_PAYLOAD_INVALID", "日志事件缺少 message")
        task.last_event_at = now
        session.add(
            TrainingLog(
                log_id=f"LOG-{task_id}-{event.eventId}",
                task_id=task_id,
                timestamp=now,
                level=level,
                message=message,
                source="worker",
            )
        )

    elif event.eventType == "task.metric":
        _require_running(task, event)
        _upsert_metric(session, task_id, payload)
        task.last_event_at = now

    elif event.eventType == "task.checkpoint":
        _require_running(task, event)
        try:
            epoch = int(payload["epoch"])
            path = str(payload["checkpointPath"])
            validation_loss = float(payload.get("validationLoss") or 0.0)
        except (KeyError, TypeError, ValueError) as exc:
            raise ApiError(400, "EVENT_PAYLOAD_INVALID", f"检查点事件字段不合法：{exc}") from exc
        session.add(
            TrainingCheckpointRow(
                task_id=task_id,
                epoch=epoch,
                checkpoint_path=path,
                validation_loss=validation_loss,
                created_at=now,
            )
        )
        task.last_event_at = now

    elif event.eventType == "task.succeeded":
        _require_running(task, event)
        task.status = "SUCCEEDED"
        task.completed_at = now
        task.last_event_at = now
        task.lease_expires_at = ""
        _write_result(session, task, payload)
        session.add(
            TrainingLog(
                log_id=f"LOG-{task_id}-SUCCEEDED-{now}",
                task_id=task_id,
                timestamp=now,
                level="INFO",
                message=sanitize("训练执行器上报任务成功，模型产物与清单已写入产物目录"),
                source="worker",
            )
        )

    elif event.eventType == "task.failed":
        _require_running(task, event)
        task.status = "FAILED"
        task.completed_at = now
        task.last_event_at = now
        task.lease_expires_at = ""
        task.error_message = sanitize(str(payload.get("message") or "训练执行器上报失败"))
        session.add(
            TrainingLog(
                log_id=f"LOG-{task_id}-FAILED-{now}",
                task_id=task_id,
                timestamp=now,
                level="ERROR",
                message=task.error_message,
                source="worker",
            )
        )

    elif event.eventType == "task.canceled":
        _require_running(task, event)
        if not task.cancel_requested_at:
            raise ApiError(409, "CANCEL_NOT_REQUESTED", "未收到取消请求，执行器不得直接置为已取消")
        task.status = "CANCELED"
        task.completed_at = now
        task.last_event_at = now
        task.lease_expires_at = ""
        session.add(
            TrainingLog(
                log_id=f"LOG-{task_id}-CANCELED-{now}",
                task_id=task_id,
                timestamp=now,
                level="WARNING",
                message=sanitize(
                    str(payload.get("message") or "训练执行器已安全停止并确认取消，资源已释放")
                ),
                source="worker",
            )
        )

    session.add(
        WorkerEvent(
            event_id=event.eventId,
            task_id=task_id,
            event_type=event.eventType,
            received_at=now,
            payload_json=json.dumps(payload, ensure_ascii=False),
        )
    )
    session.commit()
    return {
        "taskId": task_id,
        "taskStatus": task.status.lower(),
        "accepted": True,
        "duplicate": False,
    }
