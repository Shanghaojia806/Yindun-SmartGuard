"""训练任务业务逻辑：创建、查询、取消、重试、统计、快照注册。

所有写操作在服务端重新校验数据集资格；前端提交的任何资格结论都不采信。
"""

from __future__ import annotations

import json
import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..core.errors import ApiError
from ..core.time import display_time, utc_iso
from ..models import (
    DatasetVersionSnapshot,
    TrainingCheckpointRow,
    TrainingLog,
    TrainingMetricRow,
    TrainingResultRow,
    TrainingTask,
)
from ..repositories.queue import expire_stale_tasks
from ..schemas.training import CreateTrainingTaskIn, RegisterSnapshotIn, TrainingParametersIn
from .dataset_eligibility import evaluate_snapshot, snapshot_to_check
from .serializers import result_to_dict, task_to_dict

MAX_TASK_NAME = 50
MAX_TASK_DESCRIPTION = 200

MODEL_TYPES: dict[str, dict[str, Any]] = {
    "text_risk_classifier": {
        "label": "文本反诈分类模型",
        "taskTypes": ["risk_binary", "risk_multilabel"],
        "inputModalities": ["text"],
        "modelFamily": "text_classifier_placeholder",
    },
    "screen_multimodal_risk": {
        "label": "屏幕多模态风险模型",
        "taskTypes": ["risk_binary", "risk_multilabel"],
        "inputModalities": ["screen", "text", "multimodal"],
        "modelFamily": "screen_multimodal_placeholder",
    },
    "audio_call_risk": {
        "label": "通话语音风险模型",
        "taskTypes": ["risk_binary"],
        "inputModalities": ["audio"],
        "modelFamily": "audio_classifier_placeholder",
    },
    "video_scam_risk": {
        "label": "短视频风险模型",
        "taskTypes": ["risk_binary", "risk_multilabel"],
        "inputModalities": ["video", "multimodal"],
        "modelFamily": "video_classifier_placeholder",
    },
}

TASK_TYPES = [
    {"value": "risk_binary", "label": "风险二分类"},
    {"value": "risk_multilabel", "label": "风险多标签分类"},
]

OPTIMIZERS = [
    {"value": "adam", "label": "Adam", "note": "通用默认优化器"},
    {"value": "adamw", "label": "AdamW", "note": "带解耦权重衰减"},
    {"value": "sgd", "label": "SGD", "note": "需配合学习率调度使用"},
]

DEVICES = [
    {"value": "cpu", "label": "CPU", "note": "本轮 Smoke Test 固定使用 CPU"},
    {"value": "cuda", "label": "CUDA（GPU）", "note": "需 Worker 机器具备可用 GPU"},
    {"value": "mps", "label": "MPS（Apple GPU）", "note": "需 Apple Silicon 环境"},
]

STATUSES = [
    {"value": "pending", "label": "等待执行"},
    {"value": "running", "label": "运行中"},
    {"value": "succeeded", "label": "训练成功"},
    {"value": "failed", "label": "训练失败"},
    {"value": "canceled", "label": "已取消"},
]

EXECUTION_MODES = [
    {"value": "worker", "label": "真实 Worker"},
]

PARAMETER_LIMITS = {
    "epochs": (1, 200),
    "batchSize": (1, 512),
    "learningRate": (1e-5, 0.1),
    "randomSeed": (0, 4294967295),
    "validationSplit": (5, 50),
    "checkpointInterval": (1, 50),
}


# ===== 校验 =====


def validate_parameters(params: TrainingParametersIn) -> None:
    def fail(message: str) -> None:
        raise ApiError(400, "PARAMETER_INVALID", message)

    for field, (low, high) in PARAMETER_LIMITS.items():
        value = getattr(params, field)
        if field == "learningRate":
            if not isinstance(value, (int, float)) or not (low <= value <= high):
                fail(f"learningRate 必须在 {low}–{high} 之间")
            continue
        if not isinstance(value, int) or isinstance(value, bool) or not (low <= value <= high):
            fail(f"{field} 必须是 {low}–{high} 的整数")

    if params.checkpointInterval > params.epochs:
        fail("checkpointInterval 不能大于 epochs")
    if params.optimizer not in {"adam", "adamw", "sgd"}:
        fail("optimizer 仅支持 adam / adamw / sgd")
    if params.device not in {"cpu", "cuda", "mps"}:
        fail("device 仅支持 cpu / cuda / mps")


