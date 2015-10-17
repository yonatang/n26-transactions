package com.n26.yonatan.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Created by yonatan on 17/10/2015.
 */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class ConflictException extends HttpException {
    public ConflictException(String message) {
        super(message);
    }

}
