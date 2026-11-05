"""仅在 CI 临时目录解码调试签名，不输出密钥或密码。"""
import base64
import os
from pathlib import Path

encoded = os.environ.get("WIFI_WRENCH_DEBUG_KEYSTORE_BASE64") or os.environ.get("APP3_DEBUG_KEYSTORE_BASE64", "")
password = os.environ.get("WIFI_WRENCH_DEBUG_STORE_PASSWORD") or os.environ.get("APP3_DEBUG_STORE_PASSWORD")
if not encoded or not password:
    raise SystemExit("Repository debug signing secrets are required for update-compatible test APKs.")
runner_temp = Path(os.environ["RUNNER_TEMP"])
payload = base64.b64decode(encoded, validate=True)
for name in ("wifi-wrench-debug.p12", "app3-debug.p12"):
    target = runner_temp / name
    target.write_bytes(payload)
    target.chmod(0o600)