def validate_identity(payload: CreateTrainingTaskIn) -> None:
    name = payload.taskName.strip()
    if not name:
        raise ApiError(400, "TASK_NAME_REQUIRED", "请填写任务名称")
    if len(name) > MAX_TASK_NAME:
        raise ApiError(400, "TASK_NAME_TOO_LONG", f"任务名称不能超过 {MAX_TASK_NAME} 个字符")
    if len(payload.taskDescription) > MAX_TASK_DESCRIPTION:
        raise ApiError(
            400, "PARAMETER_INVALID", f"任务描述不能超过 {MAX_TASK_DESCRIPTION} 个字符"
        )

    model = MODEL_TYPES.get(payload.modelType)
    if model is None:
        raise ApiError(400, "MODEL_TYPE_INVALID", "请选择受支持的模型类型")
    if payload.taskType not in model["taskTypes"]:
        raise ApiError(
            400,
            "TASK_TYPE_INVALID",
            f"模型 {payload.modelType} 仅支持任务类型 {', '.join(model['taskTypes'])}",
        )


# ===== 数据集快照 =====


def find_snapshot(session: Session, dataset_version_id: str) -> DatasetVersionSnapshot:
    snapshot = session.execute(
        select(DatasetVersionSnapshot).where(
            DatasetVersionSnapshot.dataset_version_id == dataset_version_id
        )
    ).scalar_one_or_none()
    if snapshot is None:
        raise ApiError(
            404,
            "DATASET_VERSION_NOT_FOUND",
            "服务端没有该数据集版本的快照，请先在数据集管理中登记版本快照后再创建训练任务",
        )
    return snapshot


def ensure_snapshot_trainable(session: Session, dataset_id: str, version_id: str) -> DatasetVersionSnapshot:
    snapshot = find_snapshot(session, version_id)
    if snapshot.dataset_id != dataset_id:
        raise ApiError(404, "DATASET_VERSION_NOT_FOUND", "数据集版本与数据集不一致")
    result = evaluate_snapshot(snapshot)
    if not result["eligible"]:
        reasons = "；".join(result["blockedReasons"])
        raise ApiError(422, "DATASET_NOT_TRAINABLE", f"数据集版本不满足训练条件：{reasons}")
    return snapshot


def register_snapshot(session: Session, payload: RegisterSnapshotIn) -> dict[str, Any]:
    snapshot = session.execute(
        select(DatasetVersionSnapshot).where(
            DatasetVersionSnapshot.dataset_version_id == payload.datasetVersionId
        )
    ).scalar_one_or_none()
    created = snapshot is None
    if snapshot is None:
        snapshot = DatasetVersionSnapshot(dataset_version_id=payload.datasetVersionId)
    snapshot.dataset_id = payload.datasetId
    snapshot.dataset_name = payload.datasetName
    snapshot.dataset_type = payload.datasetType
    snapshot.dataset_version = payload.datasetVersion
    snapshot.sample_count = payload.sampleCount
    snapshot.train_count = payload.trainCount
    snapshot.validation_count = payload.validationCount
    snapshot.test_count = payload.testCount
    snapshot.source_status = payload.sourceStatus
    snapshot.license_status = payload.licenseStatus
    snapshot.quality_status = payload.qualityStatus
    snapshot.duplicate_status = payload.duplicateStatus
    snapshot.leakage_risk = payload.leakageRisk
    snapshot.manual_review_status = payload.manualReviewStatus
    snapshot.version_status = payload.versionStatus
    snapshot.revoked = payload.revoked
    snapshot.revoked_at = utc_iso() if payload.revoked else ""
    snapshot.snapshot_source = payload.snapshotSource
    snapshot.note = payload.note
    snapshot.snapshot_created_at = utc_iso()
    snapshot.created_by = payload.createdBy
    snapshot.training_eligibility = evaluate_snapshot(snapshot)["eligible"]
    session.add(snapshot)
    session.commit()
    return snapshot_to_check(snapshot) | {"created": created}


