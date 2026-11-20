"""SQLite 单 Worker 队列适配器。

SQLite 不支持 `SELECT ... FOR UPDATE SKIP LOCKED`，因此这里采用：

1. 在事务内挑选最早创建的 PENDING 任务；
2. 用「条件更新」`WHERE task_id=? AND status='PENDING'` 原子抢占，
   只有 rowcount = 1 才算领取成功（并发下不会重复领取）；
3. 领取成功立即写入 workerId / heartbeatAt / leaseExpiresAt。

后续迁移 PostgreSQL / Redis 时，只需要替换本模块的 claim_next 实现，
上层服务与 Worker 协议保持不变。
"""

from __future__ import annotations

import uuid

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from ..core.time import lease_deadline, now_utc, utc_iso
from ..models import TrainingLog, TrainingTask

STALE_ERROR_MESSAGE = (
    "任务租约超时：Worker 在租约到期前未续约，可能异常退出。"
    "为避免重复写入模型产物，本任务已标记失败，需人工确认后重试。"
)


def claim_next(session: Session, worker_id: str, lease_seconds: int) -> TrainingTask | None:
    """原子领取一个 PENDING 任务；没有可领取任务时返回 None。"""
    max_attempts = 5
    for _ in range(max_attempts):
        candidate = session.execute(
            select(TrainingTask.task_id)
            .where(TrainingTask.status == "PENDING")
            .order_by(TrainingTask.created_at.asc())
            .limit(1)
        ).scalar_one_or_none()
        if candidate is None:
            return None

        now = utc_iso(now_utc())
        result = session.execute(
            update(TrainingTask)
            .where(
                TrainingTask.task_id == candidate,
                TrainingTask.status == "PENDING",
            )
            .values(
                status="RUNNING",
                worker_id=worker_id,
                started_at=now,
                heartbeat_at=now,
                lease_expires_at=lease_deadline(lease_seconds),
                last_event_at=now,
            )
        )
        session.commit()
        if result.rowcount == 1:
            session.add(
                TrainingLog(
                    log_id=f"LOG-{candidate}-CLAIM-{uuid.uuid4().hex[:10]}",
                    task_id=candidate,
                    timestamp=now,
                    level="INFO",
                    message=f"任务已被训练执行器 {worker_id} 领取，开始准备训练环境",
                    source="api",
                )
            )
            session.commit()
            return session.get(TrainingTask, candidate)
    return None


def expire_stale_tasks(session: Session, reference: str | None = None) -> list[str]:
    """把租约超时的 RUNNING 任务标记为 FAILED。

    不自动重跑：原 Worker 是否已经产出文件无法确定，盲目重跑可能重复写入产物。
    """
    reference = reference or utc_iso()
    stale_ids = (
        session.execute(
            select(TrainingTask.task_id).where(
                TrainingTask.status == "RUNNING",
                TrainingTask.lease_expires_at != "",
                TrainingTask.lease_expires_at < reference,
            )
        )
        .scalars()
        .all()
    )
    for task_id in stale_ids:
        now = utc_iso()
        session.execute(
            update(TrainingTask)
            .where(TrainingTask.task_id == task_id, TrainingTask.status == "RUNNING")
            .values(
                status="FAILED",
                error_message=STALE_ERROR_MESSAGE,
                completed_at=now,
                last_event_at=now,
            )
        )
        session.add(
            TrainingLog(
                log_id=f"LOG-{task_id}-STALE-{uuid.uuid4().hex[:10]}",
                task_id=task_id,
                timestamp=now,
                level="ERROR",
                message=STALE_ERROR_MESSAGE,
                source="api",
            )
        )
    if stale_ids:
        session.commit()
    return stale_ids


def renew_lease(session: Session, task_id: str, worker_id: str, lease_seconds: int) -> TrainingTask | None:
    """Worker 心跳续约；Worker 不匹配时拒绝，避免抢占他人任务。"""
    task = session.get(TrainingTask, task_id)
    if task is None or task.worker_id != worker_id or task.status != "RUNNING":
        return None
    now = utc_iso()
    task.heartbeat_at = now
    task.lease_expires_at = lease_deadline(lease_seconds)
    session.commit()
    return task
