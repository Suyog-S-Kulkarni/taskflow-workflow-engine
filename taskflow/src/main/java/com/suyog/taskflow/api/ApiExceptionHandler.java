package com.suyog.taskflow.api;

import com.suyog.taskflow.execution.application.WorkflowBusyException;
import com.suyog.taskflow.workflow.domain.WorkflowValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(WorkflowValidationException.class)
    public ResponseEntity<ApiError> handleInvalidWorkflow(
            WorkflowValidationException exception,
            HttpServletRequest request
    ) {
        return badRequest(
                "INVALID_WORKFLOW",
                exception.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldViolation(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "Invalid value"
                                : error.getDefaultMessage()
                ))
                .sorted(
                        Comparator.comparing(FieldViolation::field)
                                .thenComparing(FieldViolation::message)
                )
                .toList();

        return badRequest(
                "INVALID_REQUEST",
                "Request validation failed",
                request.getRequestURI(),
                violations
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return badRequest(
                "MALFORMED_REQUEST",
                "Request body is missing or cannot be read as the expected JSON",
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(WorkflowBusyException.class)
    public ResponseEntity<ApiError> handleBusy(
            WorkflowBusyException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "WORKFLOW_BUSY",
                exception.getMessage(),
                request.getRequestURI(),
                UUID.randomUUID().toString(),
                List.of()
        );

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(error);
    }

    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity<ApiError> handleInterrupted(
            InterruptedException exception,
            HttpServletRequest request
    ) {
        Thread.currentThread().interrupt();

        ApiError error = new ApiError(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "EXECUTION_INTERRUPTED",
                "Workflow preview was interrupted; no final report is available",
                request.getRequestURI(),
                UUID.randomUUID().toString(),
                List.of()
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error);
    }

    private ResponseEntity<ApiError> badRequest(
            String code,
            String message,
            String path,
            List<FieldViolation> violations
    ) {
        ApiError error = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                code,
                message,
                path,
                UUID.randomUUID().toString(),
                violations
        );

        return ResponseEntity.badRequest().body(error);
    }

    public record FieldViolation(
            String field,
            String message
    ) {
    }

    public record ApiError(
            int status,
            String code,
            String message,
            String path,
            String correlationId,
            List<FieldViolation> violations
    ) {

        public ApiError {
            violations = List.copyOf(violations);
        }
    }
}