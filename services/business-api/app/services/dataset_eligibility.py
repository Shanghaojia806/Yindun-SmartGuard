"""服务端数据集资格校验。

前端传入的 licenseApproved / qualityPassed / manualReviewStatus 等结论
一律不采信。创建任务、重试任务、Worker 开始训练前都基于
dataset_version_snapshots 重新计算七项阻断检查。
"""

from __future__ import annotations

from typing import Any

from ..models import DatasetVersionSnapshot

MIN_TRAIN_SAMPLES = 1
MIN_VALIDATION_SAMPLES = 1
MIN_TEST_SAMPLES = 1
MIN_TOTAL_SAMPLES = 10


def _check(key: str, label: str, passed: bool, detail: str) -> dict[str, Any]:
    return {"key": key, "label": label, "passed": passed, "detail": detail, "blocking": True}


def evaluate_snapshot(snapshot: DatasetVersionSnapshot) -> dict[str, Any]:
    split_ok = (
        snapshot.train_count >= MIN_TRAIN_SAMPLES
        and snapshot.validation_count >= MIN_VALIDATION_SAMPLES
        and snapshot.test_count >= MIN_TEST_SAMPLES
        and snapshot.sample_count >= MIN_TOTAL_SAMPLES
    )
    leakage_requires_review = snapshot.leakage_risk in {"partial_group_split", "unverified"}
    manual_review_ok = snapshot.manual_review_status != "pending" and (
        not leakage_requires_review or snapshot.manual_review_status == "approved"
    )

    checks = [
        _check(
            "source",
            "数据集来源明确",
            snapshot.source_status == "clear",
            "来源状态为 clear，已由服务端快照登记"
            if snapshot.source_status == "clear"
            else f"来源状态为 {snapshot.source_status}，无法确认数据来源",
        ),
        _check(
            "license",
            "使用许可允许模型训练",
            snapshot.license_status == "allowed" and not snapshot.revoked,
            "许可状态为 allowed"
            if snapshot.license_status == "allowed" and not snapshot.revoked
            else "许可未允许训练或数据集版本已被撤销",
        ),
        _check(
            "quality",
            "样本通过必要质检",
            snapshot.quality_status == "passed",
            "质检状态为 passed"
            if snapshot.quality_status == "passed"
            else f"质检状态为 {snapshot.quality_status}",
        ),
        _check(
            "duplicate",
            "没有未处理的重复数据",
            snapshot.duplicate_status == "resolved",
            "重复数据状态为 resolved"
            if snapshot.duplicate_status == "resolved"
            else f"重复数据状态为 {snapshot.duplicate_status}",
        ),
        _check(
            "version_status",
            "版本状态满足训练要求",
            snapshot.version_status == "published" and not snapshot.revoked,
            "版本已发布，可用于训练"
            if snapshot.version_status == "published" and not snapshot.revoked
            else "仅已发布且未撤销的版本可用于训练",
        ),
        _check(
            "manual_review",
            "同源泄漏人工复核完成",
            manual_review_ok,
            "同源泄漏人工复核已满足要求"
            if manual_review_ok
            else "存在同源拆分泄漏风险，人工复核尚未完成",
        ),
        _check(
            "split",
            "训练/验证/测试拆分有效",
            split_ok,
            f"训练 {snapshot.train_count} / 验证 {snapshot.validation_count} / 测试 {snapshot.test_count}"
            if split_ok
            else "拆分存在空集合或总样本量不足",
        ),
    ]
    blocked = [item["detail"] for item in checks if not item["passed"]]
    if snapshot.revoked:
        blocked.insert(0, "数据集版本已被撤销，禁止继续训练")
    return {
        "eligible": len(blocked) == 0,
        "checks": checks,
        "blockedReasons": blocked,
    }


def snapshot_to_check(snapshot: DatasetVersionSnapshot) -> dict[str, Any]:
    result = evaluate_snapshot(snapshot)
    return {
        "datasetId": snapshot.dataset_id,
        "datasetName": snapshot.dataset_name,
        "datasetType": snapshot.dataset_type,
        "versionId": snapshot.dataset_version_id,
        "versionNumber": snapshot.dataset_version,
        "versionStatus": snapshot.version_status,
        "manualReviewStatus": snapshot.manual_review_status,
        "sampleCount": snapshot.sample_count,
        "trainCount": snapshot.train_count,
        "validationCount": snapshot.validation_count,
        "testCount": snapshot.test_count,
        "eligible": result["eligible"],
        "checks": result["checks"],
        "blockedReasons": result["blockedReasons"],
        "checkedAt": snapshot.snapshot_created_at,
        "note": (
            snapshot.note
            or "校验基于服务端数据集版本快照；前端提交的资格结论不作为放行依据。"
        ),
    }
