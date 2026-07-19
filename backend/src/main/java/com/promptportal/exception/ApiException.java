package com.promptportal.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Object details;

    public ApiException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public ApiException(String code, String message, HttpStatus status, Object details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object getDetails() {
        return details;
    }

    public static ApiException notFound(String message) {
        return new ApiException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static ApiException validation(String message) {
        return new ApiException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public static ApiException validation(String message, Object details) {
        return new ApiException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST, details);
    }

    public static ApiException forbidden(String message) {
        return new ApiException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    public static ApiException unsupportedMedia(String message) {
        return new ApiException("UNSUPPORTED_MEDIA_TYPE", message, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    public static ApiException fileTooLarge(String message) {
        return new ApiException("FILE_TOO_LARGE", message, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    public static ApiException quotaExceeded(String message) {
        return new ApiException("QUOTA_EXCEEDED", message, HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
