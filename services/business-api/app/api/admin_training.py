"""管理端训练任务接口（严格对齐 P11_TRAINING_CONTRACT.md 3.1–3.11）。"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from ..db import get_db
from ..schemas.common import envelope
from ..schemas.training import CreateTrainingTaskIn
from ..services import training_service
from .deps import operator_name, require_admin

router = APIRouter(
    prefix="/admin",
    tags=["admin-training"],
    dependencies=[Depends(require_admin)],
)


@router.get("/training-tasks/stats")
def get_stats(db: Session = Depends(get_db)) -> dict:
    return envelope(training_service.stats(db))


@router.get("/training-tasks/options")
def get_options(db: Session = Depends(get_db)) -> dict:
    return envelope(training_service.options(db))


@router.get("/training-tasks")
def list_tasks(
    keyword: str = "",
    modelType: str = "",
    datasetId: str = "",
    taskStatus: str = "",
    executionMode: str = "",
    createdFrom: str = "",
    createdTo: str = "",
    page: int = Query(default=1, ge=1),
    pageSize: int = Query(default=20, ge=1, le=100),
    db: Session = Depends(get_db),
) -> dict:
    query = {
        "keyword": keyword,
        "modelType": modelType,
        "datasetId": datasetId,
        "taskStatus": taskStatus,
        "executionMode": executionMode,
        "createdFrom": createdFrom,
        "createdTo": createdTo,
        "page": page,
        "pageSize": pageSize,
    }
    return envelope(training_service.list_tasks(db, query))


@router.post("/training-tasks", status_code=201)
def create_task(
    payload: CreateTrainingTaskIn,
    created_by: str = Depends(operator_name),
    db: Session = Depends(get_db),
) -> dict:
    return envelope(training_service.create_task(db, payload, created_by))


@router.get("/training-tasks/{task_id}")
def get_task(task_id: str, db: Session = Depends(get_db)) -> dict:
    return envelope(training_service.get_task_detail(db, task_id))


@router.get("/training-tasks/{task_id}/logs")
def get_logs(
    task_id: str,
    level: str = "",
    keyword: str = "",
    page: int = Query(default=1, ge=1),
    pageSize: int = Query(default=50, ge=1, le=100),
    db: Session = Depends(get_db),
) -> dict:
    return envelope(
        training_service.logs(
            db, task_id, {"level": level, "keyword": keyword, "page": page, "pageSize": pageSize}
        )
    )


@router.get("/training-tasks/{task_id}/metrics")
def get_metrics(task_id: str, db: Session = Depends(get_db)) -> dict:
    return envelope(training_service.metrics(db, task_id))


@router.get("/training-tasks/{task_id}/result")
def get_result(task_id: str, db: Session = Depends(get_db)) -> dict:
    return envelope(training_service.result(db, task_id))


@router.post("/training-tasks/{task_id}/retry", status_code=201)
def retry_task(
    task_id: str,
    created_by: str = Depends(operator_name),
    db: Session = Depends(get_db),
) -> dict:
    return envelope(training_service.retry_task(db, task_id, created_by))


@router.post("/training-tasks/{task_id}/cancel")
def cancel_task(task_id: str, db: Session = Depends(get_db)) -> dict:
    return envelope(training_service.cancel_task(db, task_id))
