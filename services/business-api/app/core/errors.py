"""统一异常、traceId 与错误响应。"""

from __future__ import annotations

import uuid
from contextvars import ContextVar

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

trace_id_var: ContextVar[str] = ContextVar("trace_id", default="-")


class ApiError(Exception):
    def __init__(self, status_code: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message


def current_trace_id() -> str:
    return trace_id_var.get()


class TraceIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):  # type: ignore[no-untyped-def]
        trace_id = request.headers.get("X-Trace-Id") or f"trace-{uuid.uuid4().hex[:16]}"
        token = trace_id_var.set(trace_id)
        try:
            response = await call_next(request)
            response.headers["X-Trace-Id"] = trace_id
            return response
        finally:
            trace_id_var.reset(token)


def error_body(code: str, message: str) -> dict[str, object]:
    return {"error": {"code": code, "message": message, "traceId": current_trace_id()}}


async def api_error_handler(_: Request, exc: ApiError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content=error_body(exc.code, exc.message),
    )


async def validation_error_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
    first = exc.errors()[0] if exc.errors() else {}
    location = ".".join(str(part) for part in first.get("loc", []))
    message = first.get("msg", "请求参数不合法")
    return JSONResponse(
        status_code=400,
        content=error_body("PARAMETER_INVALID", f"{location}: {message}".strip(": ")),
    )


async def unhandled_error_handler(_: Request, exc: Exception) -> JSONResponse:
    return JSONResponse(
        status_code=500,
        content=error_body("INTERNAL_ERROR", "服务端处理失败，请使用 traceId 排查日志"),
    )
