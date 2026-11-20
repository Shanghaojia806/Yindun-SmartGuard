"""模型产物：SHA-256、清单、Ed25519 签名。

约束：
- 只对真实写出的文件计算 SHA-256，不使用随机字符串；
- 未配置签名私钥时 signatureStatus = missing，绝不伪造签名；
- 私钥只从环境变量或本地密钥文件读取，不写入 Git / 前端 / 日志 / 清单。
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

TRAINING_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = TRAINING_DIR.parents[1]
ARTIFACTS_ROOT = TRAINING_DIR / "artifacts"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def relative_path(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPO_ROOT).as_posix()
    except ValueError:
        return path.name


def _load_private_key() -> tuple[Any, str] | None:
    """返回 (私钥对象, keyLabel)；未配置或读取失败时返回 None。"""
    key_label = os.environ.get("P11R_ED25519_KEY_LABEL", "development")
    key_path = os.environ.get("P11R_ED25519_PRIVATE_KEY_PATH", "")
    if not key_path:
        candidate = TRAINING_DIR / "keys" / "dev_ed25519_private.pem"
        if candidate.exists():
            key_path = str(candidate)
    if not key_path:
        return None
    try:
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric import ed25519

        raw = Path(key_path).read_bytes()
        if b"PRIVATE KEY" in raw:
            key = serialization.load_pem_private_key(raw, password=None)
        else:
            key = ed25519.Ed25519PrivateKey.from_private_bytes(base64.b64decode(raw))
        return key, key_label
    except Exception:  # noqa: BLE001 - 密钥不可用时明确降级为 missing
        return None


def sign_manifest(manifest: dict[str, Any]) -> dict[str, Any]:
    """对清单（不含签名块）签名；缺失私钥时返回 missing 状态。"""
    unsigned = {key: value for key, value in manifest.items() if key not in {"signature", "signatureStatus"}}
    payload = json.dumps(unsigned, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")

    loaded = _load_private_key()
    if loaded is None:
        return {
            "signatureStatus": "missing",
            "signature": {
                "algorithm": "ed25519",
                "keyLabel": "unconfigured",
                "signedAt": "",
                "signatureBase64": "",
                "note": "未配置签名私钥，P12 正式发布必须保持阻断。",
            },
        }

    key, key_label = loaded
    signature = key.sign(payload)
    public_key = key.public_key()
    from cryptography.hazmat.primitives import serialization

    public_bytes = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    key_dir = TRAINING_DIR / "keys"
    key_dir.mkdir(parents=True, exist_ok=True)
    (key_dir / "dev_ed25519_public.pem").write_bytes(public_bytes)

    return {
        "signatureStatus": "signed",
        "signature": {
            "algorithm": "ed25519",
            "keyLabel": key_label,
            "isDevelopmentKey": key_label != "production",
            "signedAt": _now_iso(),
            "signatureBase64": base64.b64encode(signature).decode("ascii"),
            "publicKeyPath": relative_path(key_dir / "dev_ed25519_public.pem"),
        },
    }


def write_manifest(
    *,
    task_id: str,
    model_path: Path,
    dataset_version_id: str,
    framework_version: str,
    extra: dict[str, Any] | None = None,
) -> tuple[Path, dict[str, Any]]:
    task_dir = model_path.parent
    manifest: dict[str, Any] = {
        "artifactId": f"ART-{task_id}-{uuid.uuid4().hex[:8]}",
        "taskId": task_id,
        "artifactType": "smoke_test",
        "fileName": model_path.name,
        "artifactPath": relative_path(model_path),
        "fileSize": model_path.stat().st_size,
        "sha256": sha256_file(model_path),
        "framework": "pytorch",
        "frameworkVersion": framework_version,
        "modelFormat": "torch.state_dict",
        "datasetVersionId": dataset_version_id,
        "createdAt": _now_iso(),
        "trainingProfile": "smoke_test",
        "productionReady": False,
        "publishBlockedReason": "Smoke Test 产物仅用于验证训练链路，禁止进入正式模型发布流程。",
    }
    if extra:
        manifest.update(extra)

    manifest.update(sign_manifest(manifest))
    manifest_path = task_dir / "artifact-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest_path, manifest
