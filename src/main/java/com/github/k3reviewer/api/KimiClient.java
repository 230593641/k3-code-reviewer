package com.github.k3reviewer.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.k3reviewer.config.Config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Kimi Anthropic 兼容端点客户端。
 * 端点：POST {base}/v1/messages，模型 kimi-k3（强制开启思考）。
 */
public final class KimiClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Config config;
    private final HttpClient http;

    public KimiClient(Config config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    /** 测试用：注入自定义 HttpClient。 */
    public KimiClient(Config config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    /**
     * 发送一条用户消息，返回模型回复的文本（自动拼接 thinking 之后的正文）。
     *
     * @throws ApiException API 错误（认证、限流、模型不可用等）
     */
    public String chat(String system, String userContent) throws ApiException {
        String body = buildBody(system, userContent);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/v1/messages"))
                .timeout(Duration.ofSeconds(600))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException("网络请求失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("请求被中断", e);
        }

        int status = response.statusCode();
        if (status == 401) {
            throw new ApiException("认证失败（401）：MOONSHOT_API_KEY 无效或已过期。");
        }
        if (status == 429) {
            throw new ApiException("请求过频（429）：账号限流，请稍后重试。");
        }
        if (status >= 400) {
            throw new ApiException("API 错误（" + status + "）：" + brief(response.body()));
        }

        return parseText(response.body());
    }

    /** 校验 Key 是否有效、模型是否可用。返回模型列表摘要。 */
    public String listModels() throws ApiException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.modelsEndpoint()))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + config.apiKey())
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException("网络请求失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("请求被中断", e);
        }
        if (response.statusCode() == 401) {
            throw new ApiException("认证失败（401）：MOONSHOT_API_KEY 无效。");
        }
        if (response.statusCode() >= 400) {
            throw new ApiException("API 错误（" + response.statusCode() + "）：" + brief(response.body()));
        }
        try {
            JsonNode root = JSON.readTree(response.body());
            List<String> ids = new ArrayList<>();
            for (JsonNode m : root.path("data")) {
                ids.add(m.path("id").asText());
            }
            return String.join(", ", ids);
        } catch (IOException e) {
            throw new ApiException("模型列表解析失败", e);
        }
    }

    private String buildBody(String system, String userContent) {
        try {
            var root = JSON.createObjectNode();
            root.put("model", config.model());
            root.put("max_tokens", config.maxTokens());
            if (system != null && !system.isBlank()) {
                root.put("system", system);
            }
            var messages = root.putArray("messages");
            var msg = messages.addObject();
            msg.put("role", "user");
            msg.put("content", userContent);
            return JSON.writeValueAsString(root);
        } catch (IOException e) {
            throw new IllegalStateException("请求体构造失败", e);
        }
    }

    /** 解析 Anthropic 格式响应：content[].text 中第一个非 thinking 的文本块。 */
    private String parseText(String responseBody) throws ApiException {
        try {
            JsonNode root = JSON.readTree(responseBody);
            if (root.has("error")) {
                throw new ApiException("API 返回错误：" + brief(responseBody));
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    text.append(block.path("text").asText());
                }
            }
            if (text.isEmpty()) {
                throw new ApiException("模型未返回正文内容（可能 max_tokens 过小）。");
            }
            return text.toString();
        } catch (IOException e) {
            throw new ApiException("响应解析失败", e);
        }
    }

    private static String brief(String body) {
        String t = body == null ? "" : body.trim();
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }
}
