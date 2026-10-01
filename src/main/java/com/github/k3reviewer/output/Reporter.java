package com.github.k3reviewer.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.k3reviewer.review.ReviewReport;

import java.util.List;

/**
 * 审查结果输出：终端可读文本（默认）或结构化 JSON（--json）。
 */
public final class Reporter {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 输出到标准输出。isJson 为 true 时输出 JSON，否则输出分节文本。 */
    public void print(List<ReviewReport> reports, boolean isJson) {
        if (isJson) {
            printJson(reports);
        } else {
            for (ReviewReport r : reports) {
                printText(r);
            }
        }
    }

    private void printJson(List<ReviewReport> reports) {
        try {
            var arr = JSON.createArrayNode();
            for (ReviewReport r : reports) {
                var o = arr.addObject();
                o.put("model", r.model());
                o.put("scope", r.scope());
                o.put("verdict", r.verdict());
                o.put("elapsed_ms", r.elapsedMillis());
                o.put("input_chars", r.inputChars());
                o.put("review", r.reviewText());
            }
            System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
        } catch (Exception e) {
            throw new IllegalStateException("JSON 输出失败", e);
        }
    }

    private void printText(ReviewReport r) {
        System.out.println("=".repeat(64));
        System.out.println("审查范围：" + r.scope());
        System.out.println("模型：" + r.model()
                + "　　耗时：" + r.elapsedMillis() + " ms"
                + "　　输入：" + r.inputChars() + " 字符");
        System.out.println("结论：" + r.verdict());
        System.out.println("-".repeat(64));
        System.out.println(r.reviewText());
        System.out.println("=".repeat(64));
    }
}
