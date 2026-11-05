"""核对当前提交的文件范围；不替代完整凭据审计或历史清除。"""
import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[1]
paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
allowed_roots = ("App3Android/", ".github/workflows/", "tools/", "docs/")
allowed_files = {"README.md", "AGENTS.md", ".gitignore", ".gitattributes", "最新协议.docx"}
forbidden_extensions = {".dwg", ".dxf", ".sldprt", ".sldasm", ".pdf", ".db", ".sqlite", ".pcap", ".csv", ".xlsx", ".jks", ".keystore", ".apk", ".exe"}
errors = []
for name in filter(None, paths):
    p = pathlib.PurePosixPath(name)
    if not (name in allowed_files or name.startswith(allowed_roots)):
        errors.append(name)
    if p.suffix.lower() in forbidden_extensions or any(part in {".idea", "node_modules", ".gradle", "build", ".env"} for part in p.parts) or p.name == "local.properties":
        errors.append(name)
    if p.suffix == ".docx" and name != "最新协议.docx":
        errors.append(name)
if errors:
    print("Publication scope violations:\n" + "\n".join(sorted(set(errors))))
    sys.exit(1)
print("Source and authorized protocol scope verified.")
