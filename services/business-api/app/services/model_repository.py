"""P12 模型仓库服务：真实训练产物导入、服务端二次校验、发布状态机与审计。

设计原则：
- 模型版本必须关联 P11-R 真实 TrainingTask；
- 文件哈希、Ed25519 签名、数据集资格均由服务端重新计算，不信任前端；
- Smoke Test / 合成数据 / 开发密钥 / 无真实测试集评估，在灰度与正式渠道永久阻断；
- 发布、撤回、回滚只追加记录，不覆盖历史。
"""

from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..core.config import REPO_ROOT, settings
from ..core.errors import ApiError
from ..core.time import display_time, utc_iso
from ..models import DatasetVersionSnapshot, TrainingResultRow, TrainingTask
from ..models.model_repo import (
    ModelArtifactRecord,
    ModelAuditLogRecord,
    ModelCompatibilityRecord,
    ModelEvaluationReportRecord,
    ModelRecord,
    ModelReleaseRecord,
    ModelVersionRecord,
)
from .artifact_verify import load_manifest, resolve_repo_path, verify_file, verify_signature
from .dataset_eligibility import snapshot_to_check

FORMAL_CHANNELS = {"canary", "production"}
CHANNEL_LABELS = {
    "internal_test": "受限内部测试",
    "canary": "灰度发布",
    "production": "正式发布",
}
RELEASE_STATUS_LABELS = {
    "draft": "草稿",
    "pending_review": "待审核",
    "approved": "审核通过",
    "rejected": "审核拒绝",
    "published": "已发布",
    "withdrawn": "已撤回",
    "rolled_back": "已回滚",
}
AUDIT_LABELS = {
    "create_version": "导入/创建版本",
    "submit_review": "提交审核",
    "approve": "审核通过",
    "reject": "审核拒绝",
    "publish": "发布",
    "withdraw": "撤回",
    "rollback": "回滚",
    "hash_verify_failed": "文件校验失败",
    "signature_verify_failed": "签名校验失败",
    "compatibility_test_failed": "兼容性测试失败",
}
MODEL_TYPE_LABELS = {
    "text_risk_classifier": "文本反诈分类模型",
    "screen_multimodal_risk": "屏幕多模态风险模型",
    "audio_call_risk": "通话语音风险模型",
    "video_scam_risk": "短视频风险模型",
}
MODEL_FORMATS = [
    {"value": "pytorch_pt", "label": "PyTorch（.pt）"},
    {"value": "onnx", "label": "ONNX"},
    {"value": "tflite", "label": "TFLite"},
    {"value": "mnn", "label": "MNN"},
]
COMPAT_STATUSES = [
    {"value": "untested", "label": "未测试"},
    {"value": "testing", "label": "测试中"},
    {"value": "compatible", "label": "兼容"},
    {"value": "partially_compatible", "label": "部分兼容"},
    {"value": "incompatible", "label": "不兼容"},
    {"value": "test_failed", "label": "测试失败"},
]
QUANTIZATION_FORMATS = [
    {"value": "fp32", "label": "FP32"},
    {"value": "fp16", "label": "FP16"},
    {"value": "int8", "label": "INT8"},
    {"value": "int4_dynamic", "label": "INT4（动态）"},
]


# ===== 基础工具 =====


def _dt(value: datetime | None) -> str:
    if not value:
        return ""
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return display_time(utc_iso(value))


def _loads(raw: str | None, fallback: Any) -> Any:
    try:
        value = json.loads(raw) if raw else fallback
        return value if value is not None else fallback
    except (json.JSONDecodeError, TypeError):
        return fallback


def _now() -> datetime:
    return datetime.now(timezone.utc)


def allowed_roles() -> list[str]:
    return [item.strip() for item in settings.model_release_roles.split(",") if item.strip()]


def ensure_release_role(role_label: str | None) -> None:
    """服务端再次校验角色。前端按钮状态不构成授权。"""
    label = role_label or ""
    if not any(role and role in label for role in allowed_roles()):
        raise ApiError(403, "MODEL_PERMISSION_DENIED", "当前角色无模型审核 / 发布权限")


def _audit(
    session: Session,
    *,
    model_id: str,
    model_version_id: str,
    operator: str,
    operation: str,
    target_type: str,
    target_id: str,
    target_label: str,
    result: str = "success",
    notes: str = "",
) -> None:
    session.add(
        ModelAuditLogRecord(
            audit_id=f"AU-{uuid.uuid4().hex[:12]}",
            model_id=model_id,
            model_version_id=model_version_id,
            operator=operator or "system",
            operation=operation,
            operation_label=AUDIT_LABELS.get(operation, operation),
            target_type=target_type,
            target_id=target_id,
            target_label=target_label,
            operated_at=_now(),
            result=result,
            notes=notes,
        )
    )


# ===== 序列化 =====


def artifact_to_dict(record: ModelArtifactRecord, model: ModelRecord | None) -> dict[str, Any]:
    return {
        "artifactId": record.artifact_id,
        "modelId": record.model_id,
        "modelName": model.model_name if model else "",
        "modelType": model.model_type if model else "",
        "framework": model.framework if model else "pytorch",
        "artifactPath": record.artifact_path,
        "artifactFormat": record.artifact_format,
        "fileSizeBytes": record.file_size_bytes,
        "fileHash": record.file_hash,
        "fileHashStatus": record.file_hash_status,
        "signature": record.signature,
        "signatureStatus": record.signature_status,
        "createdAt": _dt(record.created_at),
        "createdBy": record.created_by,
        "artifactSource": record.artifact_source,
        "isMock": record.artifact_source != "training_worker",
        # 扩展字段：便于区分开发密钥，不影响既有前端类型
        "signatureKeyLabel": record.signature_key_label,
        "signatureIsDevelopment": record.signature_is_development,
        "manifestPath": record.manifest_path,
        "lastVerifiedAt": _dt(record.last_verified_at),
    }


def compatibility_to_dict(record: ModelCompatibilityRecord) -> dict[str, Any]:
    return {
        "compatibilityId": record.compatibility_id,
        "platform": record.platform,
        "minOsVersion": record.min_os_version,
        "maxOsVersion": record.max_os_version,
        "architecture": record.architecture,
        "inferenceRuntime": record.inference_runtime,
        "runtimeVersion": record.runtime_version,
        "supportedDevices": _loads(record.devices_json, []),
        "memoryRequirementMb": record.memory_requirement_mb,
        "acceleratorSupport": _loads(record.accelerators_json, []),
        "compatibilityStatus": record.compatibility_status,
        "testedAt": _dt(record.tested_at),
        "testedBy": record.tested_by,
        "notes": record.notes,
        "isMock": not record.is_real_device_test,
    }


def _evaluation_summary(version: ModelVersionRecord) -> dict[str, Any]:
    return {
        "accuracy": version.eval_accuracy,
        "precision": version.eval_precision,
        "recall": version.eval_recall,
        "f1Score": version.eval_f1,
        "validationLoss": version.eval_validation_loss,
        "testDatasetVersion": version.eval_test_dataset_version,
        "evaluatedAt": _dt(version.eval_evaluated_at),
        "evaluationMethod": version.eval_method,
        "evaluationStatus": version.eval_status,
        "source": version.eval_source,
    }


def get_artifact_row(session: Session, version: ModelVersionRecord) -> ModelArtifactRecord | None:
    return session.scalar(
        select(ModelArtifactRecord)
        .where(ModelArtifactRecord.model_version_id == version.model_version_id)
        .order_by(ModelArtifactRecord.created_at.desc())
    )


