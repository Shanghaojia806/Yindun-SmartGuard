"""请求日志：只记录方法、路径、状态、耗时与 traceId。

禁止输出 Authorization、Worker Token、请求体与完整敏感路径。
"""

from __future__ import annotations

import logging
import time

from fastapi import Request

from .errors import current_trace_id

logger = logging.getLogger("p11r.api")


def configure_logging(level: str = "INFO") -> None:
    logging.basicConfig(
        level=level.upper(),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


async def log_request(request: Request, call_next):  # type: ignore[no-untyped-def]
    started = time.perf_counter()
    response = await call_next(request)
    elapsed_ms = (time.perf_counter() - started) * 1000
    logger.info(
        "%s %s -> %s %.1fms trace=%s",
        request.method,
        request.url.path,
        response.status_code,
        elapsed_ms,
        current_trace_id(),
    )
    return response
