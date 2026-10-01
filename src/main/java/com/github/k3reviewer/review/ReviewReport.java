package com.github.k3reviewer.review;

/**
 * 一次代码审查的结构化结果。
 */
public record ReviewReport(
        String model,
        String scope,
        String verdict,
        String reviewText,
        int inputChars,
        long elapsedMillis) {

    /** 空结果（API 未返回可用内容时）。 */
    public static ReviewReport empty(String model, String scope, String reason) {
        return new ReviewReport(model, scope, "审查未完成", reason, 0, 0);
    }
}
