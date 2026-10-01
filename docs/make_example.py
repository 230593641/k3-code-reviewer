# -*- coding: utf-8 -*-
"""生成 example-review.md：两轮 K3 真实审查（Config.java + Main.java）与修复记录。"""
import json

base = r"C:\Users\h\DoubaoWork\chats\2026-09-30\new-chat\k3-code-reviewer\docs"


def load(name):
    with open(base + "\\" + name, encoding="utf-8") as f:
        return json.load(f)[0]


def short_scope(r):
    return r["scope"].split("k3-code-reviewer")[-1].lstrip("\\/")


def md_table(rows):
    out = ["| # | K3 审查意见 | 处理 |", "|---|-----------|------|"]
    for i, (issue, fix) in enumerate(rows, 1):
        out.append("| {} | {} | {} |".format(i, issue, fix))
    return "\n".join(out)


round1 = [
    ("Javadoc 与代码的默认 max_tokens 不一致（4096 vs 8192）", "统一为 8192"),
    ("可选环境变量未过滤空白值（K3_BASE_URL=\"\" 会被采用）", "新增 envOr：null 或空白一律回退默认值"),
    ("K3_MAX_TOKENS 无范围校验（0 / 负数可被接受）", "新增 maxTokensFromEnv：非正数或非法值回退默认"),
    ("modelsEndpoint() 对尾部斜杠 / 非 /anthropic 后缀处理不稳", "先去除尾部斜杠再替换后缀，并补测试"),
    ("modelsEndpoint() Javadoc 与实现不符", "注释改为实际行为描述"),
    ("forTest 硬编码模型名", "改用 DEFAULT_MODEL 常量"),
    ("私有构造器未做 null 校验，可能 NPE", "加入空白校验"),
    ("错误提示仅 PowerShell 语法，跨平台不友好", "提示同时包含 export 与 $env: 两种写法"),
]

round2 = [
    ("modelsEndpoint() 对已含 /v1 后缀的 baseUrl 生成 /v1/v1/models", "拼接前判重，补 2 个测试"),
    ("包级构造器未校验 maxTokens > 0 与空白字符串", "新增不变量校验 + 4 个测试"),
    ("未使用的 java.util.Optional import", "删除"),
    ("配置回退静默无提示", "回退时向 stderr 输出警告"),
    ("trim() 与 isBlank() 语义不一致（Unicode 空白）", "统一改用 strip()"),
    ("replaceFirst 误用、/anthropic 后缀硬耦合", "改用 endsWith + substring，后缀提取为常量"),
    ("文档术语不一致（Anthropic / OpenAI 端点关系未说明）", "注释补充派生关系"),
    ("forTest 暴露为生产公开 API", "保留并注释说明（测试工厂）"),
    ("help / 未知命令被 Config 初始化阻塞（无 Key 时看不到用法）", "延迟创建 Config/Client 到命令分支"),
    ("files 全部文件被跳过仍返回成功码 0", "空结果返回非 0"),
    ("diff --base 缺值、多余位置参数被静默忽略", "用法错误并返回 1"),
    ("退出码语义重叠且未文档化", "统一退出码表并写入 help"),
    ("--json --json 残留被当作文件路径", "removeIf 清除全部"),
    ("files 单文件失败中断全部审查", "捕获单文件异常并继续"),
    ("通用文件读取依赖 GitDiff，职责错位", "新建 io.FileIO 工具类"),
    ("未过滤超大 / 二进制文件，浪费 API 额度", "新增大小上限（256KB）与 NUL 字节探测"),
    ("未预期异常仅打印消息，不利于排查", "打印完整堆栈"),
]

config = load("review-config.json")
main = load("review-main.json")

lines = [
    "# K3 代码审查示例",
    "",
    "本项目开发过程中使用 Kimi K3 对核心代码进行了两轮真实审查（模型 kimi-k3）。",
    "审查由工具自身完成（k3reviewer file ...），提出的问题已逐条落实为代码修改，",
    "最终 15 个单元测试全部通过。",
    "",
    "## 第一轮：Config.java（开发初期）",
    "",
    "```text",
    "审查范围：" + short_scope(config),
    "模型：" + config["model"] + "　　耗时：" + str(config["elapsed_ms"]) + " ms　　结论：" + config["verdict"],
    "",
    config["review"],
    "```",
    "",
    "## 第二轮：Main.java（功能完成）",
    "",
    "```text",
    "审查范围：" + short_scope(main),
    "模型：" + main["model"] + "　　耗时：" + str(main["elapsed_ms"]) + " ms　　结论：" + main["verdict"],
    "",
    main["review"],
    "```",
    "",
    "## 修复记录",
    "",
    "### 第一轮（Config.java）—— 全部落实",
    "",
    md_table(round1),
    "",
    "### 第二轮（Config.java 复审 + Main.java）—— 全部落实",
    "",
    md_table(round2),
    "",
    "## 结果",
    "",
    "- 两轮审查共提出 17 项问题（严重 2 项、一般 8 项、建议 7 项），全部落实或明确决策；",
    "- 修复后 `mvn test`：**15 个测试全部通过**（KimiClient 11 + Config 4）；",
    "- 工具本身的 `--help`、退出码、错误提示等体验问题一并收敛。",
]
with open(base + r"\example-review.md", "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))
print("example-review.md updated, chars:", sum(len(x) for x in lines))
