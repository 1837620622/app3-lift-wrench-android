"""仅在 CI 临时目录解码调试签名，不输出密钥或密码。"""
import base64
import os
from pathlib import Path

encoded = os.environ.get("APP3_DEBUG_KEYSTORE_BASE64", "")
if not encoded or not os.environ.get("APP3_DEBUG_STORE_PASSWORD"):
    raise SystemExit("Repository debug signing secrets are required for update-compatible test APKs.")
target = Path(os.environ["RUNNER_TEMP"]) / "app3-debug.p12"
target.write_bytes(base64.b64decode(encoded, validate=True))
target.chmod(0o600)
