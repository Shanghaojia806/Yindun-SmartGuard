"""依赖注入：Worker Token 鉴权与管理端身份标识。"""

from __future__ import annotations

import secrets

from fastapi import Header, Request

from ..core.config import settings
from ..core.errors import ApiError


async def require_worker_token(x_worker_token: str | None = Header(default=None)) -> None:
    """内部回调鉴权。Token 只从环境变量读取，绝不回显到日志或响应。"""
    expected = settings.worker_token
    if not expected:
        raise ApiError(500, "WORKER_TOKEN_NOT_CONFIGURED", "服务端未配置 Worker Token")
    if not x_worker_token or not secrets.compare_digest(x_worker_token, expected):
        raise ApiError(401, "WORKER_UNAUTHORIZED", "Worker 鉴权失败")


async def require_admin(request: Request, authorization: str | None = Header(default=None)) -> None:
    """开发环境未配置 admin_token 时放行；配置后要求 Bearer 匹配。"""
    expected = settings.admin_token
    if not expected:
        return
    token = ""
    if authorization and authorization.lower().startswith("bearer "):
        token = authorization[7:].strip()
    if not token or not secrets.compare_digest(token, expected):
        raise ApiError(401, "ADMIN_UNAUTHORIZED", "管理端鉴权失败")


def operator_name(x_operator: str | None = Header(default=None)) -> str:
    name = (x_operator or "").strip()
    return name[:40] if name else "演示管理员"
