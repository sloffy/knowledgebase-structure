package com.polytech.knowledgebase.structure.web;

import com.polytech.knowledgebase.structure.admin.InvalidInputException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String message, Map<String, String> fieldErrors) {}

    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<ApiError> invalidField(InvalidInputException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage(),
                Map.of(exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalidArgument(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> unreadable(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause instanceof IllegalArgumentException
                ? cause.getMessage() : "Проверьте формат полей, даты и выбранные значения.";
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "Запись конфликтует с данными базы. Проверьте уникальность названия, адреса или связи.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentChange(OptimisticLockingFailureException exception) {
        return error(HttpStatus.CONFLICT, "Запись уже изменена. Обновите её перед сохранением.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(
                exception.getReason() == null ? "Не удалось выполнить запрос." : exception.getReason(), Map.of()));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message, Map.of()));
    }
}