def version_to_dict(session: Session, version: ModelVersionRecord) -> dict[str, Any]:
    model = session.get(ModelRecord, version.model_id)
    artifact = get_artifact_row(session, version)
    compat = session.scalars(
        select(ModelCompatibilityRecord)
        .where(ModelCompatibilityRecord.model_version_id == version.model_version_id)
        .order_by(ModelCompatibilityRecord.tested_at.desc())
    ).all()
    return {
        "modelVersionId": version.model_version_id,
        "modelId": version.model_id,
        "modelVersion": version.model_version,
        "datasetId": version.dataset_id,
        "datasetName": version.dataset_name,
        "datasetVersionId": version.dataset_version_id,
        "datasetVersion": version.dataset_version,
        "trainingTaskId": version.training_task_id,
        "artifactId": version.artifact_id,
        "modelFormat": version.model_format,
        "quantization": [],
        "runtimeCompatibility": [compatibility_to_dict(item) for item in compat],
        "evaluationSummary": _evaluation_summary(version),
        "releaseStatus": version.release_status,
        "createdAt": _dt(version.created_at),
        "createdBy": version.created_by,
        "reviewedAt": _dt(version.reviewed_at),
        "reviewedBy": version.reviewed_by,
        "publishedAt": _dt(version.published_at),
        "withdrawnAt": _dt(version.withdrawn_at),
        "description": version.description,
        "isMock": False,
        # 扩展字段：真实产物来源与受限原因
        "artifactSource": artifact.artifact_source if artifact else "",
        "trainingProfile": version.training_profile,
        "datasetType": version.dataset_type,
        "datasetOrigin": version.dataset_origin,
        "productionReady": version.production_ready,
        "publishBlockedReason": version.publish_blocked_reason,
    }


def _latest_version(session: Session, model_id: str) -> ModelVersionRecord | None:
    return session.scalar(
        select(ModelVersionRecord)
        .where(ModelVersionRecord.model_id == model_id)
        .order_by(ModelVersionRecord.created_at.desc())
    )


def _published_version(session: Session, model_id: str) -> ModelVersionRecord | None:
    return session.scalar(
        select(ModelVersionRecord)
        .where(
            ModelVersionRecord.model_id == model_id,
            ModelVersionRecord.release_status == "published",
        )
        .order_by(ModelVersionRecord.published_at.desc())
    )


def _model_compat_status(session: Session, version: ModelVersionRecord | None) -> str:
    if version is None:
        return "untested"
    rows = session.scalars(
        select(ModelCompatibilityRecord).where(
            ModelCompatibilityRecord.model_version_id == version.model_version_id
        )
    ).all()
    if not rows:
        return "untested"
    for status in ("test_failed", "incompatible", "partially_compatible", "testing"):
        if any(row.compatibility_status == status for row in rows):
            return status
    return "compatible"


def model_to_dict(session: Session, model: ModelRecord) -> dict[str, Any]:
    latest = _latest_version(session, model.model_id)
    published = _published_version(session, model.model_id)
    version_count = session.scalar(
        select(func.count())
        .select_from(ModelVersionRecord)
        .where(ModelVersionRecord.model_id == model.model_id)
    )
    artifact = get_artifact_row(session, latest) if latest else None
    task = session.get(TrainingTask, model.training_task_id) if model.training_task_id else None
    source_label = ""
    if task:
        source_label = f"训练任务 {task.task_id}（真实 Worker · {task.training_profile}）"
    return {
        "modelId": model.model_id,
        "modelName": model.model_name,
        "modelType": model.model_type,
        "framework": model.framework,
        "description": model.description,
        "latestVersion": latest.model_version if latest else "",
        "latestVersionId": latest.model_version_id if latest else "",
        "publishedVersion": published.model_version if published else "",
        "publishedVersionId": published.model_version_id if published else "",
        "trainingTaskId": model.training_task_id,
        "trainingSourceLabel": source_label,
        "releaseStatus": latest.release_status if latest else "draft",
        "compatibilityStatus": _model_compat_status(session, latest),
        "modelStatus": model.model_status,
        "updatedAt": _dt(latest.created_at) if latest else _dt(model.created_at),
        "versionCount": int(version_count or 0),
        "isMock": not (artifact and artifact.artifact_source == "training_worker"),
        # 扩展字段
        "trainingProfile": latest.training_profile if latest else "",
        "datasetOrigin": latest.dataset_origin if latest else "",
        "productionReady": latest.production_ready if latest else False,
    }


def release_to_dict(record: ModelReleaseRecord) -> dict[str, Any]:
    return {
        "releaseId": record.release_id,
        "modelId": record.model_id,
        "modelVersionId": record.model_version_id,
        "modelVersion": record.model_version,
        "releaseChannel": record.release_channel,
        "releaseStatus": record.release_status,
        "operator": record.operator,
        "reviewedBy": record.reviewed_by,
        "submittedAt": _dt(record.submitted_at),
        "approvedAt": _dt(record.approved_at),
        "publishedAt": _dt(record.published_at),
        "withdrawnAt": _dt(record.withdrawn_at),
        "rollbackFrom": record.rollback_from,
        "rollbackTo": record.rollback_to,
        "operationNotes": record.operation_notes,
        "isMock": record.is_simulated,
    }


def audit_to_dict(record: ModelAuditLogRecord) -> dict[str, Any]:
    return {
        "auditId": record.audit_id,
        "operator": record.operator,
        "operation": record.operation,
        "operationLabel": record.operation_label,
        "targetType": record.target_type,
        "targetId": record.target_id,
        "targetLabel": record.target_label,
        "operatedAt": _dt(record.operated_at),
        "result": record.result,
        "notes": record.notes,
        "isMock": False,
    }


# ===== 导入真实训练产物 =====


def _next_model_id(session: Session) -> str:
    count = session.scalar(select(func.count()).select_from(ModelRecord)) or 0
    return f"M-2026-{int(count) + 1:03d}"


def _next_version_id(session: Session, model_id: str) -> str:
    count = session.scalar(
        select(func.count())
        .select_from(ModelVersionRecord)
        .where(ModelVersionRecord.model_id == model_id)
    )
    suffix = model_id.split("-")[-1]
    return f"MV-2026-{suffix}-{int(count or 0) + 1:02d}"


