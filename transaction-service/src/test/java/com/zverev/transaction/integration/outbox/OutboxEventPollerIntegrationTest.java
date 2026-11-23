package com.zverev.transaction.integration.outbox;

import com.zverev.shared.entity.OutboxEvent;
import com.zverev.shared.entity.enums.OutboxEventStatus;
import com.zverev.shared.repository.OutboxEventRepository;
import com.zverev.transaction.TransactionServiceApplication;
import com.zverev.transaction.outbox.OutboxEventPoller;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@Testcontainers
@SpringBootTest(
        classes = TransactionServiceApplication.class,
        properties = "outbox.poller.fixed-delay=3600000"
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventPollerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("antifraud_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0")
    );

    @Autowired
    private OutboxEventPoller outboxEventPoller;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean(name = "kafkaTemplate")
    private KafkaTemplate kafkaTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
    }

    @Test
    void shouldPublishPendingEventsAndMarkThemAsSent() {
        String topic = "transactions.raw." + UUID.randomUUID();
        String aggregateId = UUID.randomUUID().toString();

        String payload = """
                {"transactionId":"%s","amount":125.50,"currency":"EUR"}
                """.formatted(aggregateId);

        OutboxEvent savedEvent = createOutboxEvent(aggregateId, payload, topic);

        outboxEventPoller.publishPendingEvents();

        OutboxEvent updatedEvent = findEvent(savedEvent.getId());

        assertEquals(OutboxEventStatus.SENT, updatedEvent.getStatus());
        assertEquals(1, updatedEvent.getAttempts());
        assertNotNull(updatedEvent.getProcessedAt());

        ConsumerRecord<String, String> receivedRecord = consumeMessage(topic);

        assertEquals(topic, receivedRecord.topic());
        assertEquals(aggregateId, receivedRecord.key());
        assertEquals(payload, receivedRecord.value());
    }

    @Test
    void shouldPublishOnlyTenPendingEventsPerBatch() {
        String topic = "transactions.raw." + UUID.randomUUID();

        for (int i = 0; i < 12; i++) {
            String aggregateId = UUID.randomUUID().toString();

            createOutboxEvent(aggregateId, "{}", topic);
        }

        outboxEventPoller.publishPendingEvents();

        List<OutboxEvent> events = findEventsByTopic(topic);

        long sentCount = events.stream()
                .filter(event -> event.getStatus() == OutboxEventStatus.SENT)
                .count();

        long pendingCount = events.stream()
                .filter(event -> event.getStatus() == OutboxEventStatus.PENDING)
                .count();

        assertEquals(12, events.size());
        assertEquals(10, sentCount);
        assertEquals(2, pendingCount);
    }

    @Test
    void shouldSkipRowsLockedByAnotherTransaction() throws Exception {
        for (int i = 0; i < 20; i++) {
            String aggregateId = UUID.randomUUID().toString();

            createOutboxEvent(aggregateId, "{}", "transactions.raw");
        }

        CountDownLatch firstTransactionLockedRows = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            Future<List<Long>> firstTransaction = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        List<OutboxEvent> events = outboxEventRepository.findPendingEvents();

                        List<Long> lockedIds = events.stream()
                                .map(OutboxEvent::getId)
                                .toList();

                        firstTransactionLockedRows.countDown();

                        try {
                            if (!releaseFirstTransaction.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException(
                                        "Timed out waiting to release first transaction"
                                );
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }

                        return lockedIds;
                    })
            );

            assertTrue(
                    firstTransactionLockedRows.await(10, TimeUnit.SECONDS),
                    "First transaction did not acquire row locks"
            );

            List<Long> secondTransaction = transactionTemplate.execute(status ->
                    outboxEventRepository.findPendingEvents().stream()
                            .map(OutboxEvent::getId)
                            .toList()
            );

            releaseFirstTransaction.countDown();

            List<Long> firstTransactionIds = firstTransaction.get(10, TimeUnit.SECONDS);

            assertEquals(10, firstTransactionIds.size());
            assertEquals(10, secondTransaction.size());

            Set<Long> firstIds = new HashSet<>(firstTransactionIds);
            Set<Long> secondIds = new HashSet<>(secondTransaction);

            Set<Long> intersection = new HashSet<>(firstIds);
            intersection.retainAll(secondIds);

            assertTrue(intersection.isEmpty(), "Transactions selected overlapping events");

            Set<Long> allIds = new HashSet<>(firstIds);
            allIds.addAll(secondIds);

            assertEquals(20, allIds.size());

        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldProcessEventsConcurrentlyWithoutDuplicates() throws Exception {
        String topic = "transactions.raw." + UUID.randomUUID();

        for (int i = 0; i < 20; i++) {
            String aggregateId = UUID.randomUUID().toString();

            createOutboxEvent(aggregateId, "{}", topic);
        }

        CountDownLatch firstSendStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSend = new CountDownLatch(1);

        AtomicInteger sendCounter = new AtomicInteger();

        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();

            if (sendCounter.incrementAndGet() == 1) {
                firstSendStarted.countDown();

                if (!releaseFirstSend.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to release first Kafka send"
                    );
                }
            }

            return result;
        }).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstPoller = executor.submit(
                    () -> outboxEventPoller.publishPendingEvents()
            );

            assertTrue(
                    firstSendStarted.await(15, TimeUnit.SECONDS),
                    "First Poller did not start publishing events"
            );

            Future<?> secondPoller = executor.submit(
                    () -> outboxEventPoller.publishPendingEvents()
            );

            secondPoller.get(30, TimeUnit.SECONDS);

            releaseFirstSend.countDown();

            firstPoller.get(30, TimeUnit.SECONDS);

            List<OutboxEvent> processedEvents = findEventsByTopic(topic);

            assertEquals(20, processedEvents.size());

            assertTrue(
                    processedEvents.stream()
                            .allMatch(event -> event.getStatus() == OutboxEventStatus.SENT),
                    "All events should have SENT status"
            );

            assertTrue(
                    processedEvents.stream()
                            .allMatch(event -> event.getAttempts() == 1),
                    "Every event should be processed exactly once"
            );

            assertTrue(
                    processedEvents.stream()
                            .allMatch(event -> event.getProcessedAt() != null),
                    "Every processed event should have processedAt"
            );

        } finally {
            releaseFirstSend.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldDoNothingWhenNoPendingEventsExist() {
        String topic = "transactions.raw." + UUID.randomUUID();

        outboxEventPoller.publishPendingEvents();

        assertTrue(findEventsByTopic(topic).isEmpty());
    }

    @Test
    void shouldIgnoreEventsThatAreNotPending() {
        String topic = "transactions.raw." + UUID.randomUUID();

        OutboxEvent sentEvent = createOutboxEvent(
                UUID.randomUUID().toString(),
                "{}",
                topic
        );

        OutboxEvent failedEvent = createOutboxEvent(
                UUID.randomUUID().toString(),
                "{}",
                topic
        );

        jdbcTemplate.update(
                "UPDATE outbox_events SET status = 'SENT' WHERE id = ?",
                sentEvent.getId()
        );

        jdbcTemplate.update(
                "UPDATE outbox_events SET status = 'FAILED' WHERE id = ?",
                failedEvent.getId()
        );

        outboxEventPoller.publishPendingEvents();

        OutboxEvent updatedSentEvent = findEvent(sentEvent.getId());
        OutboxEvent updatedFailedEvent = findEvent(failedEvent.getId());

        assertEquals(OutboxEventStatus.SENT, updatedSentEvent.getStatus());
        assertEquals(OutboxEventStatus.FAILED, updatedFailedEvent.getStatus());

        assertEquals(0, updatedSentEvent.getAttempts());
        assertEquals(0, updatedFailedEvent.getAttempts());
    }

    private OutboxEvent createOutboxEvent(String aggregateId, String payload, String topic) {
        OutboxEvent event = new OutboxEvent(
                aggregateId,
                payload,
                topic,
                aggregateId
        );

        return outboxEventRepository.saveAndFlush(event);
    }

    private OutboxEvent findEvent(Long id) {
        return outboxEventRepository.findById(id).orElseThrow();
    }

    private List<OutboxEvent> findEventsByTopic(String topic) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getTopic().equals(topic))
                .toList();
    }

    private ConsumerRecord<String, String> consumeMessage(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));

            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();

            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                if (!records.isEmpty()) {
                    return records.iterator().next();
                }
            }
        }

        throw new AssertionError("Kafka message was not received within 15 seconds");
    }

    @Test
    void shouldRollbackPollerTransactionWhenPublishingIsInterrupted() {
        String aggregateId = UUID.randomUUID().toString();

        OutboxEvent savedEvent = createOutboxEvent(
                aggregateId,
                "{}",
                "transactions.raw"
        );

        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return new CompletableFuture<>();
        })
                .when(kafkaTemplate)
                .send(anyString(), anyString(), anyString());

        RuntimeException exception;

        try {
            exception = assertThrows(
                    RuntimeException.class,
                    () -> outboxEventPoller.publishPendingEvents()
            );

            assertTrue(
                    Thread.currentThread().isInterrupted(),
                    "Interrupt flag should be restored"
            );

        } finally {
            Thread.interrupted();
        }

        assertTrue(
                exception.getMessage().contains("Interrupted while publishing outbox event"),
                "Exception should describe the interrupted Kafka publishing"
        );

        OutboxEvent updatedEvent = findEvent(savedEvent.getId());

        assertEquals(
                OutboxEventStatus.PENDING,
                updatedEvent.getStatus()
        );

        assertEquals(
                0,
                updatedEvent.getAttempts()
        );

        assertEquals(
                null,
                updatedEvent.getProcessedAt()
        );
    }
}
