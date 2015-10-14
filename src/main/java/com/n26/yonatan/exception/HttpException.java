package com.n26.yonatan.exception;

public abstract class HttpException extends RuntimeException {
    HttpException() {
        super();
    }

    HttpException(String message) {
        super(message);
    }
}
