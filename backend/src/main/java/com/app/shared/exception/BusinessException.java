package com.app.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public BusinessException(String code, HttpStatus httpStatus) {
        super(code);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String code, HttpStatus httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BusinessException notFound(String code) {
        return new BusinessException(code, HttpStatus.NOT_FOUND);
    }

    public static BusinessException badRequest(String code) {
        return new BusinessException(code, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException forbidden(String code) {
        return new BusinessException(code, HttpStatus.FORBIDDEN);
    }

    public static BusinessException unauthorized(String code) {
        return new BusinessException(code, HttpStatus.UNAUTHORIZED);
    }
}
