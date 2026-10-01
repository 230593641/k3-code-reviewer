# -*- coding: utf-8 -*-
"""用 Python 子进程跑 K3 审查，字节级保留 UTF-8，避免 PowerShell 重定向破坏编码。"""
import os
import subprocess

base = r"C:\Users\h\DoubaoWork\chats\2026-09-30\new-chat\k3-code-reviewer"
jar = os.path.join(base, "target", "k3-code-reviewer-1.0.0.jar")
JAVA = r"D:\Environment\jdk-21\bin\java.exe"
env = os.environ.copy()
env["MOONSHOT_API_KEY"] = "sk-isHh1xmZTWcR0aQe3Fa1i4OXGUzq47P01tvxk6m7eJgvZPEP"

targets = [
    ("config", r"src\main\java\com\github\k3reviewer\config\Config.java"),
    ("main", r"src\main\java\com\github\k3reviewer\Main.java"),
]

for name, rel in targets:
    print(">>> reviewing", name)
    r = subprocess.run(
        [JAVA, "-jar", jar, "file", rel, "--json"],
        cwd=base, env=env, capture_output=True, timeout=720,
    )
    out = r.stdout.decode("utf-8", errors="replace")
    err = r.stderr.decode("utf-8", errors="replace")
    with open(os.path.join(base, "docs", f"review-{name}.json"), "wb") as f:
        f.write(r.stdout)
    if err.strip():
        print("ERR:", err.strip()[:200])
    print("exit:", r.returncode, "bytes:", len(r.stdout))
print("DONE")
