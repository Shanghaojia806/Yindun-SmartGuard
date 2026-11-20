"""管理端数据集快照与资格校验接口。"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..db import get_db
from ..schemas.common import envelope
from ..schemas.training import RegisterSnapshotIn, RevokeSnapshotIn
from ..services import training_service
from .deps import require_admin

router = APIRouter(
    prefix="/admin",
    tags=["admin-datasets"],
    dependencies=[Depends(require_admin)],
)


@router.get("/datasets/{dataset_id}/training-checks")
def list_checks(dataset_id: str, db: Session = Depends(get_db)) -> dict:
    return envelope(training_service.list_training_checks(db, dataset_id))


@router.get("/dataset-versions/{version_id}/training-check")
def get_check(version_id: str, datasetId: str = "", db: Session = Depends(get_db)) -> dict:
    return envelope(training_service.get_training_check(db, datasetId, version_id))


@router.post("/dataset-snapshots", status_code=201)
def register_snapshot(payload: RegisterSnapshotIn, db: Session = Depends(get_db)) -> dict:
    return envelope(training_service.register_snapshot(db, payload))


@router.post("/dataset-versions/{version_id}/revoke")
def revoke_snapshot(
    version_id: str, payload: RevokeSnapshotIn, db: Session = Depends(get_db)
) -> dict:
    return envelope(
        training_service.revoke_snapshot(db, version_id, payload.reason, payload.operator)
    )
