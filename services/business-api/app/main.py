"""P11-R 训练业务后端入口。

启动：python -m uvicorn app.main:app --reload --port 8000
"""

from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from starlette.middleware.base import BaseHTTPMiddleware

from .api import admin_datasets, admin_models, admin_training, internal_worker
from .core.config import settings
from .core.errors import (
    ApiError,
    TraceIdMiddleware,
    api_error_handler,
    unhandled_error_handler,
    validation_error_handler,
)
from .core.logging import configure_logging, log_request
from .db import init_db


class RequestLogMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):  # type: ignore[no-untyped-def]
        return await log_request(request, call_next)


@asynccontextmanager
async def lifespan(_: FastAPI):
    configure_logging(settings.log_level)
    init_db()
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(RequestLogMiddleware)
app.add_middleware(TraceIdMiddleware)

app.add_exception_handler(ApiError, api_error_handler)
app.add_exception_handler(RequestValidationError, validation_error_handler)
app.add_exception_handler(Exception, unhandled_error_handler)

app.include_router(admin_training.router, prefix=settings.api_prefix)
app.include_router(admin_datasets.router, prefix=settings.api_prefix)
app.include_router(admin_models.router, prefix=settings.api_prefix)
app.include_router(internal_worker.router, prefix=settings.api_prefix)


@app.get("/health")
def health() -> dict:
    return {"data": {"status": "ok", "service": "p11r-business-api"}}
