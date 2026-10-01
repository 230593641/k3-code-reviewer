package com.github.k3reviewer.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Config 构造不变量与端点推导测试（与 Config 同包，可访问包级构造器）。 */
class ConfigTest {

    @Test
    void packageConstructor_rejectsNonPositiveMaxTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> new Config("k", "https://api.moonshot.cn/anthropic", "kimi-k3", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Config("k", "https://api.moonshot.cn/anthropic", "kimi-k3", -1));
    }

    @Test
    void packageConstructor_rejectsBlankStrings() {
        assertThrows(IllegalArgumentException.class,
                () -> new Config("", "https://api.moonshot.cn/anthropic", "kimi-k3", 100));
        assertThrows(IllegalArgumentException.class,
                () -> new Config("   ", "https://api.moonshot.cn/anthropic", "kimi-k3", 100));
        assertThrows(IllegalArgumentException.class,
                () -> new Config("k", " ", "kimi-k3", 100));
        assertThrows(IllegalArgumentException.class,
                () -> new Config("k", "https://api.moonshot.cn/anthropic", "", 100));
    }

    @Test
    void packageConstructor_acceptsValidValues() {
        Config c = new Config("key", "https://api.moonshot.cn/anthropic", "kimi-k3", 2048);
        assertEquals("key", c.apiKey());
        assertEquals(2048, c.maxTokens());
    }

    @Test
    void forTest_rejectsBlankApiKey() {
        assertThrows(IllegalArgumentException.class, () -> Config.forTest("", "https://x/v1"));
    }
}
