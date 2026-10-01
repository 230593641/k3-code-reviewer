package com.github.k3reviewer.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件读取工具：统一处理 UTF-8 读取、大小上限与二进制探测，
 * 避免把超大或二进制文件误送大模型审查。
 */
public final class FileIO {

    /** 单文件审查大小上限。 */
    public static final long MAX_FILE_BYTES = 256 * 1024;

    private FileIO() {
    }

    /** 读取文本文件（UTF-8）。 */
    public static String readText(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** 超过大小上限的文件不送审。 */
    public static boolean tooLarge(Path path) throws IOException {
        return Files.size(path) > MAX_FILE_BYTES;
    }

    /** 简单二进制探测：前 8 KB 出现 NUL 字节即视为二进制。 */
    public static boolean looksBinary(Path path) throws IOException {
        byte[] buf = new byte[8192];
        try (InputStream in = Files.newInputStream(path)) {
            int n = in.read(buf);
            for (int i = 0; i < n; i++) {
                if (buf[i] == 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
