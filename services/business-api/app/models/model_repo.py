"""P12 模型仓库数据表（真实训练产物接入）。

与 P11-R 的训练/数据集表并列，不改动既有表结构。
所有记录均为服务端持久化数据；前端只读展示，不提交校验结论。
"""

from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from . import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class ModelRecord(Base):
    """模型主记录。"""

    __tablename__ = "models"

    model_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    model_name: Mapped[str] = mapped_column(String(200), nullable=False)
    model_type: Mapped[str] = mapped_column(String(64), nullable=False)
    framework: Mapped[str] = mapped_column(String(32), nullable=False, default="pytorch")
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    model_status: Mapped[str] = mapped_column(String(16), nullable=False, default="active")
    training_task_id: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    created_by: Mapped[str] = mapped_column(String(64), nullable=False, default="system")


class ModelVersionRecord(Base):
    """模型版本：必须关联真实训练任务与数据集版本快照。"""

    __tablename__ = "model_versions"

    model_version_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    model_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    model_version: Mapped[str] = mapped_column(String(64), nullable=False)
    dataset_id: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    dataset_name: Mapped[str] = mapped_column(String(200), nullable=False, default="")
    dataset_version_id: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    dataset_version: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    training_task_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    artifact_id: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    model_format: Mapped[str] = mapped_column(String(32), nullable=False, default="pytorch_pt")
    training_profile: Mapped[str] = mapped_column(String(32), nullable=False, default="")
    dataset_type: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    dataset_origin: Mapped[str] = mapped_column(String(32), nullable=False, default="real")

    eval_accuracy: Mapped[float | None] = mapped_column(Float, nullable=True)
    eval_precision: Mapped[float | None] = mapped_column(Float, nullable=True)
    eval_recall: Mapped[float | None] = mapped_column(Float, nullable=True)
    eval_f1: Mapped[float | None] = mapped_column(Float, nullable=True)
    eval_validation_loss: Mapped[float | None] = mapped_column(Float, nullable=True)
    eval_status: Mapped[str] = mapped_column(String(32), nullable=False, default="not_evaluated")
    eval_source: Mapped[str] = mapped_column(String(32), nullable=False, default="training_task")
    eval_method: Mapped[str] = mapped_column(String(200), nullable=False, default="")
    eval_test_dataset_version: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    eval_evaluated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    release_status: Mapped[str] = mapped_column(String(32), nullable=False, default="draft")
    production_ready: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    publish_blocked_reason: Mapped[str] = mapped_column(Text, nullable=False, default="")

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    created_by: Mapped[str] = mapped_column(String(64), nullable=False, default="system")
    reviewed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    reviewed_by: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    withdrawn_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")


class ModelArtifactRecord(Base):
    """真实模型文件记录：哈希与签名均由服务端重新校验。"""

    __tablename__ = "model_artifacts"

    artifact_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    model_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    model_version_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    artifact_path: Mapped[str] = mapped_column(String(500), nullable=False)
    manifest_path: Mapped[str] = mapped_column(String(500), nullable=False, default="")
    artifact_format: Mapped[str] = mapped_column(String(32), nullable=False, default="pytorch_pt")
    file_size_bytes: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    file_hash: Mapped[str] = mapped_column(String(128), nullable=False, default="")
    file_hash_status: Mapped[str] = mapped_column(
        String(32), nullable=False, default="not_verified"
    )
    signature: Mapped[str] = mapped_column(Text, nullable=False, default="")
    signature_status: Mapped[str] = mapped_column(
        String(32), nullable=False, default="not_verified"
    )
    signature_key_label: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    signature_is_development: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    artifact_source: Mapped[str] = mapped_column(
        String(32), nullable=False, default="training_worker"
    )
    training_task_id: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    created_by: Mapped[str] = mapped_column(String(64), nullable=False, default="system")
    last_verified_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )


class ModelReleaseRecord(Base):
    """发布 / 审核 / 撤回记录：只追加，不覆盖历史。"""

    __tablename__ = "model_release_records"

    release_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    model_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    model_version_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    model_version: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    release_channel: Mapped[str] = mapped_column(String(32), nullable=False, default="internal_test")
    release_status: Mapped[str] = mapped_column(String(32), nullable=False, default="pending_review")
    operator: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    reviewed_by: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    submitted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    approved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    withdrawn_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    rollback_from: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    rollback_to: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    operation_notes: Mapped[str] = mapped_column(Text, nullable=False, default="")
    is_simulated: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)


class ModelAuditLogRecord(Base):
    """模型审计日志：只追加，不提供删除接口。"""

    __tablename__ = "model_audit_logs"

    audit_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    model_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    model_version_id: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    operator: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    operation: Mapped[str] = mapped_column(String(64), nullable=False)
    operation_label: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    target_type: Mapped[str] = mapped_column(String(32), nullable=False, default="model_version")
    target_id: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    target_label: Mapped[str] = mapped_column(String(200), nullable=False, default="")
    operated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    result: Mapped[str] = mapped_column(String(16), nullable=False, default="success")
    notes: Mapped[str] = mapped_column(Text, nullable=False, default="")


class ModelCompatibilityRecord(Base):
    """运行时兼容性记录；is_real_device_test=false 的记录不能支撑灰度/正式发布。"""

    __tablename__ = "model_compatibility_tests"

    compatibility_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    model_version_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    platform: Mapped[str] = mapped_column(String(32), nullable=False, default="android")
    min_os_version: Mapped[str] = mapped_column(String(32), nullable=False, default="")
    max_os_version: Mapped[str] = mapped_column(String(32), nullable=False, default="")
    architecture: Mapped[str] = mapped_column(String(32), nullable=False, default="arm64-v8a")
    inference_runtime: Mapped[str] = mapped_column(
        String(32), nullable=False, default="pytorch_mobile"
    )
    runtime_version: Mapped[str] = mapped_column(String(32), nullable=False, default="")
    devices_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]")
    memory_requirement_mb: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    accelerators_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]")
    compatibility_status: Mapped[str] = mapped_column(
        String(32), nullable=False, default="untested"
    )
    tested_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    tested_by: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    notes: Mapped[str] = mapped_column(Text, nullable=False, default="")
    is_real_device_test: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)


class ModelEvaluationReportRecord(Base):
    """测试集评估报告。只有 is_real_test_set=true 的报告可用于灰度/正式发布。"""

    __tablename__ = "model_evaluation_reports"

    report_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    model_version_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    dataset_version_id: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    dataset_type: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    evaluation_status: Mapped[str] = mapped_column(
        String(32), nullable=False, default="not_evaluated"
    )
    metrics_json: Mapped[str] = mapped_column(Text, nullable=False, default="{}")
    report_path: Mapped[str] = mapped_column(String(500), nullable=False, default="")
    evaluated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    evaluated_by: Mapped[str] = mapped_column(String(64), nullable=False, default="")
    source: Mapped[str] = mapped_column(String(32), nullable=False, default="manual")
    is_real_test_set: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    notes: Mapped[str] = mapped_column(Text, nullable=False, default="")
