from __future__ import annotations

from typing import Any, Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class PageResult(BaseModel, Generic[T]):
    items: list[T]
    page: int
    pageSize: int
    total: int


def envelope(data: Any) -> dict[str, Any]:
    """所有成功响应统一包一层 data。"""
    return {"data": data}