def import_from_training_task(session: Session, payload: Any) -> dict[str, Any]:
    task_id = (payload.trainingTaskId or "").strip()
    if not task_id:
        raise ApiError(400, "PARAMETER_INVALID", "trainingTaskId 不能为空")

    task = session.get(TrainingTask, task_id)
    if task is None:
        raise ApiError(404, "TRAINING_TASK_NOT_FOUND", "训练任务不存在")

    existing = session.scalar(
        select(ModelVersionRecord).where(ModelVersionRecord.training_task_id == task_id)
    )
    if existing is not None:
        # 幂等：同一真实任务只导入一次
        return version_to_dict(session, existing)

    if task.status != "SUCCEEDED":
        raise ApiError(409, "MODEL_IMPORT_INVALID", "只有训练成功的任务才能导入模型仓库")
    if task.execution_mode != "worker":
        raise ApiError(409, "MODEL_IMPORT_INVALID", "模拟任务不能导入真实模型仓库")

    result = session.get(TrainingResultRow, task_id)
    if result is None or not result.manifest_path:
        raise ApiError(409, "MODEL_IMPORT_INVALID", "训练任务缺少产物清单，无法导入")

    manifest_path = resolve_repo_path(result.manifest_path)
    if not manifest_path.is_file():
        raise ApiError(409, "MODEL_IMPORT_INVALID", "产物清单文件不存在，无法导入")
    try:
        manifest = load_manifest(manifest_path)
    except Exception as exc:  # noqa: BLE001
        raise ApiError(409, "MODEL_IMPORT_INVALID", f"产物清单无法解析（{type(exc).__name__}）")

    if str(manifest.get("taskId") or "") != task_id:
        raise ApiError(409, "MODEL_IMPORT_INVALID", "产物清单中的 taskId 与训练任务不一致")

    file_check = verify_file(manifest, manifest_path)
    if not file_check["exists"]:
        raise ApiError(409, "MODEL_IMPORT_INVALID", "清单指向的模型文件不存在，无法导入")
    signature = verify_signature(manifest)

    snapshot = session.scalar(
        select(DatasetVersionSnapshot).where(
            DatasetVersionSnapshot.dataset_version_id == task.dataset_version_id
        )
    )
    dataset_type = snapshot.dataset_type if snapshot else "unknown"
    snapshot_source = snapshot.snapshot_source if snapshot else ""
    dataset_origin = (
        "synthetic"
        if snapshot_source in {"synthetic_demo", "synthetic"} or dataset_type == "synthetic"
        else "real"
    )
    training_profile = task.training_profile or str(manifest.get("trainingProfile") or "")

    model_name = (payload.modelName or "").strip() or task.task_name
    model = session.scalar(
        select(ModelRecord).where(
            ModelRecord.model_name == model_name, ModelRecord.model_type == task.model_type
        )
    )
    if model is None:
        model = ModelRecord(
            model_id=_next_model_id(session),
            model_name=model_name,
            model_type=task.model_type,
            framework="pytorch",
            description=payload.description
            or f"由训练任务 {task_id} 真实产物导入（{training_profile or 'unknown'}）",
            model_status="active",
            training_task_id=task_id,
            created_at=_now(),
            created_by=payload.operator or "system",
        )
        session.add(model)
        session.flush()

    version_id = _next_version_id(session, model.model_id)
    version_seq = session.scalar(
        select(func.count())
        .select_from(ModelVersionRecord)
        .where(ModelVersionRecord.model_id == model.model_id)
    )
    version_number = f"v0.{int(version_seq or 0) + 1}.0-smoke"

    artifact_id = str(manifest.get("artifactId") or f"ART-{task_id}")
    if session.get(ModelArtifactRecord, artifact_id) is not None:
        artifact_id = f"{artifact_id}-{version_id}"

    final_metrics = _loads(result.final_metrics_json, {})
    test_metrics = _loads(manifest.get("testMetrics"), {})
    eval_status = "demo" if (training_profile == "smoke_test" or dataset_origin == "synthetic") else "not_evaluated"

    def _pick(*keys: str) -> float | None:
        for key in keys:
            value = final_metrics.get(key)
            if isinstance(value, (int, float)):
                return float(value)
        return None

    accuracy = test_metrics.get("accuracy")
    f1_score = test_metrics.get("f1")
    if not isinstance(accuracy, (int, float)):
        accuracy = final_metrics.get("testAccuracy", final_metrics.get("validationAccuracy"))
    if not isinstance(f1_score, (int, float)):
        f1_score = final_metrics.get("testF1", final_metrics.get("validationF1"))

    blocked_parts: list[str] = []
    if training_profile == "smoke_test":
        blocked_parts.append("trainingProfile=smoke_test，仅限受限内部测试")
    if dataset_origin == "synthetic":
        blocked_parts.append("训练数据为合成/演示来源，禁止正式反诈发布")
    if str(manifest.get("artifactType") or "") == "smoke_test":
        blocked_parts.append("artifactType=smoke_test，仅用于基础设施验证")
    if file_check["fileHashStatus"] != "verified":
        blocked_parts.append(f"文件完整性校验未通过：{file_check['detail']}")
    if signature["signatureStatus"] != "verified":
        blocked_parts.append("模型未完成有效签名，禁止灰度/正式发布")
    if eval_status != "evaluated":
        blocked_parts.append("缺少真实测试集评估报告，禁止灰度/正式发布")

    version = ModelVersionRecord(
        model_version_id=version_id,
        model_id=model.model_id,
        model_version=version_number,
        dataset_id=task.dataset_id,
        dataset_name=snapshot.dataset_name if snapshot else task.dataset_name,
        dataset_version_id=task.dataset_version_id,
        dataset_version=task.dataset_version,
        training_task_id=task_id,
        artifact_id=artifact_id,
        model_format="pytorch_pt",
        training_profile=training_profile,
        dataset_type=dataset_type,
        dataset_origin=dataset_origin,
        eval_accuracy=float(accuracy) if isinstance(accuracy, (int, float)) else None,
        eval_precision=_pick("validationPrecision"),
        eval_recall=_pick("validationRecall"),
        eval_f1=float(f1_score) if isinstance(f1_score, (int, float)) else None,
        eval_validation_loss=_pick("validationLoss"),
        eval_status=eval_status,
        eval_source="training_task",
        eval_method=(
            "CPU Smoke Test 合成数据指标，不代表真实反诈识别能力"
            if eval_status == "demo"
            else ""
        ),
        eval_test_dataset_version="",
        eval_evaluated_at=_now(),
        release_status="draft",
        production_ready=False,
        publish_blocked_reason="；".join(blocked_parts),
        created_at=_now(),
        created_by=payload.operator or "system",
        description=payload.description or "",
    )
    session.add(version)

    artifact = ModelArtifactRecord(
        artifact_id=artifact_id,
        model_id=model.model_id,
        model_version_id=version_id,
        artifact_path=file_check["artifactPath"],
        manifest_path=str(manifest_path),
        artifact_format="pytorch_pt",
        file_size_bytes=int(file_check["fileSize"] or 0),
        file_hash=file_check["actualSha256"],
        file_hash_status=file_check["fileHashStatus"],
        signature=signature["signature"],
        signature_status=signature["signatureStatus"],
        signature_key_label=signature["keyLabel"],
        signature_is_development=signature["isDevelopmentKey"],
        artifact_source="training_worker",
        training_task_id=task_id,
        created_at=_now(),
        created_by=payload.operator or "system",
        last_verified_at=_now(),
    )
    session.add(artifact)

    _audit(
        session,
        model_id=model.model_id,
        model_version_id=version_id,
        operator=payload.operator,
        operation="create_version",
        target_type="model_version",
        target_id=version_id,
        target_label=f"{model.model_name} {version_number}",
        result="success",
        notes=(
            f"从训练任务 {task_id} 导入真实产物；"
            f"文件哈希={file_check['fileHashStatus']}；"
            f"签名={signature['signatureStatus']}"
            f"（{signature['keyLabel'] or '未签名'}）；"
            f"训练配置={training_profile}；数据来源={dataset_origin}"
        ),
    )
    if file_check["fileHashStatus"] == "failed":
        _audit(
            session,
            model_id=model.model_id,
            model_version_id=version_id,
            operator=payload.operator,
            operation="hash_verify_failed",
            target_type="model_version",
            target_id=version_id,
            target_label=f"{model.model_name} {version_number}",
            result="failed",
            notes=file_check["detail"],
        )
    if signature["signatureStatus"] == "failed":
        _audit(
            session,
            model_id=model.model_id,
            model_version_id=version_id,
            operator=payload.operator,
            operation="signature_verify_failed",
            target_type="model_version",
            target_id=version_id,
            target_label=f"{model.model_name} {version_number}",
            result="failed",
            notes=signature["detail"],
        )

    session.commit()
    session.refresh(version)
    return version_to_dict(session, version)


