# k3-code-reviewer

基于 **Kimi K3**（Anthropic 兼容端点）的 AI 代码审查命令行工具。输入 Git diff 或文件内容，K3 以"资深审查员"身份输出结构化的问题清单与总体结论。

> 本项目是"智能体协同开发"实验的实战延伸：把实验中"审查员智能体只依据代码证据下结论、逐条给出位置与建议"的监督原则，做成一个开箱即用的 CLI 工具。

## 特性

- **diff 审查**：直接审查工作区相对 HEAD（或任意基准）的未提交改动
- **单文件/多文件审查**：`file`、`files` 子命令按需审查
- **结构化输出**：默认终端可读文本，`--json` 输出机器可解析结果
- **监督式提示词**：要求按严重程度分类、每个问题附位置与修复建议、给出 PASS / NEEDS_FIX 结论
- **零重依赖**：仅 Jackson（JSON），HTTP 用 JDK 内置 `HttpClient`，git 调用系统命令
- **密钥安全**：API Key 只从环境变量读取，绝不硬编码、绝不进 Git

## 快速开始

要求：JDK 21+、Maven 3.9+、Git（可选，`diff` 子命令需要）。

```bash
# 1. 设置 API Key（Kimi 开放平台申请：https://platform.moonshot.cn）
export MOONSHOT_API_KEY="sk-你的Key"          # Windows PowerShell: $env:MOONSHOT_API_KEY="sk-你的Key"

# 2. 打包
mvn package

# 3. 校验连接与模型
java -jar target/k3-code-reviewer-1.0.0.jar check

# 4. 审查工作区 diff（在任意 Git 仓库内）
java -jar target/k3-code-reviewer-1.0.0.jar diff

# 5. 审查单个文件
java -jar target/k3-code-reviewer-1.0.0.jar file src/main/java/com/github/k3reviewer/Main.java
```

Windows 一键示例（PowerShell）：

```powershell
$env:MOONSHOT_API_KEY="sk-你的Key"
cd C:\Users\h\DoubaoWork\chats\2026-09-30\new-chat\k3-code-reviewer
mvn -q package
java -jar target\k3-code-reviewer-1.0.0.jar check
java -jar target\k3-code-reviewer-1.0.0.jar diff
```

## 命令一览

| 命令 | 说明 |
|---|---|
| `check` | 校验 Key 有效性并列出账号可用模型 |
| `diff [--base <ref>] [--json]` | 审查工作区相对 `HEAD`（或 `--base` 指定基准）的 diff |
| `file <path> [--json]` | 审查单个文件 |
| `files <path...> [--json]` | 审查多个文件（逐个调用，独立报告） |
| `help` | 显示用法 |

退出码：`0` 正常；`10` 审查结论为 NEEDS_FIX；`2` 配置错误；`3` API 错误；`4` IO/非 Git 仓库错误。

## 环境变量

| 变量 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `MOONSHOT_API_KEY` | 是 | — | Kimi 开放平台 API Key |
| `K3_BASE_URL` | 否 | `https://api.moonshot.cn/anthropic` | Anthropic 兼容端点 |
| `K3_MODEL` | 否 | `kimi-k3` | 模型名 |
| `K3_MAX_TOKENS` | 否 | `4096` | 单次回复最大 token |

## 架构

```
┌──────────────────────────────────────────────────────┐
│                    CLI (Main)                         │
│   diff / file / files / check   + 退出码规则          │
└──────────────┬───────────────────────────┬───────────┘
               │                           │
     ┌─────────▼─────────┐       ┌─────────▼─────────┐
     │      GitDiff      │       │     Reviewer      │
     │ git diff/ls-files │       │ 监督式提示词组装   │
     │  ProcessBuilder   │       │ 结论判定 PASS/…   │
     └─────────┬─────────┘       └─────────┬─────────┘
               │ 待审查内容                 │ 审查请求
     ┌─────────▼──────────────────────────▼─────────┐
     │                  KimiClient                  │
     │ JDK HttpClient → POST {base}/v1/messages     │
     │ 鉴权/限流/错误分类 · Anthropic 响应解析      │
     └──────────────────────┬───────────────────────┘
                            │
              ┌─────────────▼──────────────┐
              │   Kimi K3 (api.moonshot.cn) │
              └────────────────────────────┘
```

- `Config`：环境变量读取，单点配置
- `Reporter`：终端 / JSON 两种输出
- 测试：`src/test/java/com/github/k3reviewer/KimiClientTest.java` 用 JDK 内置 `HttpServer` 起本地 mock 端点，覆盖请求组装、响应解析、401 错误、PASS/NEEDS_FIX 判定（`mvn test` 全绿，无需联网）

## 示例输出

```text
================================================================
审查范围：git diff vs HEAD
模型：kimi-k3　　耗时：4210 ms　　输入：1284 字符
结论：PASS
----------------------------------------------------------------
审查结论：PASS

未发现严重问题。两条建议：
1.（建议）GitDiff.run 中命令列表可用 List.of 简化构造；
2.（建议）错误消息中的中文全角冒号与项目注释风格统一。
================================================================
```

完整示例见 [`docs/example-review.md`](docs/example-review.md)。

## 安全说明

- 请勿把 `MOONSHOT_API_KEY` 写入任何会被提交到仓库的文件（本项目已由 `.gitignore` 排除常见误提交文件）。
- 审查内容会发送到 Kimi 开放平台，请勿用其审查包含敏感信息的代码段。
- 本项目不保存任何审查记录，输出仅到终端。

## License

MIT
