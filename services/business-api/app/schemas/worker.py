"""Worker 事件请求模型。"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

WORKER_EVENT_TYPES = {
    "task.started",
    "task.log",
    "task.metric",
    "task.checkpoint",
    "task.succeeded",
    "task.failed",
    "task.canceled",
}


class WorkerEventIn(BaseModel):
    eventId: str
    eventType: str
    timestamp: str = ""
    workerId: str = ""
    payload: dict[str, Any] = Field(default_factory=dict)
