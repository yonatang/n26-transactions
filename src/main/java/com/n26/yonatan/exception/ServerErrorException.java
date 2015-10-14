package com.n26.yonatan.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
public class ServerErrorException extends HttpException {
    public ServerErrorException(String message) {
        super(message);
    }
}
