package com.github.k3reviewer;

import com.github.k3reviewer.api.ApiException;
import com.github.k3reviewer.api.KimiClient;
import com.github.k3reviewer.config.Config;
import com.github.k3reviewer.git.GitDiff;
import com.github.k3reviewer.io.FileIO;
import com.github.k3reviewer.output.Reporter;
import com.github.k3reviewer.review.ReviewReport;
import com.github.k3reviewer.review.Reviewer;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * k3-code-reviewer 入口。
 *
 * <p>用法：
 * <pre>
 *   k3reviewer help                         # 显示用法（不需要 API Key）
 *   k3reviewer check                        # 校验 Key 与模型是否可用
 *   k3reviewer diff [--base &lt;ref&gt;] [--json] # 审查工作区相对基准的 git diff
 *   k3reviewer file &lt;path&gt; [--json]         # 审查单个文件
 *   k3reviewer files &lt;path...&gt; [--json]     # 审查多个文件
 * </pre>
 *
 * <p>退出码：
 * <pre>
 *   0  正常（审查通过 / 无改动）
 *   1  用法错误
 *   2  配置错误（缺少 API Key 等）
 *   3  API 错误（认证失败、限流、模型不可用）
 *   4  IO / 环境错误（非 Git 仓库、文件不存在、读取失败）
 *   10 审查结论为 NEEDS_FIX
 * </pre>
 */
public final class Main {

    public static void main(String[] args) {
        // Windows 下 System.out 默认按 GBK 输出，重定向会乱码；强制 UTF-8。
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        try {
            int code = run(args);
            System.exit(code);
        } catch (IllegalStateException e) {
            System.err.println("配置错误：" + e.getMessage());
            System.exit(2);
        } catch (ApiException e) {
            System.err.println("API 错误：" + e.getMessage());
            System.exit(3);
        } catch (IOException e) {
            System.err.println("IO 错误：" + e.getMessage());
            System.exit(4);
        } catch (Exception e) {
            System.err.println("未预期错误：");
            e.printStackTrace(System.err);
            System.exit(5);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return 1;
        }

        List<String> rest = new ArrayList<>(List.of(args));
        String cmd = rest.remove(0);

        // help 与未知命令不依赖配置初始化，保证任何环境下都能看到用法。
        if (cmd.equals("help") || cmd.equals("-h") || cmd.equals("--help")) {
            printUsage();
            return 0;
        }

        boolean json = rest.contains("--json");
        rest.removeIf("--json"::equals);

        switch (cmd) {
            case "check":
                return check();
            case "diff":
                return diff(rest, json);
            case "file":
                return file(rest, json);
            case "files":
                return files(rest, json);
            default:
                System.err.println("未知命令：" + cmd);
                printUsage();
                return 1;
        }
    }

    private static int check() throws Exception {
        Config config = new Config();
        KimiClient client = new KimiClient(config);
        String models = client.listModels();
        System.out.println("连接成功。当前账号可用模型：" + models);
        return 0;
    }