# ===== 审核清单 =====


def _check(
    key: str, label: str, status: str, detail: str, blocking: bool
) -> dict[str, Any]:
    return {
        "key": key,
        "label": label,
        "status": status,
        "detail": detail,
        "blocking": blocking,
    }


def evaluate_review(
    session: Session, version: ModelVersionRecord, channel: str, role_label: str | None
) -> dict[str, Any]:
    if channel not in {"internal_test", "canary", "production"}:
        raise ApiError(400, "PARAMETER_INVALID", "channel 仅支持 internal_test / canary / production")
    formal = channel in FORMAL_CHANNELS
    checks: list[dict[str, Any]] = []

    task = session.get(TrainingTask, version.training_task_id)
    snapshot = session.scalar(
        select(DatasetVersionSnapshot).where(
            DatasetVersionSnapshot.dataset_version_id == version.dataset_version_id
        )
    )
    artifact = get_artifact_row(session, version)

    # 1. 训练任务
    if task is None:
        checks.append(_check("training_status", "关联训练任务成功", "failed", "训练任务不存在", True))
    elif task.status != "SUCCEEDED":
        checks.append(
            _check(
                "training_status",
                "关联训练任务成功",
                "failed",
                f"训练任务状态为 {task.status}，不是 SUCCEEDED",
                True,
            )
        )
    elif task.execution_mode != "worker":
        checks.append(
            _check(
                "training_status",
                "关联训练任务成功",
                "failed",
                "训练任务不是真实 Worker 执行",
                True,
            )
        )
    elif version.training_profile == "smoke_test":
        checks.append(
            _check(
                "training_status",
                "关联训练任务成功",
                "failed" if formal else "passed",
                (
                    "trainingProfile=smoke_test，仅允许受限内部测试，禁止灰度与正式发布"
                    if formal
                    else "Smoke Test 产物，仅允许受限内部测试"
                ),
                formal,
            )
        )
    else:
        checks.append(
            _check("training_status", "关联训练任务成功", "passed", "真实 Worker 训练成功", True)
        )

    # 2. 数据集版本
    if snapshot is None:
        checks.append(_check("dataset_version", "数据集版本有效", "failed", "数据集版本快照不存在", True))
    elif snapshot.revoked or snapshot.version_status != "published":
        checks.append(
            _check(
                "dataset_version",
                "数据集版本有效",
                "failed",
                f"数据集版本状态为 {snapshot.version_status}，或已被撤销",
                True,
            )
        )
    else:
        checks.append(_check("dataset_version", "数据集版本有效", "passed", "数据集版本已发布", True))

    # 3. 数据集审核
    dataset_issues: list[str] = []
    if snapshot is None:
        dataset_issues.append("缺少数据集版本快照")
    else:
        if snapshot.source_status != "clear":
            dataset_issues.append(f"来源状态={snapshot.source_status}")
        if snapshot.license_status != "allowed":
            dataset_issues.append(f"许可={snapshot.license_status}")
        if snapshot.quality_status != "passed":
            dataset_issues.append(f"质检={snapshot.quality_status}")
        if snapshot.duplicate_status != "resolved":
            dataset_issues.append(f"重复数据={snapshot.duplicate_status}")
        if formal and version.dataset_origin == "synthetic":
            dataset_issues.append("训练数据为合成/演示来源，禁止正式反诈发布")
    if dataset_issues:
        checks.append(
            _check("dataset_review", "数据集通过必要审核", "failed", "；".join(dataset_issues), True)
        )
    else:
        checks.append(
            _check("dataset_review", "数据集通过必要审核", "passed", "来源、许可、质检、重复数据均合格", True)
        )

    # 4. 泄漏人工复核
    if snapshot is None:
        checks.append(_check("leakage_review", "同源数据泄漏人工复核", "failed", "缺少数据集快照", True))
    elif snapshot.manual_review_status == "pending":
        checks.append(
            _check(
                "leakage_review",
                "同源数据泄漏人工复核",
                "failed",
                "同源数据泄漏人工复核尚未完成",
                True,
            )
        )
    elif snapshot.leakage_risk in {"partial_group_split", "unverified"} and (
        snapshot.manual_review_status != "approved"
    ):
        checks.append(
            _check(
                "leakage_review",
                "同源数据泄漏人工复核",
                "failed",
                f"泄漏风险={snapshot.leakage_risk}，人工复核={snapshot.manual_review_status}",
                True,
            )
        )
    else:
        checks.append(
            _check("leakage_review", "同源数据泄漏人工复核", "passed", "泄漏风险复核满足要求", True)
        )

    # 5. 产物存在
    if artifact is None:
        checks.append(
            _check("artifact_exists", "模型文件存在", "failed", "缺少模型产物记录", True)
        )
        artifact_file_ok = False
    else:
        path = resolve_repo_path(artifact.artifact_path)
        artifact_file_ok = path.is_file()
        if not artifact_file_ok:
            checks.append(
                _check("artifact_exists", "模型文件存在", "failed", "模型文件在服务端不存在", True)
            )
        elif artifact.artifact_source != "training_worker":
            checks.append(
                _check(
                    "artifact_exists",
                    "模型文件存在",
                    "failed",
                    "产物来源不是真实训练 Worker",
                    True,
                )
            )
            artifact_file_ok = False
        else:
            checks.append(
                _check("artifact_exists", "模型文件存在", "passed", "真实模型文件存在", True)
            )

    # 6. 文件完整性（重新计算）
    if artifact is None or not artifact_file_ok:
        checks.append(
            _check("file_integrity", "文件完整性验证", "failed", "没有可校验的模型文件", True)
        )
    else:
        try:
            manifest = load_manifest(resolve_repo_path(artifact.manifest_path))
            file_check = verify_file(manifest, resolve_repo_path(artifact.manifest_path))
        except Exception as exc:  # noqa: BLE001
            checks.append(
                _check(
                    "file_integrity",
                    "文件完整性验证",
                    "failed",
                    f"产物清单读取失败（{type(exc).__name__}）",
                    True,
                )
            )
        else:
            if file_check["fileHashStatus"] == "verified":
                checks.append(
                    _check(
                        "file_integrity",
                        "文件完整性验证",
                        "passed",
                        f"服务端重算 SHA-256 一致（{file_check['actualSha256'][:16]}…）",
                        True,
                    )
                )
            else:
                checks.append(
                    _check(
                        "file_integrity",
                        "文件完整性验证",
                        "failed",
                        file_check["detail"],
                        True,
                    )
                )

    # 7. 数字签名（重新验签）
    if artifact is None:
        checks.append(_check("signature", "数字签名验证", "failed", "缺少模型产物记录", True))
    else:
        sig_status = artifact.signature_status
        key_label = artifact.signature_key_label or "未知密钥"
        is_dev = artifact.signature_is_development
        try:
            manifest = load_manifest(resolve_repo_path(artifact.manifest_path))
            sig = verify_signature(manifest)
            sig_status = sig["signatureStatus"]
            key_label = sig["keyLabel"] or key_label
            is_dev = sig["isDevelopmentKey"]
            sig_detail = sig["detail"]
        except Exception as exc:  # noqa: BLE001
            sig_status = "failed"
            sig_detail = f"签名复验失败（{type(exc).__name__}）"

        if sig_status == "verified" and is_dev and formal:
            checks.append(
                _check(
                    "signature",
                    "数字签名验证",
                    "failed",
                    "使用开发密钥签名（development），禁止灰度与正式发布",
                    True,
                )
            )
        elif sig_status == "verified":
            checks.append(
                _check(
                    "signature",
                    "数字签名验证",
                    "passed",
                    f"Ed25519 验签通过（{key_label}{'，开发密钥' if is_dev else ''}）",
                    True,
                )
            )
        elif sig_status == "failed":
            checks.append(_check("signature", "数字签名验证", "failed", sig_detail, True))
        else:
            checks.append(
                _check(
                    "signature",
                    "数字签名验证",
                    "failed" if formal else "unverified",
                    "模型未签名，仅允许受限内部测试" if not formal else "模型未签名，禁止灰度与正式发布",
                    formal,
                )
            )

    # 8. 模型评估
    if version.eval_status == "not_evaluated":
        checks.append(_check("evaluation", "模型评估完成", "failed", "尚无任何评估结果", True))
    elif version.eval_status == "demo":
        checks.append(
            _check(
                "evaluation",
                "模型评估完成",
                "failed" if formal else "unverified",
                (
                    "评估指标来自 Smoke Test 合成数据，禁止正式发布"
                    if formal
                    else "Smoke Test 合成数据指标，不代表反诈能力"
                ),
                formal,
            )
        )
    else:
        checks.append(_check("evaluation", "模型评估完成", "passed", "存在有效评估结果", True))

    # 9. 测试集评估报告
    report = session.scalar(
        select(ModelEvaluationReportRecord)
        .where(
            ModelEvaluationReportRecord.model_version_id == version.model_version_id,
            ModelEvaluationReportRecord.is_real_test_set.is_(True),
            ModelEvaluationReportRecord.evaluation_status == "evaluated",
        )
        .order_by(ModelEvaluationReportRecord.evaluated_at.desc())
    )
    if report is not None:
        checks.append(
            _check(
                "test_evaluation",
                "测试集评估有效",
                "passed",
                f"真实测试集评估报告存在（{report.dataset_version_id}）",
                True,
            )
        )
    else:
        checks.append(
            _check(
                "test_evaluation",
                "测试集评估有效",
                "failed" if formal else "unverified",
                (
                    "缺少真实测试集评估报告，禁止灰度与正式发布"
                    if formal
                    else "内部测试不要求真实测试集评估报告"
                ),
                formal,
            )
        )

    # 10. 运行时兼容性
    compat_rows = session.scalars(
        select(ModelCompatibilityRecord).where(
            ModelCompatibilityRecord.model_version_id == version.model_version_id
        )
    ).all()
    if not compat_rows:
        checks.append(
            _check(
                "runtime_compatibility",
                "目标运行时兼容性",
                "failed" if formal else "unverified",
                (
                    "没有真实设备测试记录，禁止灰度与正式发布"
                    if formal
                    else "暂无真实设备测试记录，仅允许受限内部测试"
                ),
                formal,
            )
        )
    elif any(row.compatibility_status in {"test_failed", "incompatible"} for row in compat_rows):
        checks.append(
            _check(
                "runtime_compatibility",
                "目标运行时兼容性",
                "failed",
                "存在测试失败或不兼容的设备记录",
                True,
            )
        )
    elif formal and not all(
        row.compatibility_status == "compatible" and row.is_real_device_test for row in compat_rows
    ):
        checks.append(
            _check(
                "runtime_compatibility",
                "目标运行时兼容性",
                "failed",
                "灰度/正式发布要求全部为真实设备测试且兼容",
                True,
            )
        )
    else:
        checks.append(
            _check(
                "runtime_compatibility",
                "目标运行时兼容性",
                "passed",
                "设备测试记录满足当前渠道要求",
                True,
            )
        )

    # 11. 发布权限
    role = role_label or ""
    if any(item and item in role for item in allowed_roles()):
        checks.append(_check("publish_permission", "发布审批权限", "passed", "具备发布权限", True))
    else:
        checks.append(
            _check("publish_permission", "发布审批权限", "failed", "当前角色无发布权限", True)
        )

    blocked = [
        f"{item['label']}：{item['detail']}"
        for item in checks
        if item["blocking"] and item["status"] != "passed"
    ]
    return {
        "modelVersionId": version.model_version_id,
        "channel": channel,
        "checks": checks,
        "eligible": not blocked,
        "blockedReasons": blocked,
        "checkedAt": display_time(utc_iso(_now())),
        "note": (
            "受限内部测试渠道：Smoke Test / 合成数据产物可在此验证，但不会作为真实反诈模型发布"
            if not formal
            else "灰度与正式发布要求真实模型文件、非开发密钥签名、真实测试集评估与真实设备兼容性记录"
        ),
    }


