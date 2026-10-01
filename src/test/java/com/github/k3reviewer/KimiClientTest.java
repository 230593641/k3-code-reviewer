package com.github.k3reviewer;

import com.github.k3reviewer.api.ApiException;
import com.github.k3reviewer.api.KimiClient;
import com.github.k3reviewer.config.Config;
import com.github.k3reviewer.review.ReviewReport;
import com.github.k3reviewer.review.Reviewer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** KimiClient 与 Reviewer 的集成测试：使用本地 mock HTTP 服务模拟 Kimi 端点。 */
class KimiClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/v1/messages", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = """
                    {"content":[{"type":"text","text":"审查完毕。结论：PASS"}],"model":"kimi-k3"}"""
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.createContext("/v1/models", exchange -> {
            byte[] resp = """
                    {"data":[{"id":"kimi-k3"},{"id":"kimi-k2.7-code"}]}"""
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private KimiClient newClient() {
        return new KimiClient(Config.forTest("test-key", baseUrl), HttpClient.newHttpClient());
    }

    @Test
    void chat_parsesTextBlock() throws Exception {
        String text = newClient().chat("system", "请审查这段代码");
        assertEquals("审查完毕。结论：PASS", text);
    }

    @Test
    void chat_sendsModelAndMessage() throws Exception {
        newClient().chat("sys", "hello");
        assertTrue(lastBody.get().contains("\"model\":\"kimi-k3\""));
        assertTrue(lastBody.get().contains("\"role\":\"user\""));
        assertTrue(lastBody.get().contains("hello"));
    }

    @Test
    void listModels_returnsIds() throws Exception {
        String models = newClient().listModels();
        assertEquals("kimi-k3, kimi-k2.7-code", models);
    }

    @Test
    void chat_401_throwsAuthError() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/v1/messages", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        s.start();
        try {
            KimiClient client = new KimiClient(
                    Config.forTest("bad", "http://127.0.0.1:" + s.getAddress().getPort()),
                    HttpClient.newHttpClient());
            ApiException e = assertThrows(ApiException.class, () -> client.chat("sys", "x"));
            assertTrue(e.getMessage().contains("401") || e.getMessage().contains("认证"));
        } finally {
            s.stop(0);
        }
    }

    @Test
    void reviewer_buildsReviewReport() throws Exception {
        KimiClient client = newClient();
        Reviewer reviewer = new Reviewer(Config.forTest("test-key", baseUrl), client);
        ReviewReport report = reviewer.review("git diff vs HEAD", "public int add(int a, int b) { return a + b; }");
        assertNotNull(report);
        assertEquals("PASS", report.verdict());
        assertTrue(report.reviewText().contains("审查完毕"));
        assertEquals("kimi-k3", report.model());
        assertTrue(report.inputChars() > 0);
    }

    @Test
    void reviewer_blankContent_returnsEmpty() throws Exception {
        KimiClient client = newClient();
        Reviewer reviewer = new Reviewer(Config.forTest("test-key", baseUrl), client);
        ReviewReport report = reviewer.review("file x", "   ");
        assertEquals("审查未完成", report.verdict());
        assertTrue(report.reviewText().contains("没有可审查的内容"));
    }

    @Test
    void reviewer_needsFixVerdict() throws Exception {
        // 覆盖 messages 处理器返回 NEEDS_FIX 文本
        server.removeContext("/v1/messages");
        server.createContext("/v1/messages", exchange -> {
            byte[] resp = """
                    {"content":[{"type":"text","text":"发现严重问题。结论：NEEDS_FIX"}]}"""
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        Reviewer reviewer = new Reviewer(Config.forTest("test-key", baseUrl), newClient());
        ReviewReport report = reviewer.review("file x", "public int add(int a, int b) { return a - b; }");
        assertEquals("NEEDS_FIX", report.verdict());
    }

    @Test
    void config_modelsEndpointMapsBaseUrl() {
        Config c = Config.forTest("k", "https://api.moonshot.cn/anthropic");
        assertEquals("https://api.moonshot.cn/v1/models", c.modelsEndpoint());
    }

    @Test
    void config_modelsEndpointHandlesTrailingSlash() {
        Config c = Config.forTest("k", "https://api.moonshot.cn/anthropic/");
        assertEquals("https://api.moonshot.cn/v1/models", c.modelsEndpoint());
        Config c2 = Config.forTest("k", "https://api.moonshot.cn/");
        assertEquals("https://api.moonshot.cn/v1/models", c2.modelsEndpoint());
    }

    @Test
    void config_modelsEndpointHandlesCustomV1Base() {
        // K3 审查意见 1：自定义 baseUrl 已含 /v1 后缀时不得生成 /v1/v1/models
        Config c = Config.forTest("k", "https://api.moonshot.cn/v1");
        assertEquals("https://api.moonshot.cn/v1/models", c.modelsEndpoint());
        Config c2 = Config.forTest("k", "https://api.moonshot.cn/anthropic/v1");
        assertEquals("https://api.moonshot.cn/anthropic/v1/models", c2.modelsEndpoint());
    }

    @Test
    void main_helpDoesNotRequireApiKey() throws Exception {
        // K3 审查意见 S1：help 不能因缺少 MOONSHOT_API_KEY 而失败
        int code = Main.run(new String[]{"help"});
        assertEquals(0, code);
        int code2 = Main.run(new String[]{"--help"});
        assertEquals(0, code2);
    }
}
