package com.github.k3reviewer.api;

/** API 调用异常：认证、限流、模型不可用等。 */
public class ApiException extends Exception {

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