# ===== 状态机动作 =====


def _get_version(session: Session, version_id: str) -> ModelVersionRecord:
    version = session.get(ModelVersionRecord, version_id)
    if version is None:
        raise ApiError(404, "MODEL_VERSION_NOT_FOUND", "模型版本不存在")
    return version


def _latest_release(
    session: Session, version_id: str, status: str | None = None
) -> ModelReleaseRecord | None:
    stmt = select(ModelReleaseRecord).where(ModelReleaseRecord.model_version_id == version_id)
    if status:
        stmt = stmt.where(ModelReleaseRecord.release_status == status)
    return session.scalar(stmt.order_by(ModelReleaseRecord.release_id.desc()))


def submit_review(session: Session, version_id: str, payload: Any) -> dict[str, Any]:
    version = _get_version(session, version_id)
    if version.release_status not in {"draft", "rejected", "pending_review"}:
        raise ApiError(409, "MODEL_STATE_INVALID", "当前状态不允许提交审核")
    channel = payload.channel or "internal_test"
    if channel not in CHANNEL_LABELS:
        raise ApiError(400, "PARAMETER_INVALID", "不支持的发布渠道")

    version.release_status = "pending_review"
    record = ModelReleaseRecord(
        release_id=f"RL-{uuid.uuid4().hex[:12]}",
        model_id=version.model_id,
        model_version_id=version.model_version_id,
        model_version=version.model_version,
        release_channel=channel,
        release_status="pending_review",
        operator=payload.operator,
        submitted_at=_now(),
        operation_notes=payload.notes or f"提交{CHANNEL_LABELS[channel]}审核",
    )
    session.add(record)
    _audit(
        session,
        model_id=version.model_id,
        model_version_id=version.model_version_id,
        operator=payload.operator,
        operation="submit_review",
        target_type="release",
        target_id=record.release_id,
        target_label=f"{version.model_version} · {CHANNEL_LABELS[channel]}",
        notes=payload.notes or "提交审核",
    )
    session.commit()
    return release_to_dict(record)


