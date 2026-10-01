package com.github.k3reviewer.config;

/**
 * 运行配置：全部从环境变量读取，密钥绝不硬编码进代码或仓库。
 *
 * <ul>
 *   <li>MOONSHOT_API_KEY —— Kimi 开放平台 API Key（必填）</li>
 *   <li>K3_BASE_URL —— Anthropic 兼容端点（默认 https://api.moonshot.cn/anthropic）</li>
 *   <li>K3_MODEL —— 模型名（默认 kimi-k3）</li>
 *   <li>K3_MAX_TOKENS —— 单次回复最大 token（默认 8192）</li>
 * </ul>
 */
public final class Config {

    public static final String DEFAULT_BASE_URL = "https://api.moonshot.cn/anthropic";
    public static final String DEFAULT_MODEL = "kimi-k3";
    public static final int DEFAULT_MAX_TOKENS = 8192;
    private static final String ANTHROPIC_SUFFIX = "/anthropic";

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;

    public Config() {
        this.apiKey = requireEnv("MOONSHOT_API_KEY");
        this.baseUrl = envOr("K3_BASE_URL", DEFAULT_BASE_URL);
        this.model = envOr("K3_MODEL", DEFAULT_MODEL);
        this.maxTokens = maxTokensFromEnv();
    }

    /** 包级构造器（供 forTest 与同包使用）：与无参构造器保持同一组不变量。 */
    Config(String apiKey, String baseUrl, String model, int maxTokens) {
        this.apiKey = requireNonBlank(apiKey, "apiKey");
        this.baseUrl = requireNonBlank(baseUrl, "baseUrl");
        this.model = requireNonBlank(model, "model");
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens 必须为正数：" + maxTokens);
        }
        this.maxTokens = maxTokens;
    }

    /** 仅用于单元测试：构造一个不依赖真实环境的配置。 */
    public static Config forTest(String apiKey, String baseUrl) {
        return new Config(apiKey, baseUrl, DEFAULT_MODEL, 1024);
    }

    /** 读取环境变量：null 或空白值一律回退默认，并 strip 首尾空白。 */
    private static String envOr(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v.strip();
    }

    private static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                    "缺少环境变量 " + name + "。请先设置：MOONSHOT_API_KEY=\"你的 Key\""
                            + "（Windows PowerShell 用 $env:MOONSHOT_API_KEY=\"...\"）");
        }
        return v.strip();
    }

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return v.strip();
    }

    /** 解析并校验 K3_MAX_TOKENS：非法或非正数时回退默认值并告警，避免静默吞掉用户配置。 */
    private static int maxTokensFromEnv() {
        String v = System.getenv("K3_MAX_TOKENS");
        if (v == null || v.isBlank()) {
            return DEFAULT_MAX_TOKENS;
        }
        try {
            int n = Integer.parseInt(v.strip());
            if (n <= 0) {
                warn("K3_MAX_TOKENS 必须为正数（当前 \"" + v.strip() + "\"），已回退默认 " + DEFAULT_MAX_TOKENS);
                return DEFAULT_MAX_TOKENS;
            }
            return n;
        } catch (NumberFormatException e) {
            warn("K3_MAX_TOKENS 不是有效数字（当前 \"" + v.strip() + "\"），已回退默认 " + DEFAULT_MAX_TOKENS);
            return DEFAULT_MAX_TOKENS;
        }
    }

    private static void warn(String msg) {
        System.err.println("警告：" + msg);
    }

    public String apiKey() {
        return apiKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public int maxTokens() {
        return maxTokens;
    }

    /**
     * 由 Anthropic 兼容端点推导同主机的 OpenAI 兼容模型列表端点（/v1/models），
     * 供 check 子命令使用。处理尾部斜杠、/anthropic 后缀、以及自定义 baseUrl
     * 已含 /v1 后缀三种情况。
     */
    public String modelsEndpoint() {
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(ANTHROPIC_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - ANTHROPIC_SUFFIX.length());
        }
        if (!normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        return normalized + "/models";
    }
}
