package com.zverev.kafka;

import com.zverev.dto.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TransactionEventConsumer {

    @KafkaListener(topics = "transactions.raw", groupId = "antifraud-group")
    public void consume(TransactionEvent event) {
        log.info("Received transaction event from Kafka: externalId={}, cardToken={}, amount={} {}",
                event.externalId(), event.cardToken(), event.amount(), event.currency());
    }
}
