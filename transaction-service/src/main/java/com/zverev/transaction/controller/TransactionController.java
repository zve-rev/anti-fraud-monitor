package com.zverev.transaction.controller;

import com.zverev.entity.enums.TransactionStatus;
import com.zverev.transaction.dto.*;
import com.zverev.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("api/v1/transactions")
@RequiredArgsConstructor
@Tag(
        name = "Transactions",
        description = "Transaction management endpoints for submission, retrieval, and fraud alert inspection."
)
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "Retrieve all transactions",
            description = """
                    Retrieves a paginated list of transactions.
                    Optionally filters transactions by their processing status.
                    Supports configurable pagination and sorting.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions retrieved successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid filter, pagination, or sorting parameters.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "An unexpected server-side error occurred.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @GetMapping
    public Page<TransactionResponse> listAllTransactions(
            @Parameter(
                    description = "Filter transactions by their current processing status."
            )
            @RequestParam(required = false) TransactionStatus status,

            @Parameter(
                    description = "Pagination and sorting configuration. Defaults to 20 records per page, sorted by creation date and transaction ID in descending order."
            )
            @PageableDefault(
                    size = 20,
                    sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return transactionService.findAllTransactions(status, pageable);
    }

    @Operation(
            summary = "Retrieve a transaction by ID",
            description = "Retrieves detailed information about a transaction using its unique internal identifier."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction retrieved successfully.",
                    content = @Content(
                            schema = @Schema(implementation = TransactionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid transaction identifier.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The requested transaction was not found.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "An unexpected server-side error occurred.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public TransactionResponse getTransactionById(
            @Parameter(
                    description = "Unique internal identifier of the transaction.",
                    example = "1",
                    required = true
            )
            @PathVariable Long id
    ) {
        return transactionService.findTransactionById(id);
    }

    @Operation(
            summary = "Retrieve transaction alerts",
            description = """
                    Retrieves fraud detection alerts associated with a specific transaction.
                    Results are returned as a paginated list and support configurable sorting.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction alerts retrieved successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid transaction identifier or pagination parameters.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The requested transaction was not found.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "An unexpected server-side error occurred.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{id}/alerts")
    public Page<AlertResponse> getTransactionAlerts(
            @Parameter(
                    description = "Unique internal identifier of the transaction.",
                    example = "1",
                    required = true
            )
            @PathVariable Long id,

            @Parameter(
                    description = "Pagination and sorting configuration. Defaults to 20 records per page, sorted by creation date and alert ID in descending order."
            )
            @PageableDefault(
                    size = 20,
                    sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return transactionService.findTransactionAlerts(id, pageable);
    }

    @Operation(
            summary = "Submit a transaction",
            description = """
                    Submits a new transaction for processing.
                    The transaction is accepted for asynchronous processing.
                    Returns the assigned transaction identifier and the resource location.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Transaction accepted for processing.",
                    content = @Content(
                            schema = @Schema(implementation = TransactionCreatedResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The request body is malformed or one or more fields failed validation.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A transaction with the specified external identifier already exists.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "An unexpected server-side error occurred.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<TransactionCreatedResponse> createTransaction(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Transaction details required for submission and processing.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = TransactionRequest.class)
                    )
            )
            @Valid @RequestBody TransactionRequest request
    ) {
        TransactionCreatedResponse response = transactionService.process(request);
        URI location = URI.create("/api/v1/transactions/" + response.transactionId());

        return ResponseEntity.accepted()
                .location(location)
                .body(response);
    }
}
