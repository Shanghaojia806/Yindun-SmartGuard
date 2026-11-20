"""SQLAlchemy 2 引擎与会话。

本地使用 SQLite（WAL + busy_timeout），生产预留 PostgreSQL：
只需要替换 P11R_DATABASE_URL，ORM 与队列适配器接口保持不变。
"""

from __future__ import annotations

from pathlib import Path

from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from .core.config import settings


class Base(DeclarativeBase):
    pass


def _ensure_sqlite_dir(url: str) -> None:
    prefix = "sqlite:///"
    if not url.startswith(prefix):
        return
    raw_path = url[len(prefix) :]
    if raw_path in {"", ":memory:"}:
        return
    Path(raw_path).parent.mkdir(parents=True, exist_ok=True)


_ensure_sqlite_dir(settings.database_url)

engine = create_engine(
    settings.database_url,
    connect_args={"check_same_thread": False} if settings.database_url.startswith("sqlite") else {},
    pool_pre_ping=True,
)


@event.listens_for(engine, "connect")
def _configure_sqlite(dbapi_connection, _connection_record) -> None:  # type: ignore[no-untyped-def]
    if not settings.database_url.startswith("sqlite"):
        return
    cursor = dbapi_connection.cursor()
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.execute("PRAGMA journal_mode=WAL")
    cursor.execute("PRAGMA busy_timeout=5000")
    cursor.close()


SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def init_db() -> None:
    from . import models  # noqa: F401

    Base.metadata.create_all(bind=engine)


def get_db():  # type: ignore[no-untyped-def]
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()