def revoke_snapshot(session: Session, dataset_version_id: str, reason: str, operator: str) -> dict[str, Any]:
    snapshot = find_snapshot(session, dataset_version_id)
    snapshot.revoked = True
    snapshot.revoked_at = utc_iso()
    snapshot.version_status = "draft"
    snapshot.training_eligibility = False
    snapshot.note = (snapshot.note + f"｜已被 {operator} 撤销：{reason}") if snapshot.note else f"已被 {operator} 撤销：{reason}"
    session.commit()
    return snapshot_to_check(snapshot)


def list_training_checks(session: Session, dataset_id: str) -> list[dict[str, Any]]:
    snapshots = (
        session.execute(
            select(DatasetVersionSnapshot)
            .where(DatasetVersionSnapshot.dataset_id == dataset_id)
            .order_by(DatasetVersionSnapshot.dataset_version.asc())
        )
        .scalars()
        .all()
    )
    if not snapshots:
        raise ApiError(404, "DATASET_NOT_FOUND", "服务端没有该数据集版本的快照")
    return [snapshot_to_check(item) for item in snapshots]


def get_training_check(session: Session, dataset_id: str, version_id: str) -> dict[str, Any]:
    snapshot = find_snapshot(session, version_id)
    if dataset_id and snapshot.dataset_id != dataset_id:
        raise ApiError(404, "DATASET_VERSION_NOT_FOUND", "数据集版本与数据集不一致")
    return snapshot_to_check(snapshot)


# ===== 任务读写 =====


def next_task_id(session: Session) -> str:
    year = datetime.now().year
    prefix = f"T-{year}-"
    ids = (
        session.execute(
            select(TrainingTask.task_id).where(TrainingTask.task_id.like(f"{prefix}%"))
        )
        .scalars()
        .all()
    )
    max_seq = 0
    for task_id in ids:
        try:
            max_seq = max(max_seq, int(task_id.rsplit("-", 1)[1]))
        except (IndexError, ValueError):
            continue
    return f"{prefix}{max_seq + 1:03d}"


def _model_config(model_type: str, task_type: str) -> dict[str, Any]:
    model = MODEL_TYPES[model_type]
    return {
        "taskType": task_type,
        "framework": "pytorch",
        "modelFamily": model["modelFamily"],
        "inputModalities": model["inputModalities"],
        "implementationStatus": "not_implemented",
        "note": "本轮仅执行 CPU Smoke Test，不使用真实反诈模型结构。",
    }


def _initial_log(session: Session, task_id: str, message: str, level: str = "INFO") -> None:
    session.add(
        TrainingLog(
            log_id=f"LOG-{task_id}-{uuid.uuid4().hex[:12]}",
            task_id=task_id,
            timestamp=utc_iso(),
            level=level,
            message=message,
            source="api",
        )
    )


