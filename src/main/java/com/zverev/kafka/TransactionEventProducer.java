package com.zverev.kafka;

import com.zverev.dto.TransactionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionEventProducer {

    private static final String TOPIC = "transactions.raw";

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public void send(TransactionEvent event) {
        kafkaTemplate.send(TOPIC, event.cardToken(), event);
    }
}
