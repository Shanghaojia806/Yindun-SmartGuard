"""P11-R 数据表。

设计原则：
- 任务、日志、指标、检查点、结果、Worker 事件分表持久化；
- 数据集版本以服务端快照为唯一可信来源，前端传入的数据集状态一律不采信；
- 审计与 Worker 事件只追加，不提供删除接口。
"""

from __future__ import annotations

from sqlalchemy import Boolean, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from ..db import Base


class DatasetVersionSnapshot(Base):
    __tablename__ = "dataset_version_snapshots"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    dataset_id: Mapped[str] = mapped_column(String(64), index=True)
    dataset_name: Mapped[str] = mapped_column(String(200))
    dataset_type: Mapped[str] = mapped_column(String(32), default="text")
    dataset_version_id: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    dataset_version: Mapped[str] = mapped_column(String(64))

    sample_count: Mapped[int] = mapped_column(Integer, default=0)
    train_count: Mapped[int] = mapped_column(Integer, default=0)
    validation_count: Mapped[int] = mapped_column(Integer, default=0)
    test_count: Mapped[int] = mapped_column(Integer, default=0)

    source_status: Mapped[str] = mapped_column(String(32), default="unknown")
    license_status: Mapped[str] = mapped_column(String(32), default="unknown")
    quality_status: Mapped[str] = mapped_column(String(32), default="pending")
    duplicate_status: Mapped[str] = mapped_column(String(32), default="pending")
    leakage_risk: Mapped[str] = mapped_column(String(32), default="unverified")
    manual_review_status: Mapped[str] = mapped_column(String(32), default="pending")
    version_status: Mapped[str] = mapped_column(String(32), default="draft")

    training_eligibility: Mapped[bool] = mapped_column(Boolean, default=False)
    revoked: Mapped[bool] = mapped_column(Boolean, default=False)
    revoked_at: Mapped[str] = mapped_column(String(40), default="")
    snapshot_source: Mapped[str] = mapped_column(String(32), default="synthetic_demo")
    note: Mapped[str] = mapped_column(Text, default="")
    snapshot_created_at: Mapped[str] = mapped_column(String(40), index=True)
    created_by: Mapped[str] = mapped_column(String(80), default="system")


class TrainingTask(Base):
    __tablename__ = "training_tasks"

    task_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    task_name: Mapped[str] = mapped_column(String(80))
    task_description: Mapped[str] = mapped_column(String(240), default="")
    model_type: Mapped[str] = mapped_column(String(64))
    task_type: Mapped[str] = mapped_column(String(64))
    model_config_json: Mapped[str] = mapped_column(Text, default="{}")

    dataset_id: Mapped[str] = mapped_column(String(64), index=True)
    dataset_name: Mapped[str] = mapped_column(String(200), default="")
    dataset_version_id: Mapped[str] = mapped_column(String(64), index=True)
    dataset_version: Mapped[str] = mapped_column(String(64), default="")
    dataset_split_json: Mapped[str] = mapped_column(Text, default="{}")
    dataset_snapshot_json: Mapped[str] = mapped_column(Text, default="{}")
    parameters_json: Mapped[str] = mapped_column(Text, default="{}")

    status: Mapped[str] = mapped_column(String(24), index=True, default="PENDING")
    execution_mode: Mapped[str] = mapped_column(String(16), default="worker")
    training_profile: Mapped[str] = mapped_column(String(32), default="smoke_test")

    created_at: Mapped[str] = mapped_column(String(40), index=True)
    created_by: Mapped[str] = mapped_column(String(80), default="演示管理员")
    started_at: Mapped[str] = mapped_column(String(40), default="")
    completed_at: Mapped[str] = mapped_column(String(40), default="")
    error_message: Mapped[str] = mapped_column(Text, default="")
    cancel_requested_at: Mapped[str] = mapped_column(String(40), default="")
    retry_of_task_id: Mapped[str] = mapped_column(String(64), default="")
    retried_by_task_id: Mapped[str] = mapped_column(String(64), default="")
    attempt: Mapped[int] = mapped_column(Integer, default=1)

    worker_id: Mapped[str] = mapped_column(String(80), default="")
    heartbeat_at: Mapped[str] = mapped_column(String(40), default="")
    lease_expires_at: Mapped[str] = mapped_column(String(40), default="")
    last_event_at: Mapped[str] = mapped_column(String(40), default="")


class TrainingLog(Base):
    __tablename__ = "training_logs"

    log_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    task_id: Mapped[str] = mapped_column(String(64), index=True)
    timestamp: Mapped[str] = mapped_column(String(40), index=True)
    level: Mapped[str] = mapped_column(String(16), default="INFO")
    message: Mapped[str] = mapped_column(Text)
    source: Mapped[str] = mapped_column(String(16), default="worker")


class TrainingMetricRow(Base):
    __tablename__ = "training_metrics"
    __table_args__ = (UniqueConstraint("task_id", "epoch", name="uq_metric_task_epoch"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    task_id: Mapped[str] = mapped_column(String(64), index=True)
    epoch: Mapped[int] = mapped_column(Integer)
    train_loss: Mapped[float] = mapped_column()
    validation_loss: Mapped[float] = mapped_column()
    train_accuracy: Mapped[float] = mapped_column()
    validation_accuracy: Mapped[float] = mapped_column()
    validation_precision: Mapped[float] = mapped_column()
    validation_recall: Mapped[float] = mapped_column()
    validation_f1: Mapped[float] = mapped_column()
    recorded_at: Mapped[str] = mapped_column(String(40))


class TrainingCheckpointRow(Base):
    __tablename__ = "training_checkpoints"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    task_id: Mapped[str] = mapped_column(String(64), index=True)
    epoch: Mapped[int] = mapped_column(Integer)
    checkpoint_path: Mapped[str] = mapped_column(Text)
    validation_loss: Mapped[float] = mapped_column()
    created_at: Mapped[str] = mapped_column(String(40))


class TrainingResultRow(Base):
    __tablename__ = "training_results"

    task_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    result_id: Mapped[str] = mapped_column(String(64))
    final_metrics_json: Mapped[str] = mapped_column(Text, default="{}")
    best_checkpoint_json: Mapped[str] = mapped_column(Text, default="{}")
    model_artifact_path: Mapped[str] = mapped_column(Text, default="")
    manifest_path: Mapped[str] = mapped_column(Text, default="")
    dataset_version: Mapped[str] = mapped_column(String(64), default="")
    completed_at: Mapped[str] = mapped_column(String(40), default="")
    evaluation_status: Mapped[str] = mapped_column(String(24), default="not_evaluated")
    evaluation_summary: Mapped[str] = mapped_column(Text, default="")
    simulated: Mapped[bool] = mapped_column(Boolean, default=False)
    training_profile: Mapped[str] = mapped_column(String(32), default="smoke_test")


class WorkerEvent(Base):
    __tablename__ = "worker_events"

    event_id: Mapped[str] = mapped_column(String(80), primary_key=True)
    task_id: Mapped[str] = mapped_column(String(64), index=True)
    event_type: Mapped[str] = mapped_column(String(40))
    received_at: Mapped[str] = mapped_column(String(40))
    payload_json: Mapped[str] = mapped_column(Text, default="{}")


# P12 模型仓库表：在既有表定义之后导入，确保 init_db() 一并建表
from . import model_repo as _model_repo  # noqa: E402,F401  (side-effect import)
