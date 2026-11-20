"""内部 Worker 接口：领取任务、心跳续约、获取上下文、事件上报。

与对外管理端接口完全分离；仅接受 X-Worker-Token 鉴权。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..core.errors import ApiError
from ..core.time import utc_iso
from ..db import get_db
from ..models import TrainingTask
from ..repositories.queue import claim_next, renew_lease
from ..schemas.common import envelope
from ..schemas.training import WorkerClaimIn, WorkerHeartbeatIn
from ..schemas.worker import WorkerEventIn
from ..services import worker_events
from ..services.dataset_eligibility import evaluate_snapshot
from ..models import DatasetVersionSnapshot
from .deps import require_worker_token

router = APIRouter(
    prefix="/internal/worker",
    tags=["internal-worker"],
    dependencies=[Depends(require_worker_token)],
)


@router.post("/tasks/claim")
def claim(payload: WorkerClaimIn, db: Session = Depends(get_db)) -> dict:
    from ..repositories.queue import expire_stale_tasks

    expire_stale_tasks(db)
    task = claim_next(db, payload.workerId, max(30, payload.leaseSeconds))
    if task is None:
        return envelope(None)
    return envelope(worker_events.worker_task_context(db, task))


@router.get("/tasks/{task_id}/context")
def context(task_id: str, db: Session = Depends(get_db)) -> dict:
    task = db.get(TrainingTask, task_id)
    if task is None:
        raise ApiError(404, "TRAINING_TASK_NOT_FOUND", "未找到该训练任务")
    payload = worker_events.worker_task_context(db, task)
    snapshot = db.query(DatasetVersionSnapshot).filter_by(dataset_version_id=task.dataset_version_id).one_or_none()
    if snapshot is not None:
        payload["eligibility"] = evaluate_snapshot(snapshot)
    return envelope(payload)


@router.post("/tasks/{task_id}/heartbeat")
def heartbeat(task_id: str, payload: WorkerHeartbeatIn, db: Session = Depends(get_db)) -> dict:
    task = renew_lease(db, task_id, payload.workerId, max(30, payload.leaseSeconds))
    if task is None:
        raise ApiError(409, "LEASE_LOST", "任务租约已失效或 Worker 不匹配，请停止训练")
    return envelope(
        {
            "taskId": task.task_id,
            "taskStatus": task.status.lower(),
            "cancelRequested": bool(task.cancel_requested_at),
            "heartbeatAt": utc_iso(),
            "leaseExpiresAt": task.lease_expires_at,
        }
    )


@router.post("/tasks/{task_id}/events")
def report_event(task_id: str, payload: WorkerEventIn, db: Session = Depends(get_db)) -> dict:
    return envelope(worker_events.handle_event(db, task_id, payload))
