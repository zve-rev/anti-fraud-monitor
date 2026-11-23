package com.zverev.transaction.integration;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.repository.OutboxEventRepository;
import com.zverev.transaction.TransactionServiceApplication;
import com.zverev.transaction.outbox.OutboxEventPoller;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OutboxApplicationRestartIntegrationTest {

    private static final String TOPIC = "transactions.raw";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("antifraud_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @Test
    void shouldRecoverPendingEventAfterApplicationRestart() throws Exception {
        createTopic();

        String aggregateId = UUID.randomUUID().toString();

        String payload = """
                {"transactionId":"%s","amount":125.50,"currency":"EUR"}
                """.formatted(aggregateId);

        Long eventId;

        try (ConfigurableApplicationContext firstContext = startApplication()) {
            OutboxEventRepository repository =
                    firstContext.getBean(OutboxEventRepository.class);

            OutboxEvent event = repository.saveAndFlush(
                    new OutboxEvent(
                            aggregateId,
                            payload,
                            TOPIC,
                            aggregateId
                    )
            );

            eventId = event.getId();

            assertThat(repository.findById(eventId))
                    .isPresent()
                    .get()
                    .extracting(OutboxEvent::getStatus)
                    .isEqualTo(OutboxEventStatus.PENDING);
        }

        try (ConfigurableApplicationContext secondContext = startApplication()) {
            OutboxEventPoller poller =
                    secondContext.getBean(OutboxEventPoller.class);

            OutboxEventRepository repository =
                    secondContext.getBean(OutboxEventRepository.class);

            poller.publishPendingEvents();

            OutboxEvent recoveredEvent = repository.findById(eventId)
                    .orElseThrow();

            assertThat(recoveredEvent.getStatus())
                    .isEqualTo(OutboxEventStatus.SENT);

            assertThat(recoveredEvent.getAttempts())
                    .isEqualTo(1);

            assertThat(recoveredEvent.getProcessedAt())
                    .isNotNull();

            ConsumerRecord<String, String> receivedRecord =
                    consumeMessage(aggregateId);

            assertThat(receivedRecord.topic()).isEqualTo(TOPIC);
            assertThat(receivedRecord.key()).isEqualTo(aggregateId);
            assertThat(receivedRecord.value()).isEqualTo(payload);
        }
    }

    private void createTopic() throws Exception {
        try (AdminClient adminClient = AdminClient.create(
                Map.of(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                        kafka.getBootstrapServers()
                )
        )) {
            adminClient.createTopics(
                    List.of(new NewTopic(TOPIC, 1, (short) 1))
            ).all().get(10, TimeUnit.SECONDS);
        }
    }

    private ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(TransactionServiceApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                        "--outbox.poller.initial-delay=3600000",
                        "--outbox.poller.fixed-delay=3600000"
                );
    }

    private ConsumerRecord<String, String> consumeMessage(String expectedKey) {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers()
        );

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                UUID.randomUUID().toString()
        );

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(properties)) {

            consumer.subscribe(List.of(TOPIC));

            long deadline = System.nanoTime()
                    + Duration.ofSeconds(15).toNanos();

            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> record : records) {
                    if (expectedKey.equals(record.key())) {
                        return record;
                    }
                }
            }
        }

        throw new AssertionError(
                "Kafka message with key " + expectedKey
                        + " was not received within 15 seconds"
        );
    }
}
