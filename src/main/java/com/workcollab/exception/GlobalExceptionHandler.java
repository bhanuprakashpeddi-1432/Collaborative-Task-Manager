package com.workcollab.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Validation failed", request, Map.of("errors", errors));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to access this resource.", request, null);
    }

    @ExceptionHandler(TaskOptimisticLockingException.class)
    public ResponseEntity<Map<String, Object>> handleTaskOptimisticLockingException(TaskOptimisticLockingException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request, Map.of(
                "staleVersion", ex.getStaleVersion(),
                "currentVersion", ex.getCurrentVersion()
        ));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, "CONFLICT", "The resource was updated by another user. Please reload and try again.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        ex.printStackTrace();
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred", request, Map.of("stackTrace", java.util.Arrays.toString(ex.getStackTrace())));
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String error, String message, HttpServletRequest request, Map<String, Object> additionalProps) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        
        // Ensure requestId is attached (if using a request filter to generate it)
        String requestId = (String) request.getAttribute("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        body.put("requestId", requestId);

        if (additionalProps != null) {
            body.putAll(additionalProps);
        }

        return ResponseEntity.status(status).body(body);
    }
}
