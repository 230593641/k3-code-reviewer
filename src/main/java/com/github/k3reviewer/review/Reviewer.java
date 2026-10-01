package com.github.k3reviewer.review;

import com.github.k3reviewer.api.ApiException;
import com.github.k3reviewer.api.KimiClient;
import com.github.k3reviewer.config.Config;

import java.util.List;

/**
 * 审查器：把"待审查内容"包装成对审查员智能体的任务，并组装监督式提示词。
 *
 * <p>设计借鉴"智能体协同开发"实验中的审查原则：
 * <ul>
 *   <li>审查员只基于规格与代码证据下结论，不臆测；</li>
 *   <li>每个问题必须给出位置与修复建议；</li>
 *   <li>最终给出可执行的结论（通过 / 需修改）。</li>
 * </ul>
 */
public final class Reviewer {

    /** 审查员系统提示词（扮演资深 Java 审查员）。 */
    public static final String SYSTEM_PROMPT = """
            你是一位资深 Java 代码审查员。你的工作原则：
            1. 只根据下面提供的代码与规格证据下结论，不臆测不存在的背景；
            2. 按严重程度分类问题：严重（可导致错误/崩溃/安全问题）、一般（可维护性/健壮性）、建议（风格/改进）；
            3. 每个问题必须给出：位置（文件/行/符号）、问题描述、修复建议；
            4. 最后给出总体结论：PASS（无需修改）或 NEEDS_FIX（需修改，列出必须修复项）。
            使用中文回答，格式清晰，不要输出无关内容。""";

    private final Config config;
    private final KimiClient client;

    public Reviewer(Config config, KimiClient client) {
        this.config = config;
        this.client = client;
    }

    /**
     * 审查指定内容。
     *
     * @param scope 审查范围描述（如 "git diff (HEAD)" / "src/main/java/.../Main.java"）
     * @param content 待审查代码/差异文本
     */
    public ReviewReport review(String scope, String content) throws ApiException {
        if (content == null || content.isBlank()) {
            return ReviewReport.empty(config.model(), scope, "没有可审查的内容（diff 为空或文件为空）。");
        }
        long start = System.currentTimeMillis();
        String user = """
                请审查以下代码。

                审查范围：%s
                ---- 待审查内容开始 ----
                %s
                ---- 待审查内容结束 ----

                请按你的审查原则输出问题清单与总体结论。""".formatted(scope, content);

        String text = client.chat(SYSTEM_PROMPT, user);
        long elapsed = System.currentTimeMillis() - start;
        String verdict = text.contains("NEEDS_FIX") || text.contains("需修改")
                ? "NEEDS_FIX"
                : "PASS";
        return new ReviewReport(config.model(), scope, verdict, text, content.length(), elapsed);
    }

    /** 一次审查多个文件（顺序执行，每个文件独立调用）。 */
    public List<ReviewReport> reviewFiles(List<String> filePaths) throws ApiException {
        // 由 Main 逐文件调用 review 更直观，此处保留批量入口供扩展
        throw new UnsupportedOperationException("批量审查请逐文件调用 review()");
    }
}
