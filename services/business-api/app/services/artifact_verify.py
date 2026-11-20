"""P12 模型产物完整性校验。

服务端重新计算 SHA-256，并使用 Ed25519 公钥验签；
所有结论只来自真实文件，绝不接受前端提交的“已校验”状态。
"""

from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path
from typing import Any

from ..core.config import REPO_ROOT, settings

DEFAULT_PUBLIC_KEY = REPO_ROOT / "ml" / "training" / "keys" / "dev_ed25519_public.pem"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def resolve_repo_path(raw: str, fallback_dir: Path | None = None) -> Path:
    """把清单/结果中的路径解析为真实文件路径。

    优先按仓库根相对路径解析，其次回退到清单所在目录。
    """
    if not raw:
        return REPO_ROOT / "__missing__"
    candidate = Path(raw)
    if candidate.is_absolute():
        return candidate
    repo_candidate = REPO_ROOT / raw
    if repo_candidate.exists():
        return repo_candidate
    if fallback_dir is not None:
        return fallback_dir / candidate.name
    return repo_candidate


def load_manifest(manifest_path: Path) -> dict[str, Any]:
    return json.loads(manifest_path.read_text(encoding="utf-8"))


def verify_file(manifest: dict[str, Any], manifest_path: Path) -> dict[str, Any]:
    """校验文件存在性、大小与 SHA-256。"""
    artifact_path = resolve_repo_path(str(manifest.get("artifactPath", "")), manifest_path.parent)
    result: dict[str, Any] = {
        "artifactPath": str(artifact_path),
        "exists": artifact_path.is_file(),
        "fileSize": 0,
        "manifestFileSize": int(manifest.get("fileSize", -1) or -1),
        "actualSha256": "",
        "manifestSha256": str(manifest.get("sha256", "")),
        "fileHashStatus": "not_available",
        "detail": "",
    }
    if not artifact_path.is_file():
        result["detail"] = "模型文件不存在"
        return result

    size = artifact_path.stat().st_size
    actual = sha256_file(artifact_path)
    result["fileSize"] = size
    result["actualSha256"] = actual

    if result["manifestFileSize"] not in (-1, size):
        result["fileHashStatus"] = "failed"
        result["detail"] = (
            f"文件大小与清单不一致（清单 {result['manifestFileSize']} / 实际 {size}）"
        )
        return result
    if not result["manifestSha256"] or actual != result["manifestSha256"]:
        result["fileHashStatus"] = "failed"
        result["detail"] = "SHA-256 与产物清单不一致"
        return result

    result["fileHashStatus"] = "verified"
    result["detail"] = "SHA-256 与产物清单一致"
    return result


def verify_signature(manifest: dict[str, Any]) -> dict[str, Any]:
    """使用 Ed25519 公钥验签。

    返回 status：verified / failed / not_verified。
    未签名不会被当作通过，开发密钥会被明确标记。
    """
    signature = manifest.get("signature") or {}
    signature_b64 = str(signature.get("signatureBase64") or "")
    status = str(manifest.get("signatureStatus") or "")
    key_label = str(signature.get("keyLabel") or "")
    is_development = bool(signature.get("isDevelopmentKey", False))

    result: dict[str, Any] = {
        "signatureStatus": "not_verified",
        "signature": signature_b64,
        "keyLabel": key_label,
        "isDevelopmentKey": is_development,
        "detail": "",
    }
    if status != "signed" or not signature_b64:
        result["detail"] = "产物未签名，不能进入灰度或正式发布"
        return result

    public_path = str(signature.get("publicKeyPath") or "")
    path = resolve_repo_path(public_path) if public_path else DEFAULT_PUBLIC_KEY
    if not path.is_file() and settings.ed25519_public_key_path:
        path = resolve_repo_path(settings.ed25519_public_key_path)
    if not path.is_file():
        result["signatureStatus"] = "failed"
        result["detail"] = "缺少可用的 Ed25519 公钥，无法验签"
        return result

    try:
        from cryptography.hazmat.primitives import serialization

        public_key = serialization.load_pem_public_key(path.read_bytes())
        unsigned = {
            key: value
            for key, value in manifest.items()
            if key not in {"signature", "signatureStatus"}
        }
        payload = json.dumps(
            unsigned, sort_keys=True, separators=(",", ":"), ensure_ascii=False
        ).encode("utf-8")
        public_key.verify(base64.b64decode(signature_b64), payload)
    except Exception as exc:  # noqa: BLE001
        result["signatureStatus"] = "failed"
        result["detail"] = f"Ed25519 验签失败（{type(exc).__name__}）"
        return result

    result["signatureStatus"] = "verified"
    result["detail"] = (
        "Ed25519 验签通过（开发密钥，仅限开发/受限内部测试）"
        if is_development
        else "Ed25519 验签通过"
    )
    return result
