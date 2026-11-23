package com.zverev.riskassessment.util;

import com.zverev.shared.entity.Alert;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.TransactionStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionStatusResolver {

    public TransactionStatus resolve(List<Alert> alerts) {
        if (alerts.isEmpty()) {
            return TransactionStatus.APPROVED;
        }
        boolean hasCritical = alerts.stream().anyMatch(a -> a.getSeverity() == AlertSeverity.CRITICAL);
        return hasCritical ? TransactionStatus.DECLINED : TransactionStatus.REVIEW;
    }
}
