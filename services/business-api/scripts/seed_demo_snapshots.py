"""登记演示用数据集版本快照。

用途：本地开发 / 联调时提供明确标记为「合成演示」的服务端快照，
使创建训练任务能通过服务端资格校验。

注意：
- 这里登记的是聚合快照，不是真实 A04 数据集；逐样本复核需等 P10 后端落地；
- 快照来源统一标记 synthetic_demo，前端与服务端均应展示为演示数据；
- 重复运行会覆盖同版本快照（以 dataset_version_id 唯一）。

运行（在 services/business-api 目录）：
    python scripts/seed_demo_snapshots.py
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.db import SessionLocal, init_db  # noqa: E402
from app.schemas.training import RegisterSnapshotIn  # noqa: E402
from app.services.training_service import register_snapshot  # noqa: E402

DEMO_SNAPSHOTS = [
    RegisterSnapshotIn(
        datasetId="DS-2026-004",
        datasetName="演示·屏幕多模态反诈数据集（合成）",
        datasetType="multimodal",
        datasetVersionId="VER-DS-2026-004-02",
        datasetVersion="v2.0.0",
        sampleCount=40,
        trainCount=28,
        validationCount=6,
        testCount=6,
        sourceStatus="clear",
        licenseStatus="allowed",
        qualityStatus="passed",
        duplicateStatus="resolved",
        leakageRisk="grouped",
        manualReviewStatus="not_required",
        versionStatus="published",
        snapshotSource="synthetic_demo",
        note="合成演示快照：仅用于验证 P11-R 训练链路，不代表真实反诈数据。",
    ),
    RegisterSnapshotIn(
        datasetId="DS-2026-004",
        datasetName="演示·屏幕多模态反诈数据集（合成）",
        datasetType="multimodal",
        datasetVersionId="VER-DS-2026-004-01",
        datasetVersion="v1.0.0",
        sampleCount=32,
        trainCount=22,
        validationCount=5,
        testCount=5,
        sourceStatus="clear",
        licenseStatus="unknown",
        qualityStatus="pending",
        duplicateStatus="pending",
        leakageRisk="grouped",
        manualReviewStatus="not_required",
        versionStatus="draft",
        snapshotSource="synthetic_demo",
        note="合成演示快照（未通过资格校验）：用于验证服务端阻断。",
    ),
    RegisterSnapshotIn(
        datasetId="DS-2026-005",
        datasetName="演示·文本反诈话术数据集（合成）",
        datasetType="text",
        datasetVersionId="VER-DS-2026-005-01",
        datasetVersion="v1.2.0",
        sampleCount=24,
        trainCount=16,
        validationCount=4,
        testCount=4,
        sourceStatus="clear",
        licenseStatus="allowed",
        qualityStatus="passed",
        duplicateStatus="resolved",
        leakageRisk="partial_group_split",
        manualReviewStatus="pending",
        versionStatus="published",
        snapshotSource="synthetic_demo",
        note="合成演示快照：存在同源拆分泄漏风险，人工复核未完成，应被阻断。",
    ),
]


def main() -> None:
    init_db()
    session = SessionLocal()
    try:
        for item in DEMO_SNAPSHOTS:
            result = register_snapshot(session, item)
            print(
                f"{item.datasetVersionId}: eligible={result['eligible']} "
                f"created={result['created']} status={result['versionStatus']}"
            )
    finally:
        session.close()


if __name__ == "__main__":
    main()
