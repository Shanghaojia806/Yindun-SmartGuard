"""统一时间工具：数据库存 UTC ISO 字符串，接口按本地时间展示。

SQLite 对时区支持有限，本项目统一使用固定格式的 UTC ISO 字符串，
既可按字典序比较租约时间，也可安全落库。
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

SHANGHAI = timezone(timedelta(hours=8))


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def utc_iso(value: datetime | None = None) -> str:
    moment = value or now_utc()
    return moment.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def display_time(value: str | None) -> str:
    """把库内 UTC ISO 转为东八区展示时间，保持与 P11 Mock 页面一致。"""
    if not value:
        return ""
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return value
    return parsed.astimezone(SHANGHAI).strftime("%Y-%m-%d %H:%M")


def lease_deadline(seconds: int) -> str:
    return utc_iso(now_utc() + timedelta(seconds=seconds))


def is_expired(value: str | None, reference: str | None = None) -> bool:
    if not value:
        return True
    return value < (reference or utc_iso())
