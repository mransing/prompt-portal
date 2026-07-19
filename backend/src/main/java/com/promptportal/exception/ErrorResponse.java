package com.promptportal.exception;

public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        Object details
) {
}
