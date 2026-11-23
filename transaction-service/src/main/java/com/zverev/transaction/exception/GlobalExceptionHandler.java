package com.zverev.transaction.exception;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@Tag(
        name = "Error Responses",
        description = "Global exception handling and standardized API error responses."
)
public class GlobalExceptionHandler {

    @Operation(
            summary = "Handle duplicate transactions",
            description = "Returns a conflict response when a transaction with the same external identifier already exists."
    )
    @ApiResponse(
            responseCode = "409",
            description = "A transaction with the specified external identifier already exists.",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @ExceptionHandler(TransactionAlreadyExistsException.class)
    public ProblemDetail handleDuplicate(TransactionAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Duplicate transaction");
        return problem;
    }

    @Operation(
            summary = "Handle missing transactions",
            description = "Returns a not found response when the requested transaction does not exist."
    )
    @ApiResponse(
            responseCode = "404",
            description = "The requested transaction was not found.",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleNotFound(TransactionNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setTitle("Transaction not found");
        return problem;
    }

    @Operation(
            summary = "Handle malformed request bodies",
            description = "Returns a bad request response when the request body cannot be deserialized."
    )
    @ApiResponse(
            responseCode = "400",
            description = "The request body is malformed or contains invalid data types.",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed request body"
        );
        problem.setTitle("Invalid request");
        return problem;
    }

    @Operation(
            summary = "Handle request validation failures",
            description = "Returns a bad request response containing validation errors for individual request fields."
    )
    @ApiResponse(
            responseCode = "400",
            description = "One or more request fields failed validation.",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            description = "Problem details with an additional errors property mapping field names to validation messages.",
                            implementation = ProblemDetail.class
                    )
            )
    )
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed"
        );
        problem.setTitle("Invalid request");

        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(
                        error.getField(),
                        error.getDefaultMessage()
                ));

        problem.setProperty("errors", errors);

        return problem;
    }

    @Operation(
            summary = "Handle unexpected exceptions",
            description = "Returns a generic internal server error response without exposing internal exception details."
    )
    @ApiResponse(
            responseCode = "500",
            description = "An unexpected server-side error occurred.",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error occurred while processing request", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
        problem.setTitle("Internal server error");

        return problem;
    }
}
