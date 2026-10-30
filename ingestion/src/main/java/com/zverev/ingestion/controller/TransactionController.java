package com.zverev.ingestion.controller;

import com.zverev.ingestion.dto.TransactionCreatedResponse;
import com.zverev.ingestion.dto.TransactionRequest;
import com.zverev.ingestion.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("api/v1/transactions")
@Tag(
        name = "Ingestion — Write-Side",
        description = "Приём и первичная валидация платёжных транзакций."
)
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(
            summary = "Принять новую транзакцию",
            description = "Сохраняет транзакцию со статусом `NEW` и публикует событие в Kafka."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Транзакция принята в обработку.",
                    content = @Content(schema = @Schema(implementation = TransactionCreatedResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Ошибка валидации входных данных.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Транзакция с указанным `externalId` уже существует.",
                    content = @Content
            )
    })
    @PostMapping
    public ResponseEntity<TransactionCreatedResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionCreatedResponse response = transactionService.process(request);
        URI location = URI.create("/api/v1/transactions/" + response.transactionId());
        return ResponseEntity.accepted()
                .location(location)
                .body(response);
    }
}