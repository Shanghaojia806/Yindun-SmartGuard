"""请求体模型。字段名与前端契约保持一致（camelCase）。"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class TrainingParametersIn(BaseModel):
    epochs: int = 20
    batchSize: int = 32
    learningRate: float = 0.001
    optimizer: Literal["adam", "adamw", "sgd"] = "adamw"
    randomSeed: int = 42
    device: Literal["cpu", "cuda", "mps"] = "cpu"
    validationSplit: int = 15
    checkpointInterval: int = 5


class CreateTrainingTaskIn(BaseModel):
    taskName: str = ""
    taskDescription: str = ""
    modelType: str = ""
    taskType: str = ""
    datasetId: str = ""
    datasetVersionId: str = ""
    trainingParameters: TrainingParametersIn = Field(default_factory=TrainingParametersIn)


class RegisterSnapshotIn(BaseModel):
    datasetId: str
    datasetName: str
    datasetType: str = "text"
    datasetVersionId: str
    datasetVersion: str
    sampleCount: int = 0
    trainCount: int = 0
    validationCount: int = 0
    testCount: int = 0
    sourceStatus: Literal["clear", "missing", "unknown"] = "unknown"
    licenseStatus: Literal["allowed", "denied", "unknown"] = "unknown"
    qualityStatus: Literal["passed", "failed", "pending"] = "pending"
    duplicateStatus: Literal["resolved", "pending", "unknown"] = "unknown"
    leakageRisk: Literal["grouped", "partial_group_split", "unverified"] = "unverified"
    manualReviewStatus: Literal["not_required", "pending", "approved"] = "pending"
    versionStatus: Literal["draft", "reviewing", "published"] = "draft"
    revoked: bool = False
    snapshotSource: Literal["synthetic_demo", "registered", "p10_import"] = "synthetic_demo"
    note: str = ""
    createdBy: str = "演示管理员"


class RevokeSnapshotIn(BaseModel):
    reason: str = ""
    operator: str = "演示管理员"


class WorkerClaimIn(BaseModel):
    workerId: str
    leaseSeconds: int = 120


class WorkerHeartbeatIn(BaseModel):
    workerId: str
    leaseSeconds: int = 120
