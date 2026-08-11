package com.example.management.common.api;

import com.example.management.common.exception.AuthenticationRequiredException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 모든 API가 공유하는 인증·역직렬화 오류를 공통 오류 계약으로 변환한다. */
@RestControllerAdvice(basePackages = "com.example.management")
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationRequired(AuthenticationRequiredException e) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해주세요.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidArgument(MethodArgumentNotValidException e) {
        boolean expirationInvalid = e.getBindingResult().getFieldErrors().stream()
                .anyMatch(error -> error.getField().equals("expiresAt"));
        if (expirationInvalid) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_EXPIRATION",
                    "만료일은 현재보다 미래이며 허용 기간 이내여야 합니다.");
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해주세요.");
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidModelAttribute(BindException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해주세요.");
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidQueryParameter(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해주세요.");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String type, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), type, message));
    }
}