def approve_review(session: Session, version_id: str, payload: Any) -> dict[str, Any]:
    ensure_release_role(payload.roleLabel)
    version = _get_version(session, version_id)
    if version.release_status != "pending_review":
        raise ApiError(409, "MODEL_STATE_INVALID", "只有待审核版本可以审核通过")

    channel = payload.channel or "internal_test"
    review = evaluate_review(session, version, channel, payload.roleLabel)
    if not review["eligible"]:
        _audit(
            session,
            model_id=version.model_id,
            model_version_id=version.model_version_id,
            operator=payload.operator,
            operation="approve",
            target_type="model_version",
            target_id=version.model_version_id,
            target_label=version.model_version,
            result="blocked",
            notes="审核拦截：" + "；".join(review["blockedReasons"]),
        )
        session.commit()
        raise ApiError(422, "MODEL_REVIEW_BLOCKED", "审核清单存在阻断项：" + "；".join(review["blockedReasons"]))

    version.release_status = "approved"
    version.reviewed_at = _now()
    version.reviewed_by = payload.operator
    record = _latest_release(session, version_id, "pending_review")
    if record is None:
        record = ModelReleaseRecord(
            release_id=f"RL-{uuid.uuid4().hex[:12]}",
            model_id=version.model_id,
            model_version_id=version.model_version_id,
            model_version=version.model_version,
            release_channel=channel,
            release_status="approved",
            operator=payload.operator,
            submitted_at=_now(),
            operation_notes=payload.notes,
        )
        session.add(record)
    record.release_status = "approved"
    record.reviewed_by = payload.operator
    record.approved_at = _now()
    record.operation_notes = (record.operation_notes + "；" + payload.notes).strip("；") if payload.notes else record.operation_notes
    _audit(
        session,
        model_id=version.model_id,
        model_version_id=version.model_version_id,
        operator=payload.operator,
        operation="approve",
        target_type="release",
        target_id=record.release_id,
        target_label=f"{version.model_version} · {CHANNEL_LABELS[channel]}",
        notes=payload.notes or "服务端审核清单通过",
    )
    session.commit()
    return release_to_dict(record)


def reject_review(session: Session, version_id: str, payload: Any) -> dict[str, Any]:
    ensure_release_role(payload.roleLabel)
    version = _get_version(session, version_id)
    if version.release_status != "pending_review":
        raise ApiError(409, "MODEL_STATE_INVALID", "只有待审核版本可以拒绝")
    reason = (payload.reason or "").strip()
    if not reason:
        raise ApiError(400, "PARAMETER_INVALID", "必须填写拒绝原因")

    version.release_status = "rejected"
    version.reviewed_at = _now()
    version.reviewed_by = payload.reviewer
    record = _latest_release(session, version_id, "pending_review")
    if record is not None:
        record.release_status = "rejected"
        record.reviewed_by = payload.reviewer
        record.approved_at = None
        record.operation_notes = (record.operation_notes + "；拒绝原因：" + reason).strip("；")
    _audit(
        session,
        model_id=version.model_id,
        model_version_id=version.model_version_id,
        operator=payload.reviewer,
        operation="reject",
        target_type="model_version",
        target_id=version.model_version_id,
        target_label=version.model_version,
        result="blocked",
        notes=reason,
    )
    session.commit()
    return version_to_dict(session, version)


def publish_version(session: Session, version_id: str, payload: Any) -> dict[str, Any]:
    ensure_release_role(payload.roleLabel)
    version = _get_version(session, version_id)
    if version.release_status != "approved":
        raise ApiError(409, "MODEL_STATE_INVALID", "只有审核通过的版本可以发布")
    channel = payload.channel or "internal_test"
    review = evaluate_review(session, version, channel, payload.roleLabel)
    if not review["eligible"]:
        _audit(
            session,
            model_id=version.model_id,
            model_version_id=version.model_version_id,
            operator=payload.operator,
            operation="publish",
            target_type="model_version",
            target_id=version.model_version_id,
            target_label=version.model_version,
            result="blocked",
            notes="发布拦截：" + "；".join(review["blockedReasons"]),
        )
        session.commit()
        raise ApiError(
            422, "MODEL_RELEASE_BLOCKED", "发布前置条件未满足：" + "；".join(review["blockedReasons"])
        )

    note = payload.notes or "发布"
    if channel == "internal_test" and version.training_profile == "smoke_test":
        note = "[受限内部测试] " + note + "（Smoke Test 产物，禁止正式反诈发布）"

    version.release_status = "published"
    version.published_at = _now()
    record = ModelReleaseRecord(
        release_id=f"RL-{uuid.uuid4().hex[:12]}",
        model_id=version.model_id,
        model_version_id=version.model_version_id,
        model_version=version.model_version,
        release_channel=channel,
        release_status="published",
        operator=payload.operator,
        reviewed_by=version.reviewed_by,
        submitted_at=_now(),
        approved_at=_now(),
        published_at=_now(),
        operation_notes=note,
    )
    session.add(record)
    _audit(
        session,
        model_id=version.model_id,
        model_version_id=version.model_version_id,
        operator=payload.operator,
        operation="publish",
        target_type="release",
        target_id=record.release_id,
        target_label=f"{version.model_version} · {CHANNEL_LABELS[channel]}",
        notes=note,
    )
    session.commit()
    return release_to_dict(record)


def withdraw_version(session: Session, version_id: str, payload: Any) -> dict[str, Any]:
    ensure_release_role(payload.roleLabel)
    version = _get_version(session, version_id)
    if version.release_status != "published":
        raise ApiError(409, "MODEL_STATE_INVALID", "只有已发布版本可以撤回")
    reason = (payload.reason or "").strip()
    if not reason:
        raise ApiError(400, "WITHDRAW_REASON_REQUIRED", "必须填写撤回原因")

    version.release_status = "withdrawn"
    version.withdrawn_at = _now()
    record = _latest_release(session, version_id, "published")
    if record is not None:
        record.release_status = "withdrawn"
        record.withdrawn_at = _now()
        record.operation_notes = (
            record.operation_notes + f"；撤回原因：{reason}；影响说明：{payload.impact or '未填写'}"
        ).strip("；")
    _audit(
        session,
        model_id=version.model_id,
        model_version_id=version.model_version_id,
        operator=payload.operator,
        operation="withdraw",
        target_type="model_version",
        target_id=version.model_version_id,
        target_label=version.model_version,
        notes=f"撤回原因：{reason}；影响说明：{payload.impact or '未填写'}",
    )
    session.commit()
    return version_to_dict(session, version)