def create_task(
    session: Session, payload: CreateTrainingTaskIn, created_by: str = "演示管理员"
) -> dict[str, Any]:
    validate_identity(payload)
    validate_parameters(payload.trainingParameters)
    snapshot = ensure_snapshot_trainable(session, payload.datasetId, payload.datasetVersionId)

    task_id = next_task_id(session)
    created_at = utc_iso()
    parameters = payload.trainingParameters.model_dump()
    split = {
        "trainCount": snapshot.train_count,
        "validationCount": snapshot.validation_count,
        "testCount": snapshot.test_count,
    }
    dataset_snapshot = {
        "datasetId": snapshot.dataset_id,
        "datasetName": snapshot.dataset_name,
        "datasetVersionId": snapshot.dataset_version_id,
        "datasetVersion": snapshot.dataset_version,
        "sampleCount": snapshot.sample_count,
        "trainCount": snapshot.train_count,
        "validationCount": snapshot.validation_count,
        "testCount": snapshot.test_count,
        "sourceStatus": snapshot.source_status,
        "licenseStatus": snapshot.license_status,
        "qualityStatus": snapshot.quality_status,
        "duplicateStatus": snapshot.duplicate_status,
        "leakageRisk": snapshot.leakage_risk,
        "manualReviewStatus": snapshot.manual_review_status,
        "versionStatus": snapshot.version_status,
        "snapshotCreatedAt": snapshot.snapshot_created_at,
    }

    task = TrainingTask(
        task_id=task_id,
        task_name=payload.taskName.strip(),
        task_description=payload.taskDescription.strip(),
        model_type=payload.modelType,
        task_type=payload.taskType,
        model_config_json=json.dumps(_model_config(payload.modelType, payload.taskType), ensure_ascii=False),
        dataset_id=snapshot.dataset_id,
        dataset_name=snapshot.dataset_name,
        dataset_version_id=snapshot.dataset_version_id,
        dataset_version=snapshot.dataset_version,
        dataset_split_json=json.dumps(split, ensure_ascii=False),
        dataset_snapshot_json=json.dumps(dataset_snapshot, ensure_ascii=False),
        parameters_json=json.dumps(parameters, ensure_ascii=False),
        status="PENDING",
        execution_mode="worker",
        training_profile="smoke_test",
        created_at=created_at,
        created_by=created_by,
        attempt=1,
    )
    session.add(task)
    session.flush()
    _initial_log(
        session,
        task_id,
        "训练任务已创建，已通过服务端数据集资格校验，等待独立训练执行器领取。",
    )
    session.commit()
    return task_to_dict(task, 0)


def _max_epoch(session: Session, task_id: str) -> int:
    value = session.execute(
        select(func.max(TrainingMetricRow.epoch)).where(TrainingMetricRow.task_id == task_id)
    ).scalar()
    return int(value or 0)


def get_task_or_404(session: Session, task_id: str) -> TrainingTask:
    task = session.get(TrainingTask, task_id)
    if task is None:
        raise ApiError(404, "TRAINING_TASK_NOT_FOUND", "未找到该训练任务")
    return task


def get_task_detail(session: Session, task_id: str) -> dict[str, Any]:
    expire_stale_tasks(session)
    task = get_task_or_404(session, task_id)
    return task_to_dict(task, _max_epoch(session, task_id))


def stats(session: Session) -> dict[str, int]:
    expire_stale_tasks(session)
    rows = session.execute(select(TrainingTask.status, func.count()).group_by(TrainingTask.status)).all()
    result = {
        "total": 0,
        "pending": 0,
        "running": 0,
        "succeeded": 0,
        "failed": 0,
        "canceled": 0,
    }
    for status, count in rows:
        key = status.lower()
        if key in result:
            result[key] = int(count)
        result["total"] += int(count)
    return result


def list_tasks(session: Session, query: dict[str, Any]) -> dict[str, Any]:
    expire_stale_tasks(session)
    stmt = select(TrainingTask).order_by(TrainingTask.created_at.desc())
    keyword = str(query.get("keyword") or "").strip()
    if keyword:
        pattern = f"%{keyword}%"
        stmt = stmt.where(
            (TrainingTask.task_id.like(pattern)) | (TrainingTask.task_name.like(pattern))
        )
    if query.get("modelType"):
        stmt = stmt.where(TrainingTask.model_type == query["modelType"])
    if query.get("datasetId"):
        stmt = stmt.where(TrainingTask.dataset_id == query["datasetId"])
    if query.get("taskStatus"):
        stmt = stmt.where(TrainingTask.status == str(query["taskStatus"]).upper())
    if query.get("executionMode"):
        stmt = stmt.where(TrainingTask.execution_mode == query["executionMode"])
    tasks = session.execute(stmt).scalars().all()

    created_from = str(query.get("createdFrom") or "")
    created_to = str(query.get("createdTo") or "")
    if created_from or created_to:
        filtered = []
        for task in tasks:
            day = display_time(task.created_at)[:10]
            if created_from and day < created_from:
                continue
            if created_to and day > created_to:
                continue
            filtered.append(task)
        tasks = filtered

    page = max(1, int(query.get("page") or 1))
    page_size = min(100, max(1, int(query.get("pageSize") or 20)))
    start = (page - 1) * page_size
    items = [task_to_dict(task, _max_epoch(session, task.task_id)) for task in tasks[start : start + page_size]]
    return {"items": items, "page": page, "pageSize": page_size, "total": len(tasks)}


