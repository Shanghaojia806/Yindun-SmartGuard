"""P12 模型仓库请求体。"""

from __future__ import annotations

from pydantic import BaseModel, Field


class ImportModelIn(BaseModel):
    trainingTaskId: str
    modelName: str = ""
    description: str = ""
    operator: str = "演示管理员"
    roleLabel: str = "系统管理员（模拟）"


class ReviewActionIn(BaseModel):
    channel: str = "internal_test"
    notes: str = ""
    operator: str = "演示管理员"
    roleLabel: str = "系统管理员（模拟）"


class RejectModelIn(BaseModel):
    reviewer: str = "演示管理员"
    roleLabel: str = "系统管理员（模拟）"
    reason: str = Field(min_length=1, max_length=500)


class PublishModelIn(BaseModel):
    channel: str = "internal_test"
    deviceScope: str = ""
    notes: str = ""
    operator: str = "演示管理员"
    roleLabel: str = "系统管理员（模拟）"


class WithdrawModelIn(BaseModel):
    reason: str = ""
    impact: str = ""
    operator: str = "演示管理员"
    roleLabel: str = "系统管理员（模拟）"


class RollbackModelIn(BaseModel):
    fromVersionId: str
    toVersionId: str
    notes: str = ""
    operator: str = "演示管理员"
    roleLabel: str = "系统管理员（模拟）"


class CompatibilityIn(BaseModel):
    platform: str = "android"
    minOsVersion: str = ""
    maxOsVersion: str = ""
    architecture: str = "arm64-v8a"
    inferenceRuntime: str = "pytorch_mobile"
    runtimeVersion: str = ""
    supportedDevices: list[str] = Field(default_factory=list)
    memoryRequirementMb: int = 0
    acceleratorSupport: list[str] = Field(default_factory=lambda: ["cpu"])
    compatibilityStatus: str = "compatible"
    testedAt: str = ""
    testedBy: str = "演示管理员"
    notes: str = ""
    isRealDeviceTest: bool = False
