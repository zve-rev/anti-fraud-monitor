package com.zverev.alerting.controller;

import com.zverev.alerting.dto.AlertResponse;
import com.zverev.alerting.service.AlertQueryService;
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
@RequestMapping("api/v1/alerts")
@Tag(
        name = "Alerting — Read-Side (Alerts)",
        description = "Сработавшие антифрод-правила."
)
public class AlertQueryController {

    private final AlertQueryService alertQueryService;

    public AlertQueryController(AlertQueryService alertQueryService) {
        this.alertQueryService = alertQueryService;
    }

    @Operation(
            summary = "Получить список алертов",
            description = "Возвращает все алерты по всем транзакциям."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Список алертов.",
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = AlertResponse.class))
                    )
            )
    })
    @GetMapping
    public List<AlertResponse> listAllAlerts() {
        return alertQueryService.findAllAlerts();
    }
}