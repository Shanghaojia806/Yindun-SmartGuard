"""模型产物独立校验脚本。

功能：
1. 重新计算产物文件 SHA-256，与清单对比；
2. 校验文件大小与清单一致；
3. 若清单包含 Ed25519 签名，使用对应公钥验证签名；
4. 提示 Smoke Test 产物不可作为正式反诈模型发布。

用法（仓库根目录）：
    python -m ml.training.verify_manifest ml/training/artifacts/T-2026-001/artifact-manifest.json
"""

from __future__ import annotations

import base64
import hashlib
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _resolve_path(raw: str, manifest_path: Path) -> Path:
    candidate = REPO_ROOT / raw
    if candidate.exists():
        return candidate
    fallback = manifest_path.parent / Path(raw).name
    return fallback


def verify(manifest_path: Path) -> int:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    artifact_path = _resolve_path(str(manifest.get("artifactPath", "")), manifest_path)

    print(f"清单：{manifest_path}")
    print(f"产物：{artifact_path}")

    if not artifact_path.exists():
        print("FAIL：产物文件不存在")
        return 1

    actual_size = artifact_path.stat().st_size
    if actual_size != int(manifest.get("fileSize", -1)):
        print(f"FAIL：文件大小不一致（清单 {manifest.get('fileSize')} / 实际 {actual_size}）")
        return 1
    print(f"PASS：文件大小一致（{actual_size} 字节）")

    actual_sha = sha256_file(artifact_path)
    if actual_sha != manifest.get("sha256"):
        print(f"FAIL：SHA-256 不一致（清单 {manifest.get('sha256')} / 实际 {actual_sha}）")
        return 1
    print(f"PASS：SHA-256 一致（{actual_sha}）")

    signature_status = manifest.get("signatureStatus")
    signature = manifest.get("signature") or {}
    if signature_status == "signed" and signature.get("signatureBase64"):
        try:
            from cryptography.hazmat.primitives import serialization
            from cryptography.hazmat.primitives.asymmetric import ed25519

            public_path = signature.get("publicKeyPath", "")
            if not public_path:
                print("FAIL：清单缺少 publicKeyPath，无法验证签名")
                return 1
            public_key = serialization.load_pem_public_key((REPO_ROOT / public_path).read_bytes())
            unsigned = {
                key: value
                for key, value in manifest.items()
                if key not in {"signature", "signatureStatus"}
            }
            payload = json.dumps(
                unsigned, sort_keys=True, separators=(",", ":"), ensure_ascii=False
            ).encode("utf-8")
            public_key.verify(base64.b64decode(signature["signatureBase64"]), payload)
            print(f"PASS：Ed25519 签名有效（keyLabel={signature.get('keyLabel')}）")
            if signature.get("isDevelopmentKey", True):
                print("WARN：使用开发密钥签名，不能作为生产发布依据")
        except Exception as exc:  # noqa: BLE001
            print(f"FAIL：签名验证失败：（{type(exc).__name__}）")
            return 1
    else:
        print("INFO：signatureStatus=missing，未签名产物不得进入正式发布流程")

    if manifest.get("artifactType") == "smoke_test":
        print("INFO：该产物为 CPU Smoke Test 结果，不是反诈模型，禁止正式发布。")
    return 0


def main() -> int:
    if len(sys.argv) != 2:
        print("用法：python -m ml.training.verify_manifest <manifest 路径>")
        return 2
    return verify(Path(sys.argv[1]).resolve())


if __name__ == "__main__":
    raise SystemExit(main())
