package com.github.k3reviewer.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 从本地 Git 仓库取 diff。通过 {@link ProcessBuilder} 调用 git 命令，
 * 避免引入 jgit 等重量级依赖。
 */
public final class GitDiff {

    private final Path repoRoot;

    public GitDiff(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    /** 生成 diff 的上下文行数。 */
    public static final String DIFF_ARGS = "--unified=3";

    /**
     * 获取工作区相对 HEAD 的未提交改动。
     *
     * @param base 基准引用（默认 HEAD）；为 null 或空时用 HEAD
     * @return diff 文本；无改动时返回空字符串
     */
    public String diffAgainst(String base) throws IOException {
        String ref = (base == null || base.isBlank()) ? "HEAD" : base;
        List<String> cmd = new ArrayList<>(List.of("git", "diff", DIFF_ARGS, ref, "--"));
        return run(cmd);
    }

    /** 检查当前目录是否在 Git 仓库中。 */
    public boolean isGitRepo() {
        try {
            return !run(List.of("git", "rev-parse", "--is-inside-work-tree")).isBlank();
        } catch (IOException e) {
            return false;
        }
    }

    /** 列出仓库中已跟踪的 Java 源文件（供整仓审查）。 */
    public List<String> trackedJavaFiles() throws IOException {
        String out = run(List.of("git", "ls-files", "*.java"));
        if (out.isBlank()) {
            return List.of();
        }
        return out.lines().filter(l -> !l.isBlank()).toList();
    }

    private String run(List<String> command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoRoot.toFile());
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new IOException("无法执行命令 " + command.get(0) + "：" + e.getMessage(), e);
        }
        String output;
        try (var in = p.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int code;
        try {
            code = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("命令执行被中断", e);
        }
        if (code != 0) {
            throw new IOException("git 命令失败（退出码 " + code + "）：" + output.lines().findFirst().orElse(""));
        }
        return output;
    }
}
