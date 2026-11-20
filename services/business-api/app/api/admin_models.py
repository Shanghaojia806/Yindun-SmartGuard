"""P12 模型仓库管理端接口。

所有发布资格、文件哈希、签名与数据集资格均由服务端重新校验；
前端提交的 eligible / 校验状态一律忽略。
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from ..core.config import settings
from ..db import get_db
from ..schemas.common import envelope
from ..schemas.model import (
    CompatibilityIn,
    ImportModelIn,
    PublishModelIn,
    RejectModelIn,
    ReviewActionIn,
    RollbackModelIn,
    WithdrawModelIn,
)
from ..services import model_repository as repo
from .deps import require_admin

router = APIRouter(prefix="/admin", tags=["admin-models"], dependencies=[Depends(require_admin)])


@router.get("/models/stats")
def model_stats(session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.get_stats(session))


@router.get("/models/options")
def model_options(session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.get_options(session))


@router.get("/models")
def list_models(
    keyword: str = "",
    modelType: str = "",
    framework: str = "",
    releaseStatus: str = "",
    compatibilityStatus: str = "",
    publishedFrom: str = "",
    publishedTo: str = "",
    page: int = Query(1, ge=1),
    pageSize: int = Query(20, ge=1, le=100),
    session: Session = Depends(get_db),
) -> dict[str, Any]:
    return envelope(
        repo.list_models(
            session,
            {
                "keyword": keyword,
                "modelType": modelType,
                "framework": framework,
                "releaseStatus": releaseStatus,
                "compatibilityStatus": compatibilityStatus,
                "publishedFrom": publishedFrom,
                "publishedTo": publishedTo,
                "page": page,
                "pageSize": pageSize,
            },
        )
    )


@router.post("/model-versions/import")
def import_model_version(
    payload: ImportModelIn, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.import_from_training_task(session, payload))


@router.get("/model-versions/compare")
def compare_model_versions(
    baseVersionId: str, targetVersionId: str, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.compare_versions(session, baseVersionId, targetVersionId))


@router.get("/model-artifacts")
def list_artifacts(modelId: str, session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.list_artifacts(session, modelId))


@router.get("/model-artifacts/{artifact_id}")
def get_artifact(artifact_id: str, session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.get_artifact(session, artifact_id))


@router.get("/models/{model_id}")
def get_model(model_id: str, session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.get_model(session, model_id))


@router.get("/models/{model_id}/versions")
def list_versions(
    model_id: str,
    keyword: str = "",
    releaseStatus: str = "",
    compatibilityStatus: str = "",
    page: int = Query(1, ge=1),
    pageSize: int = Query(20, ge=1, le=100),
    session: Session = Depends(get_db),
) -> dict[str, Any]:
    return envelope(
        repo.list_versions(
            session,
            model_id,
            {
                "keyword": keyword,
                "releaseStatus": releaseStatus,
                "compatibilityStatus": compatibilityStatus,
                "page": page,
                "pageSize": pageSize,
            },
        )
    )


@router.get("/models/{model_id}/versions/{version_id}")
def get_version(
    model_id: str, version_id: str, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.get_version(session, model_id, version_id))


@router.get("/model-versions/{version_id}/dataset-snapshot")
def version_dataset_snapshot(version_id: str, session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.get_dataset_snapshot(session, version_id))


@router.get("/models/{model_id}/releases")
def list_model_releases(model_id: str, session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.list_releases(session, model_id=model_id))


@router.get("/model-versions/{version_id}/releases")
def list_version_releases(version_id: str, session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.list_releases(session, version_id=version_id))


@router.get("/models/{model_id}/audits")
def list_model_audits(model_id: str, session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.list_audits(session, model_id=model_id))


@router.get("/model-versions/{version_id}/audits")
def list_version_audits(version_id: str, session: Session = Depends(get_db)) -> dict[str, Any]:
    return envelope(repo.list_audits(session, version_id=version_id))


@router.get("/model-versions/{version_id}/review-check")
def review_check(
    version_id: str,
    channel: str = "internal_test",
    roleLabel: str = "系统管理员（模拟）",
    session: Session = Depends(get_db),
) -> dict[str, Any]:
    version = repo._get_version(session, version_id)  # noqa: SLF001
    return envelope(repo.evaluate_review(session, version, channel, roleLabel))


@router.post("/model-versions/{version_id}/submit-review")
def submit_review(
    version_id: str, payload: ReviewActionIn, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.submit_review(session, version_id, payload))


@router.post("/model-versions/{version_id}/approve")
def approve_review(
    version_id: str, payload: ReviewActionIn, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.approve_review(session, version_id, payload))


@router.post("/model-versions/{version_id}/reject")
def reject_review(
    version_id: str, payload: RejectModelIn, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.reject_review(session, version_id, payload))


@router.post("/model-versions/{version_id}/publish")
def publish_version(
    version_id: str, payload: PublishModelIn, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.publish_version(session, version_id, payload))


@router.post("/model-versions/{version_id}/withdraw")
def withdraw_version(
    version_id: str, payload: WithdrawModelIn, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.withdraw_version(session, version_id, payload))


@router.post("/model-versions/{version_id}/compatibility-tests")
def record_compatibility(
    version_id: str, payload: CompatibilityIn, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.record_compatibility(session, version_id, payload))


@router.post("/models/{model_id}/rollback")
def rollback_model(
    model_id: str, payload: RollbackModelIn, session: Session = Depends(get_db)
) -> dict[str, Any]:
    return envelope(repo.rollback_model(session, model_id, payload))


# 便于排查：返回当前 Ed25519 公钥配置（不包含私钥）
@router.get("/model-verification-config")
def verification_config() -> dict[str, Any]:
    return envelope(
        {
            "publicKeyPath": settings.ed25519_public_key_path
            or "ml/training/keys/dev_ed25519_public.pem",
            "roles": repo.allowed_roles(),
        }
    )
