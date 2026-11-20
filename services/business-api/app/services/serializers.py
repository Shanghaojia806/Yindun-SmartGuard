"""任务与结果序列化：输出前端 P11 契约要求的 camelCase 字段。"""

from __future__ import annotations

import json
from typing import Any

from ..core.time import display_time
from ..models import TrainingResultRow, TrainingTask

STAGE_LABELS = {
    "PENDING": "等待执行",
    "RUNNING": "训练中",
    "SUCCEEDED": "训练完成",
    "FAILED": "训练失败",
    "CANCELED": "已取消",
}


def _loads(raw: str, fallback: Any) -> Any:
    try:
        value = json.loads(raw) if raw else fallback
        return value if value is not None else fallback
    except json.JSONDecodeError:
        return fallback


def task_to_dict(task: TrainingTask, current_epoch: int = 0) -> dict[str, Any]:
    parameters = _loads(task.parameters_json, {})
    model_config = _loads(task.model_config_json, {})
    split = _loads(task.dataset_split_json, {})

    updated = (
        display_time(task.last_event_at)
        or display_time(task.heartbeat_at)
        or display_time(task.started_at)
        or display_time(task.created_at)
    )
    return {
        "taskId": task.task_id,
        "taskName": task.task_name,
        "taskDescription": task.task_description,
        "modelType": task.model_type,
        "modelConfig": model_config,
        "datasetId": task.dataset_id,
        "datasetName": task.dataset_name,
        "datasetVersionId": task.dataset_version_id,
        "datasetVersion": task.dataset_version,
        "datasetSplit": split,
        "trainingParameters": parameters,
        "taskStatus": task.status.lower(),
        "executionMode": task.execution_mode,
        "createdAt": display_time(task.created_at),
        "startedAt": display_time(task.started_at),
        "completedAt": display_time(task.completed_at),
        "createdBy": task.created_by,
        "errorMessage": task.error_message,
        "progress": {
            "currentEpoch": current_epoch,
            "totalEpochs": int(parameters.get("epochs", 0)),
            "stage": STAGE_LABELS.get(task.status, task.status),
            "updatedAt": updated,
        },
        "cancelRequestedAt": display_time(task.cancel_requested_at) or None,
        "retryOfTaskId": task.retry_of_task_id or None,
        "retriedByTaskId": task.retried_by_task_id or None,
        "attempt": task.attempt,
        # 扩展字段：前端未声明时会被忽略，但便于排查真实 Worker 来源
        "trainingProfile": task.training_profile,
        "workerId": task.worker_id or None,
    }


def result_to_dict(row: TrainingResultRow) -> dict[str, Any]:
    return {
        "resultId": row.result_id,
        "taskId": row.task_id,
        "finalMetrics": _loads(row.final_metrics_json, {}),
        "bestCheckpoint": _loads(row.best_checkpoint_json, {}),
        "modelArtifactPath": row.model_artifact_path,
        "datasetVersion": row.dataset_version,
        "completedAt": display_time(row.completed_at),
        "evaluationStatus": row.evaluation_status,
        "evaluationSummary": row.evaluation_summary,
        "simulated": row.simulated,
    }