    private static int diff(List<String> args, boolean json) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        GitDiff git = new GitDiff(root);
        if (!git.isGitRepo()) {
            System.err.println("当前目录不是 Git 仓库（请先 git init / 进入已有仓库）。");
            return 4;
        }
        String base = null;
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--base")) {
                if (i + 1 >= args.size()) {
                    System.err.println("用法：diff --base <ref>（--base 后缺少基准引用）");
                    return 1;
                }
                base = args.get(i + 1);
                i++;
            } else {
                System.err.println("未知参数：" + a + "（如需指定基准请用 --base <ref>）");
                return 1;
            }
        }
        String ref = base == null ? "HEAD" : base;
        String diffText = git.diffAgainst(ref);
        if (diffText.isBlank()) {
            System.out.println("工作区相对 " + ref + " 没有改动，无需审查。");
            return 0;
        }
        Config config = new Config();
        Reviewer reviewer = new Reviewer(config, new KimiClient(config));
        ReviewReport report = reviewer.review("git diff vs " + ref, diffText);
        new Reporter().print(List.of(report), json);
        return "NEEDS_FIX".equals(report.verdict()) ? 10 : 0;
    }

    private static int file(List<String> args, boolean json) throws Exception {
        if (args.isEmpty()) {
            System.err.println("用法：file <路径> [--json]");
            return 1;
        }
        if (args.size() > 1) {
            System.err.println("file 只接受一个文件；审查多个文件请用 files 命令。");
            return 1;
        }
        Path p = Path.of(args.get(0)).toAbsolutePath();
        if (!Files.isRegularFile(p)) {
            System.err.println("文件不存在：" + p);
            return 4;
        }
        if (FileIO.tooLarge(p) || FileIO.looksBinary(p)) {
            System.err.println("跳过：" + p + "（超过大小上限或疑似二进制文件）");
            return 4;
        }
        String content = FileIO.readText(p);
        Config config = new Config();
        Reviewer reviewer = new Reviewer(config, new KimiClient(config));
        ReviewReport report = reviewer.review(p.toString(), content);
        new Reporter().print(List.of(report), json);
        return "NEEDS_FIX".equals(report.verdict()) ? 10 : 0;
    }

    private static int files(List<String> args, boolean json) throws Exception {
        if (args.isEmpty()) {
            System.err.println("用法：files <路径...> [--json]");
            return 1;
        }
        Config config = new Config();
        Reviewer reviewer = new Reviewer(config, new KimiClient(config));
        List<ReviewReport> reports = new ArrayList<>();
        boolean anyFailed = false;
        for (String a : args) {
            Path p = Path.of(a).toAbsolutePath();
            if (!Files.isRegularFile(p)) {
                System.err.println("跳过不存在的文件：" + p);
                anyFailed = true;
                continue;
            }
            if (FileIO.tooLarge(p) || FileIO.looksBinary(p)) {
                System.err.println("跳过：" + p + "（超过大小上限或疑似二进制文件）");
                anyFailed = true;
                continue;
            }
            try {
                reports.add(reviewer.review(p.toString(), FileIO.readText(p)));
            } catch (Exception e) {
                System.err.println("审查失败（" + a + "）：" + e.getMessage());
                anyFailed = true;
            }
        }
        if (reports.isEmpty()) {
            System.err.println("没有可审查的文件（全部跳过或失败）。");
            return 4;
        }
        new Reporter().print(reports, json);
        if (anyFailed || reports.stream().anyMatch(r -> "NEEDS_FIX".equals(r.verdict()))) {
            return 10;
        }
        return 0;
    }

    private static void printUsage() {
        System.out.println("""
                k3-code-reviewer —— 基于 Kimi K3 的 AI 代码审查 CLI

                用法：
                  k3reviewer help                      显示本说明（无需 API Key）
                  k3reviewer check                     校验 Key 与模型可用性
                  k3reviewer diff [--base <ref>] [--json]  审查工作区 git diff（默认对比 HEAD）
                  k3reviewer file <path> [--json]      审查单个文件（超过 256KB 或二进制文件跳过）
                  k3reviewer files <path...> [--json]  审查多个文件

                环境变量：
                  MOONSHOT_API_KEY  必填，Kimi 开放平台 API Key
                  K3_BASE_URL       可选，Anthropic 兼容端点（默认 https://api.moonshot.cn/anthropic）
                  K3_MODEL          可选，模型名（默认 kimi-k3）
                  K3_MAX_TOKENS     可选，单次回复最大 token（默认 8192）

                退出码：
                  0  正常（审查通过 / 无改动）
                  1  用法错误
                  2  配置错误（缺少 API Key 等）
                  3  API 错误（认证失败、限流、模型不可用）
                  4  IO / 环境错误（非 Git 仓库、文件不存在、读取失败）
                  10 审查结论为 NEEDS_FIX

                示例：
                  $env:MOONSHOT_API_KEY="sk-..."
                  k3reviewer check
                  k3reviewer diff
                  k3reviewer file src/main/java/com/github/k3reviewer/Main.java --json""");
    }
}