def rollback_model(session: Session, model_id: str, payload: Any) -> dict[str, Any]:
    ensure_release_role(payload.roleLabel)
    model = session.get(ModelRecord, model_id)
    if model is None:
        raise ApiError(404, "MODEL_NOT_FOUND", "模型不存在")
    if payload.fromVersionId == payload.toVersionId:
        raise ApiError(400, "ROLLBACK_TARGET_INVALID", "回滚目标不能与当前版本相同")

    source = session.get(ModelVersionRecord, payload.fromVersionId)
    if source is None or source.model_id != model_id or source.release_status != "published":
        raise ApiError(409, "ROLLBACK_SOURCE_INVALID", "当前发布版本不存在或已不是发布状态")
    target = session.get(ModelVersionRecord, payload.toVersionId)
    if target is None or target.model_id != model_id:
        raise ApiError(404, "MODEL_VERSION_NOT_FOUND", "回滚目标版本不存在")
    if target.release_status not in {"approved", "published"}:
        raise ApiError(409, "ROLLBACK_TARGET_INVALID", "回滚目标必须已审核通过")

    review = evaluate_review(session, target, "internal_test", payload.roleLabel)
    if not review["eligible"]:
        _audit(
            session,
            model_id=model_id,
            model_version_id=target.model_version_id,
            operator=payload.operator,
            operation="rollback",
            target_type="model_version",
            target_id=target.model_version_id,
            target_label=target.model_version,
            result="blocked",
            notes="回滚前检查未通过：" + "；".join(review["blockedReasons"]),
        )
        session.commit()
        raise ApiError(
            422,
            "ROLLBACK_TARGET_NOT_READY",
            "回滚目标未通过回滚前检查：" + "；".join(review["blockedReasons"]),
        )

    source.release_status = "rolled_back"
    target.release_status = "published"
    target.published_at = _now()
    record = ModelReleaseRecord(
        release_id=f"RL-{uuid.uuid4().hex[:12]}",
        model_id=model_id,
        model_version_id=target.model_version_id,
        model_version=target.model_version,
        release_channel="internal_test",
        release_status="published",
        operator=payload.operator,
        reviewed_by=target.reviewed_by,
        submitted_at=_now(),
        approved_at=_now(),
        published_at=_now(),
        rollback_from=source.model_version_id,
        rollback_to=target.model_version_id,
        operation_notes=payload.notes or f"从 {source.model_version} 回滚到 {target.model_version}",
    )
    session.add(record)
    _audit(
        session,
        model_id=model_id,
        model_version_id=target.model_version_id,
        operator=payload.operator,
        operation="rollback",
        target_type="release",
        target_id=record.release_id,
        target_label=f"{source.model_version} → {target.model_version}",
        notes=record.operation_notes,
    )
    session.commit()
    return release_to_dict(record)


def record_compatibility(session: Session, version_id: str, payload: Any) -> dict[str, Any]:
    version = _get_version(session, version_id)
    record = ModelCompatibilityRecord(
        compatibility_id=f"CP-{uuid.uuid4().hex[:12]}",
        model_version_id=version.model_version_id,
        platform=payload.platform,
        min_os_version=payload.minOsVersion,
        max_os_version=payload.maxOsVersion,
        architecture=payload.architecture,
        inference_runtime=payload.inferenceRuntime,
        runtime_version=payload.runtimeVersion,
        devices_json=json.dumps(payload.supportedDevices, ensure_ascii=False),
        memory_requirement_mb=payload.memoryRequirementMb,
        accelerators_json=json.dumps(payload.acceleratorSupport, ensure_ascii=False),
        compatibility_status=payload.compatibilityStatus,
        tested_at=_now(),
        tested_by=payload.testedBy,
        notes=payload.notes,
        is_real_device_test=payload.isRealDeviceTest,
    )
    session.add(record)
    if payload.compatibilityStatus in {"test_failed", "incompatible"}:
        _audit(
            session,
            model_id=version.model_id,
            model_version_id=version.model_version_id,
            operator=payload.testedBy,
            operation="compatibility_test_failed",
            target_type="model_version",
            target_id=version.model_version_id,
            target_label=version.model_version,
            result="failed",
            notes=f"{payload.architecture} / {payload.inferenceRuntime}：{payload.compatibilityStatus}",
        )
    session.commit()
    return compatibility_to_dict(record)


# ===== 查询 =====


def get_stats(session: Session) -> dict[str, Any]:
    total_models = session.scalar(select(func.count()).select_from(ModelRecord)) or 0
    total_versions = session.scalar(select(func.count()).select_from(ModelVersionRecord)) or 0
    pending = (
        session.scalar(
            select(func.count())
            .select_from(ModelVersionRecord)
            .where(ModelVersionRecord.release_status == "pending_review")
        )
        or 0
    )
    published = (
        session.scalar(
            select(func.count())
            .select_from(ModelVersionRecord)
            .where(ModelVersionRecord.release_status == "published")
        )
        or 0
    )
    withdrawn = (
        session.scalar(
            select(func.count())
            .select_from(ModelVersionRecord)
            .where(ModelVersionRecord.release_status == "withdrawn")
        )
        or 0
    )
    versions = session.scalars(select(ModelVersionRecord)).all()
    untested = sum(1 for item in versions if _model_compat_status(session, item) == "untested")
    return {
        "totalModels": int(total_models),
        "totalVersions": int(total_versions),
        "pendingReview": int(pending),
        "published": int(published),
        "withdrawn": int(withdrawn),
        "compatibilityUntested": int(untested),
    }


def get_options(session: Session) -> dict[str, Any]:
    snapshots = session.scalars(
        select(DatasetVersionSnapshot).order_by(DatasetVersionSnapshot.snapshot_created_at.desc()).limit(50)
    ).all()
    tasks = session.scalars(
        select(TrainingTask).order_by(TrainingTask.created_at.desc()).limit(100)
    ).all()
    return {
        "modelTypes": [
            {"value": key, "label": label} for key, label in MODEL_TYPE_LABELS.items()
        ],
        "frameworks": [{"value": "pytorch", "label": "PyTorch"}],
        "modelFormats": MODEL_FORMATS,
        "releaseStatuses": [
            {"value": key, "label": label} for key, label in RELEASE_STATUS_LABELS.items()
        ],
        "compatibilityStatuses": COMPAT_STATUSES,
        "channels": [
            {
                "value": "internal_test",
                "label": "受限内部测试",
                "note": "Smoke Test / 合成数据产物仅可在此渠道验证，不向真实设备下发",
            },
            {
                "value": "canary",
                "label": "灰度发布",
                "note": "要求真实非开发密钥签名、真实测试集评估与真实设备兼容性记录",
            },
            {
                "value": "production",
                "label": "正式发布",
                "note": "禁止 Smoke Test 与合成数据产物进入",
            },
        ],
        "quantizationFormats": QUANTIZATION_FORMATS,
        "datasetVersions": [
            {
                "datasetId": item.dataset_id,
                "datasetName": item.dataset_name,
                "versionId": item.dataset_version_id,
                "versionNumber": item.dataset_version,
                "versionStatus": item.version_status,
                "manualReviewStatus": item.manual_review_status,
            }
            for item in snapshots
        ],
        "trainingTasks": [
            {
                "taskId": item.task_id,
                "taskName": item.task_name,
                "taskStatus": item.status.lower(),
                "executionMode": item.execution_mode,
            }
            for item in tasks
        ],
    }


def list_models(session: Session, query: dict[str, Any]) -> dict[str, Any]:
    stmt = select(ModelRecord)
    keyword = (query.get("keyword") or "").strip()
    if keyword:
        stmt = stmt.where(
            ModelRecord.model_name.ilike(f"%{keyword}%") | ModelRecord.model_id.ilike(f"%{keyword}%")
        )
    if query.get("modelType"):
        stmt = stmt.where(ModelRecord.model_type == query["modelType"])
    if query.get("framework"):
        stmt = stmt.where(ModelRecord.framework == query["framework"])

    models = session.scalars(stmt.order_by(ModelRecord.created_at.desc())).all()
    items = [model_to_dict(session, model) for model in models]

    if query.get("releaseStatus"):
        items = [item for item in items if item["releaseStatus"] == query["releaseStatus"]]
    if query.get("compatibilityStatus"):
        items = [
            item for item in items if item["compatibilityStatus"] == query["compatibilityStatus"]
        ]
    if query.get("publishedFrom") or query.get("publishedTo"):
        start = query.get("publishedFrom") or ""
        end = query.get("publishedTo") or ""
        filtered = []
        for item in items:
            if not item["publishedVersionId"]:
                continue
            stamp = (item["updatedAt"] or "")[:10]
            if start and stamp < start:
                continue
            if end and stamp > end:
                continue
            filtered.append(item)
        items = filtered

    page = int(query.get("page") or 1)
    page_size = min(int(query.get("pageSize") or 20), 100)
    total = len(items)
    start = (page - 1) * page_size
    return {
        "items": items[start : start + page_size],
        "page": page,
        "pageSize": page_size,
        "total": total,
    }


