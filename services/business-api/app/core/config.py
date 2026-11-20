"""P11-R 业务后端配置。

所有配置通过环境变量注入，禁止把 Worker Token、签名私钥写入代码或 Git。
"""

from __future__ import annotations

from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

BUSINESS_API_DIR = Path(__file__).resolve().parents[2]
REPO_ROOT = BUSINESS_API_DIR.parents[1]


class Settings(BaseSettings):
    app_name: str = "银盾智鉴 · P11-R 训练业务后端"
    api_prefix: str = "/api/v1"
    database_url: str = f"sqlite:///{(BUSINESS_API_DIR / 'data' / 'p11r.db').as_posix()}"
    worker_token: str = "dev-worker-token-change-me"
    admin_token: str = ""
    worker_lease_seconds: int = 120
    # P12：Ed25519 公钥路径（为空时回退 ml/training/keys/dev_ed25519_public.pem）
    ed25519_public_key_path: str = ""
    # P12：允许执行模型审核 / 发布 / 撤回 / 回滚的角色
    model_release_roles: str = "系统管理员,发布管理员"
    cors_origins: str = (
        "http://127.0.0.1:5173,http://127.0.0.1:5175,http://localhost:5173"
    )
    log_level: str = "INFO"

    model_config = SettingsConfigDict(
        env_prefix="P11R_",
        env_file=(BUSINESS_API_DIR / ".env", REPO_ROOT / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def cors_origin_list(self) -> list[str]:
        return [item.strip() for item in self.cors_origins.split(",") if item.strip()]


settings = Settings()