def options(session: Session) -> dict[str, Any]:
    snapshots = session.execute(
        select(DatasetVersionSnapshot).order_by(DatasetVersionSnapshot.dataset_id.asc())
    ).scalars().all()

    grouped: dict[str, list[DatasetVersionSnapshot]] = {}
    for snapshot in snapshots:
        grouped.setdefault(snapshot.dataset_id, []).append(snapshot)

    datasets = []
    for dataset_id, items in grouped.items():
        first = items[0]
        versions = [
            {
                "versionId": item.dataset_version_id,
                "datasetId": item.dataset_id,
                "versionNumber": item.dataset_version,
                "sampleCount": item.sample_count,
                "trainCount": item.train_count,
                "validationCount": item.validation_count,
                "testCount": item.test_count,
                "versionDescription": item.note,
                "createdAt": display_time(item.snapshot_created_at),
                "createdBy": item.created_by,
                "versionStatus": item.version_status,
                "changeLog": [],
                "leakageRisk": item.leakage_risk,
                "manualReviewStatus": item.manual_review_status,
            }
            for item in items
        ]
        datasets.append(
            {
                "datasetId": dataset_id,
                "datasetName": first.dataset_name,
                "datasetType": first.dataset_type,
                "datasetStatus": "ready"
                if any(item.version_status == "published" for item in items)
                else "reviewing",
                "versions": versions,
            }
        )

    return {
        "modelTypes": [
            {
                "value": key,
                "label": value["label"],
                "description": "仅提供训练配置项；本轮 Worker 执行 CPU Smoke Test。",
                "taskTypes": value["taskTypes"],
                "inputModalities": value["inputModalities"],
                "implementationStatus": "planned",
                "implementationNote": "真实反诈模型尚未实现，本轮只验证训练链路。",
            }
            for key, value in MODEL_TYPES.items()
        ],
        "optimizers": OPTIMIZERS,
        "devices": DEVICES,
        "statuses": STATUSES,
        "executionModes": EXECUTION_MODES,
        "taskTypes": TASK_TYPES,
        "datasets": datasets,
    }


def logs(session: Session, task_id: str, query: dict[str, Any]) -> dict[str, Any]:
    task = get_task_or_404(session, task_id)
    stmt = select(TrainingLog).where(TrainingLog.task_id == task_id)
    level = str(query.get("level") or "").upper()
    if level:
        stmt = stmt.where(TrainingLog.level == level)
    keyword = str(query.get("keyword") or "").strip()
    if keyword:
        stmt = stmt.where(TrainingLog.message.like(f"%{keyword}%"))
    stmt = stmt.order_by(TrainingLog.timestamp.asc(), TrainingLog.log_id.asc())
    rows = session.execute(stmt).scalars().all()

    page = max(1, int(query.get("page") or 1))
    page_size = min(100, max(1, int(query.get("pageSize") or 20)))
    start = (page - 1) * page_size

    items = [
        {
            "logId": row.log_id,
            "taskId": row.task_id,
            "timestamp": display_time(row.timestamp),
            "level": row.level,
            "message": row.message,
            "source": row.source,
        }
        for row in rows[start : start + page_size]
    ]
    return {
        "items": items,
        "page": page,
        "pageSize": page_size,
        "total": len(rows),
        "executionMode": task.execution_mode,
        "notice": "日志来自真实训练执行器上报（CPU Smoke Test）。"
        if task.execution_mode == "worker"
        else "当前为演示日志，尚未连接真实训练执行器。",
    }


