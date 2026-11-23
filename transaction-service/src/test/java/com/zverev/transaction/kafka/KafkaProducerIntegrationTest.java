package com.zverev.transaction.kafka;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.repository.OutboxEventRepository;
import com.zverev.transaction.TransactionServiceApplication;
import com.zverev.transaction.outbox.OutboxEventPoller;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@Testcontainers
@SpringBootTest(classes = TransactionServiceApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class KafkaProducerIntegrationTest {

    private static final String TOPIC = "transactions.raw";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("antifraud_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0")
    );

    @Autowired
    private KafkaProducer kafkaProducer;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoSpyBean(name = "kafkaTemplate")
    private KafkaTemplate kafkaTemplate;

    @MockitoBean
    private OutboxEventPoller outboxEventPoller;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void shouldPublishEventAndMarkOutboxEventAsSent() {
        String aggregateId = UUID.randomUUID().toString();
        String messageKey = aggregateId;
        String payload = """
                {"transactionId":"%s","amount":125.50,"currency":"EUR"}
                """.formatted(aggregateId);

        OutboxEvent savedEvent = createOutboxEvent(aggregateId, payload);

        kafkaProducer.send(savedEvent);

        ConsumerRecord<String, String> receivedRecord = consumeMessage(messageKey);

        assertEquals(TOPIC, receivedRecord.topic());
        assertEquals(messageKey, receivedRecord.key());
        assertEquals(payload, receivedRecord.value());

        OutboxEvent updatedEvent = findEvent(savedEvent.getId());

        assertEquals(OutboxEventStatus.SENT, updatedEvent.getStatus());
        assertEquals(1, updatedEvent.getAttempts());
        assertNotNull(updatedEvent.getProcessedAt());
    }

    @Test
    void shouldKeepEventPendingAfterPublishingFailure() {
        simulateKafkaFailure();

        String aggregateId = UUID.randomUUID().toString();
        OutboxEvent savedEvent = createOutboxEvent(aggregateId, "{}");

        kafkaProducer.send(savedEvent);

        OutboxEvent updatedEvent = findEvent(savedEvent.getId());

        assertEquals(OutboxEventStatus.PENDING, updatedEvent.getStatus());
        assertEquals(1, updatedEvent.getAttempts());
    }

    @Test
    void shouldMarkEventAsFailedAfterMaxAttempts() {
        simulateKafkaFailure();

        String aggregateId = UUID.randomUUID().toString();
        OutboxEvent savedEvent = createOutboxEvent(aggregateId, "{}");

        for (int i = 0; i < 5; i++) {
            OutboxEvent event = findEvent(savedEvent.getId());
            kafkaProducer.send(event);
        }

        OutboxEvent updatedEvent = findEvent(savedEvent.getId());

        assertEquals(OutboxEventStatus.FAILED, updatedEvent.getStatus());
        assertEquals(5, updatedEvent.getAttempts());
    }

    @Test
    void shouldRestoreInterruptFlagAndPropagateExceptionWhenPublishingIsInterrupted() {
        String aggregateId = UUID.randomUUID().toString();
        OutboxEvent savedEvent = createOutboxEvent(aggregateId, "{}");

        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return new CompletableFuture<>();
        })
                .when(kafkaTemplate)
                .send(anyString(), anyString(), anyString());

        try {
            assertThatThrownBy(() -> kafkaProducer.send(savedEvent))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Interrupted while publishing outbox event");

            assertThat(Thread.currentThread().isInterrupted()).isTrue();

            OutboxEvent updatedEvent = findEvent(savedEvent.getId());

            assertThat(updatedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(updatedEvent.getAttempts()).isZero();

        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldRecoverAndPublishPendingEventAfterKafkaRestart() {
        String aggregateId = UUID.randomUUID().toString();
        String payload = """
                {"transactionId":"%s","amount":125.50,"currency":"EUR"}
                """.formatted(aggregateId);

        OutboxEvent savedEvent = createOutboxEvent(aggregateId, payload);

        kafka.stop();

        kafkaProducer.send(savedEvent);

        OutboxEvent eventAfterFailure = findEvent(savedEvent.getId());

        assertThat(eventAfterFailure.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);

        assertThat(eventAfterFailure.getAttempts())
                .isEqualTo(1);

        kafka.start();

        DefaultKafkaProducerFactory<String, String> producerFactory =
                (DefaultKafkaProducerFactory<String, String>) kafkaTemplate.getProducerFactory();

        producerFactory.updateConfigs(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers()
        ));

        producerFactory.reset();

        OutboxEvent eventForRetry = findEvent(savedEvent.getId());

        kafkaProducer.send(eventForRetry);

        ConsumerRecord<String, String> receivedRecord = consumeMessage(aggregateId);

        assertThat(receivedRecord.topic()).isEqualTo(TOPIC);
        assertThat(receivedRecord.key()).isEqualTo(aggregateId);
        assertThat(receivedRecord.value()).isEqualTo(payload);

        OutboxEvent recoveredEvent = findEvent(savedEvent.getId());

        assertThat(recoveredEvent.getStatus())
                .isEqualTo(OutboxEventStatus.SENT);

        assertThat(recoveredEvent.getAttempts())
                .isEqualTo(2);

        assertThat(recoveredEvent.getProcessedAt()).isNotNull();
    }

    private void simulateKafkaFailure() {
        doReturn(CompletableFuture.failedFuture(
                new RuntimeException("Simulated Kafka publishing failure")
        ))
                .when(kafkaTemplate)
                .send(anyString(), anyString(), anyString());
    }

    private OutboxEvent createOutboxEvent(String aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent(
                aggregateId,
                payload,
                TOPIC,
                aggregateId
        );

        return outboxEventRepository.saveAndFlush(event);
    }

    private OutboxEvent findEvent(Long id) {
        return outboxEventRepository.findById(id).orElseThrow();
    }

    private ConsumerRecord<String, String> consumeMessage(String expectedKey) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));

            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();

            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> record : records) {
                    if (expectedKey.equals(record.key())) {
                        return record;
                    }
                }
            }
        }

        throw new AssertionError(
                "Kafka message with key " + expectedKey + " was not received within 15 seconds"
        );
    }
}
