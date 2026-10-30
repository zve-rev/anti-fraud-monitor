package com.zverev.alerting.controller;

import com.zverev.alerting.dto.TransactionResponse;
import com.zverev.alerting.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/transactions")
@Tag(
        name = "Alerting — Read-Side (Transactions)",
        description = "Чтение транзакций и результатов скоринга."
)
public class TransactionQueryController {

    private final TransactionQueryService transactionQueryService;

    public TransactionQueryController(TransactionQueryService transactionQueryService) {
        this.transactionQueryService = transactionQueryService;
    }

    @Operation(
            summary = "Получить список транзакций",
            description = "Возвращает все транзакции со статусами и алертами."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Список транзакций.",
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = TransactionResponse.class))
                    )
            )
    })
    @GetMapping
    public List<TransactionResponse> listAllTransactions() {
        return transactionQueryService.findAllTransactions();
    }
}