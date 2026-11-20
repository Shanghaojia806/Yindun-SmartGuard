"""训练回调上报：事件 ID、日志、指标、检查点、心跳与取消检查。"""

from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Any

from ..worker.client import WorkerClient


def _utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


class TrainingReporter:
    def __init__(self, client: WorkerClient, task_id: str, lease_seconds: int = 120) -> None:
        self.client = client
        self.task_id = task_id
        self.lease_seconds = lease_seconds

    def _event(self, event_type: str, payload: dict[str, Any]) -> dict[str, Any]:
        return {
            "eventId": f"{self.task_id}-{event_type}-{uuid.uuid4().hex[:12]}",
            "eventType": event_type,
            "timestamp": _utc_now(),
            "workerId": self.client.worker_id,
            "payload": payload,
        }

    def _send(self, event_type: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self.client.send_event(self.task_id, self._event(event_type, payload))

    # ===== 事件 =====

    def started(self, message: str = "开始执行 CPU Smoke Test") -> None:
        self._send("task.started", {"message": message})

    def log(self, message: str, level: str = "INFO") -> None:
        self._send("task.log", {"level": level, "message": message})

    def metric(
        self,
        epoch: int,
        train_loss: float,
        validation_loss: float,
        train_accuracy: float,
        validation_accuracy: float,
        precision: float,
        recall: float,
        f1: float,
    ) -> None:
        self._send(
            "task.metric",
            {
                "epoch": epoch,
                "trainLoss": round(float(train_loss), 6),
                "validationLoss": round(float(validation_loss), 6),
                "trainAccuracy": round(float(train_accuracy), 6),
                "validationAccuracy": round(float(validation_accuracy), 6),
                "validationPrecision": round(float(precision), 6),
                "validationRecall": round(float(recall), 6),
                "validationF1": round(float(f1), 6),
            },
        )

    def checkpoint(self, epoch: int, checkpoint_path: str, validation_loss: float) -> None:
        self._send(
            "task.checkpoint",
            {
                "epoch": epoch,
                "checkpointPath": checkpoint_path,
                "validationLoss": round(float(validation_loss), 6),
            },
        )

    def succeeded(
        self,
        *,
        final_metrics: dict[str, Any],
        best_checkpoint: dict[str, Any],
        model_artifact_path: str,
        manifest_path: str,
        dataset_version: str,
    ) -> None:
        self._send(
            "task.succeeded",
            {
                "resultId": f"R-{self.task_id}",
                "finalMetrics": final_metrics,
                "bestCheckpoint": best_checkpoint,
                "modelArtifactPath": model_artifact_path,
                "manifestPath": manifest_path,
                "datasetVersion": dataset_version,
                "evaluationStatus": "not_evaluated",
                "evaluationSummary": (
                    "CPU Smoke Test 产物：仅验证训练链路，未进行反诈能力评估，"
                    "不允许进入正式模型发布流程。"
                ),
            },
        )

    def failed(self, message: str) -> None:
        self._send("task.failed", {"message": message})

    def canceled(self, message: str = "训练执行器已安全停止") -> None:
        self._send("task.canceled", {"message": message})

    # ===== 心跳 =====

    def heartbeat(self) -> bool:
        """续约并返回是否收到取消请求。"""
        response = self.client.heartbeat(self.task_id, self.lease_seconds)
        return bool(response.get("cancelRequested"))