def get_model(session: Session, model_id: str) -> dict[str, Any]:
    model = session.get(ModelRecord, model_id)
    if model is None:
        raise ApiError(404, "MODEL_NOT_FOUND", "模型不存在")
    return model_to_dict(session, model)


def list_versions(session: Session, model_id: str, query: dict[str, Any]) -> dict[str, Any]:
    if session.get(ModelRecord, model_id) is None:
        raise ApiError(404, "MODEL_NOT_FOUND", "模型不存在")
    stmt = select(ModelVersionRecord).where(ModelVersionRecord.model_id == model_id)
    keyword = (query.get("keyword") or "").strip()
    if keyword:
        stmt = stmt.where(
            ModelVersionRecord.model_version.ilike(f"%{keyword}%")
            | ModelVersionRecord.model_version_id.ilike(f"%{keyword}%")
        )
    if query.get("releaseStatus"):
        stmt = stmt.where(ModelVersionRecord.release_status == query["releaseStatus"])
    versions = session.scalars(
        stmt.order_by(ModelVersionRecord.created_at.desc())
    ).all()
    items = [version_to_dict(session, item) for item in versions]
    if query.get("compatibilityStatus"):
        items = [
            item
            for item in items
            if _version_compat(item) == query["compatibilityStatus"]
        ]
    page = int(query.get("page") or 1)
    page_size = min(int(query.get("pageSize") or 20), 100)
    total = len(items)
    start = (page - 1) * page_size
    return {
        "items": items[start : start + page_size],
        "page": page,
        "pageSize": page_size,
        "total": total,
    }


def _version_compat(item: dict[str, Any]) -> str:
    rows = item.get("runtimeCompatibility") or []
    if not rows:
        return "untested"
    statuses = [row["compatibilityStatus"] for row in rows]
    for status in ("test_failed", "incompatible", "partially_compatible", "testing"):
        if status in statuses:
            return status
    return "compatible"


def get_version(session: Session, model_id: str, version_id: str) -> dict[str, Any]:
    version = _get_version(session, version_id)
    if version.model_id != model_id:
        raise ApiError(404, "MODEL_VERSION_NOT_FOUND", "模型版本不属于当前模型")
    return version_to_dict(session, version)


def get_dataset_snapshot(session: Session, version_id: str) -> dict[str, Any]:
    """模型版本关联数据集的服务端快照与资格结论（只读）。

    前端数据集卡片在真实模式下必须使用该结果，不得回退到 P10 Mock 数据。
    """
    version = _get_version(session, version_id)
    snapshot = session.execute(
        select(DatasetVersionSnapshot).where(
            DatasetVersionSnapshot.dataset_version_id == version.dataset_version_id
        )
    ).scalar_one_or_none()
    if snapshot is None:
        raise ApiError(
            404,
            "DATASET_VERSION_NOT_FOUND",
            "服务端没有该数据集版本的快照，无法展示真实数据集来源",
        )
    data = snapshot_to_check(snapshot)
    data.update(
        {
            "sourceStatus": snapshot.source_status,
            "licenseStatus": snapshot.license_status,
            "qualityStatus": snapshot.quality_status,
            "duplicateStatus": snapshot.duplicate_status,
            "leakageRisk": snapshot.leakage_risk,
            "snapshotSource": snapshot.snapshot_source,
            "revoked": snapshot.revoked,
            "snapshotCreatedAt": snapshot.snapshot_created_at,
            "dataSource": "dataset_version_snapshot",
        }
    )
    return data


def get_artifact(session: Session, artifact_id: str) -> dict[str, Any]:
    record = session.get(ModelArtifactRecord, artifact_id)
    if record is None:
        raise ApiError(404, "ARTIFACT_NOT_FOUND", "模型文件记录不存在")
    return artifact_to_dict(record, session.get(ModelRecord, record.model_id))


def list_artifacts(session: Session, model_id: str) -> list[dict[str, Any]]:
    if not model_id:
        raise ApiError(400, "PARAMETER_INVALID", "缺少 modelId")
    rows = session.scalars(
        select(ModelArtifactRecord)
        .where(ModelArtifactRecord.model_id == model_id)
        .order_by(ModelArtifactRecord.created_at.desc())
    ).all()
    model = session.get(ModelRecord, model_id)
    return [artifact_to_dict(row, model) for row in rows]


def list_releases(
    session: Session, model_id: str | None = None, version_id: str | None = None
) -> list[dict[str, Any]]:
    stmt = select(ModelReleaseRecord)
    if version_id:
        stmt = stmt.where(ModelReleaseRecord.model_version_id == version_id)
    elif model_id:
        stmt = stmt.where(ModelReleaseRecord.model_id == model_id)
    else:
        raise ApiError(400, "PARAMETER_INVALID", "缺少 modelId 或 modelVersionId")
    rows = session.scalars(stmt.order_by(ModelReleaseRecord.release_id.desc())).all()
    return [release_to_dict(row) for row in rows]


def list_audits(
    session: Session, model_id: str | None = None, version_id: str | None = None
) -> list[dict[str, Any]]:
    stmt = select(ModelAuditLogRecord)
    if version_id:
        stmt = stmt.where(ModelAuditLogRecord.model_version_id == version_id)
    elif model_id:
        stmt = stmt.where(ModelAuditLogRecord.model_id == model_id)
    else:
        raise ApiError(400, "PARAMETER_INVALID", "缺少 modelId 或 modelVersionId")
    rows = session.scalars(
        stmt.order_by(ModelAuditLogRecord.operated_at.desc()).limit(200)
    ).all()
    return [audit_to_dict(row) for row in rows]


def compare_versions(session: Session, base_id: str, target_id: str) -> list[dict[str, Any]]:
    base = _get_version(session, base_id)
    target = _get_version(session, target_id)
    base_artifact = get_artifact_row(session, base)
    target_artifact = get_artifact_row(session, target)

    rows = [
        ("版本号", base.model_version, target.model_version),
        ("发布状态", RELEASE_STATUS_LABELS.get(base.release_status, base.release_status), RELEASE_STATUS_LABELS.get(target.release_status, target.release_status)),
        ("训练任务", base.training_task_id, target.training_task_id),
        ("训练配置", base.training_profile, target.training_profile),
        ("数据集版本", base.dataset_version_id, target.dataset_version_id),
        ("数据来源", base.dataset_origin, target.dataset_origin),
        ("文件 SHA-256", (base_artifact.file_hash if base_artifact else ""), (target_artifact.file_hash if target_artifact else "")),
        ("签名状态", (base_artifact.signature_status if base_artifact else ""), (target_artifact.signature_status if target_artifact else "")),
        ("评估状态", base.eval_status, target.eval_status),
        ("准确率", str(base.eval_accuracy or ""), str(target.eval_accuracy or "")),
        ("F1", str(base.eval_f1 or ""), str(target.eval_f1 or "")),
    ]
    return [
        {
            "label": label,
            "baseValue": str(left),
            "targetValue": str(right),
            "changed": str(left) != str(right),
        }
        for label, left, right in rows
    ]


def repo_root_str() -> str:
    return str(REPO_ROOT)
