"""与 FastAPI 业务后端通信的 Worker HTTP 客户端。

Token 只从环境变量读取，只放在请求头，不写入日志与产物。
"""

from __future__ import annotations

import os
import time
from typing import Any

import httpx

DEFAULT_BASE_URL = os.environ.get("P11R_API_BASE_URL", "http://127.0.0.1:8000/api/v1")
DEFAULT_TIMEOUT = 15.0


class WorkerApiError(RuntimeError):
    def __init__(self, status_code: int, code: str, message: str) -> None:
        super().__init__(f"[{status_code}/{code}] {message}")
        self.status_code = status_code
        self.code = code


class LeaseLostError(RuntimeError):
    pass


class WorkerClient:
    def __init__(
        self,
        base_url: str | None = None,
        token: str | None = None,
        worker_id: str | None = None,
        timeout: float = DEFAULT_TIMEOUT,
    ) -> None:
        self.base_url = (base_url or DEFAULT_BASE_URL).rstrip("/")
        self.token = token or os.environ.get("P11R_WORKER_TOKEN", "")
        self.worker_id = worker_id or os.environ.get("P11R_WORKER_ID", "worker-cpu-01")
        if not self.token:
            raise RuntimeError("缺少 Worker Token：请设置环境变量 P11R_WORKER_TOKEN")
        self._http = httpx.Client(timeout=timeout)

    # ===== 内部 =====

    def _headers(self) -> dict[str, str]:
        return {"X-Worker-Token": self.token, "Accept": "application/json"}

    def _request(self, method: str, path: str, json: dict[str, Any] | None = None) -> Any:
        url = f"{self.base_url}{path}"
        last_error: Exception | None = None
        for attempt in range(3):
            try:
                response = self._http.request(method, url, json=json, headers=self._headers())
                if response.status_code >= 400:
                    try:
                        body = response.json().get("error", {})
                    except ValueError:
                        body = {}
                    raise WorkerApiError(
                        response.status_code,
                        str(body.get("code", "HTTP_ERROR")),
                        str(body.get("message", response.text[:200])),
                    )
                payload = response.json()
                return payload.get("data") if isinstance(payload, dict) else payload
            except WorkerApiError:
                raise
            except (httpx.HTTPError, OSError) as exc:
                last_error = exc
                time.sleep(1.0 + attempt)
        raise RuntimeError(f"请求业务后端失败：{last_error}")

    # ===== 队列 =====

    def claim(self, lease_seconds: int) -> dict[str, Any] | None:
        return self._request(
            "POST",
            "/internal/worker/tasks/claim",
            {"workerId": self.worker_id, "leaseSeconds": lease_seconds},
        )

    def fetch_context(self, task_id: str) -> dict[str, Any]:
        return self._request("GET", f"/internal/worker/tasks/{task_id}/context")

    def heartbeat(self, task_id: str, lease_seconds: int) -> dict[str, Any]:
        try:
            return self._request(
                "POST",
                f"/internal/worker/tasks/{task_id}/heartbeat",
                {"workerId": self.worker_id, "leaseSeconds": lease_seconds},
            )
        except WorkerApiError as exc:
            if exc.code == "LEASE_LOST":
                raise LeaseLostError(str(exc)) from exc
            raise

    def send_event(self, task_id: str, event: dict[str, Any]) -> dict[str, Any]:
        return self._request("POST", f"/internal/worker/tasks/{task_id}/events", event)

    def close(self) -> None:
        self._http.close()
