package com.smart.restaurant_saas.common;

public class ApiException extends RuntimeException {

    public ApiException(String message) {
        super(message);
    }
}