def metrics(session: Session, task_id: str) -> dict[str, Any]:
    task = get_task_or_404(session, task_id)
    rows = session.execute(
        select(TrainingMetricRow)
        .where(TrainingMetricRow.task_id == task_id)
        .order_by(TrainingMetricRow.epoch.asc())
    ).scalars().all()
    return {
        "taskId": task_id,
        "source": "worker" if task.execution_mode == "worker" else "mock",
        "metrics": [
            {
                "epoch": row.epoch,
                "trainLoss": row.train_loss,
                "validationLoss": row.validation_loss,
                "trainAccuracy": row.train_accuracy,
                "validationAccuracy": row.validation_accuracy,
                "validationPrecision": row.validation_precision,
                "validationRecall": row.validation_recall,
                "validationF1": row.validation_f1,
                "recordedAt": display_time(row.recorded_at),
            }
            for row in rows
        ],
        "notice": "指标来自真实训练执行器上报（CPU Smoke Test，不代表反诈模型能力）。",
    }


def result(session: Session, task_id: str) -> dict[str, Any] | None:
    task = get_task_or_404(session, task_id)
    if task.status != "SUCCEEDED":
        return None
    row = session.get(TrainingResultRow, task_id)
    if row is None:
        return None
    return result_to_dict(row)


def cancel_task(session: Session, task_id: str) -> dict[str, Any]:
    task = get_task_or_404(session, task_id)
    now = utc_iso()
    if task.status == "PENDING":
        task.status = "CANCELED"
        task.completed_at = now
        task.last_event_at = now
        _initial_log(session, task_id, "任务在等待领取阶段被取消，执行器无需停止任何进程。", "WARNING")
        session.commit()
        return task_to_dict(task, 0)
    if task.status == "RUNNING":
        if not task.cancel_requested_at:
            task.cancel_requested_at = now
            task.last_event_at = now
            _initial_log(
                session,
                task_id,
                "已向训练执行器发送取消请求，等待执行器安全停止并上报 CANCELED。",
                "WARNING",
            )
            session.commit()
        return task_to_dict(task, _max_epoch(session, task_id))
    raise ApiError(409, "TASK_STATE_INVALID", "当前状态不允许取消")


def retry_task(session: Session, task_id: str, created_by: str | None = None) -> dict[str, Any]:
    original = get_task_or_404(session, task_id)
    if original.status != "FAILED":
        raise ApiError(409, "TASK_STATE_INVALID", "仅失败任务允许重试")
    if original.retried_by_task_id:
        raise ApiError(
            409,
            "TASK_ALREADY_RETRIED",
            f"该任务已重试并生成 {original.retried_by_task_id}，请勿重复重试",
        )

    snapshot = ensure_snapshot_trainable(session, original.dataset_id, original.dataset_version_id)

    new_id = next_task_id(session)
    now = utc_iso()
    parameters = json.loads(original.parameters_json or "{}")
    split = json.loads(original.dataset_split_json or "{}")
    dataset_snapshot = json.loads(original.dataset_snapshot_json or "{}")

    task = TrainingTask(
        task_id=new_id,
        task_name=original.task_name,
        task_description=original.task_description,
        model_type=original.model_type,
        task_type=original.task_type,
        model_config_json=original.model_config_json,
        dataset_id=original.dataset_id,
        dataset_name=snapshot.dataset_name,
        dataset_version_id=original.dataset_version_id,
        dataset_version=original.dataset_version,
        dataset_split_json=json.dumps(split, ensure_ascii=False),
        dataset_snapshot_json=json.dumps(dataset_snapshot, ensure_ascii=False),
        parameters_json=json.dumps(parameters, ensure_ascii=False),
        status="PENDING",
        execution_mode="worker",
        training_profile="smoke_test",
        created_at=now,
        created_by=created_by or original.created_by,
        retry_of_task_id=original.task_id,
        attempt=original.attempt + 1,
    )
    session.add(task)
    original.retried_by_task_id = new_id
    session.flush()
    _initial_log(
        session,
        new_id,
        f"由失败任务 {original.task_id} 重试创建，已重新通过数据集资格校验。",
    )
    session.commit()
    return task_to_dict(task, 0)
