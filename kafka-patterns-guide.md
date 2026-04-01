# Kafka Design Patterns: Outbox + CDC + Event-Driven + Saga + DLQ + Idempotent Consumer

## Muc luc

1. [Tong quan kien truc](#1-tong-quan-kien-truc)
2. [Outbox Pattern](#2-outbox-pattern)
3. [Change Data Capture (CDC)](#3-change-data-capture-cdc)
4. [Event-Driven Architecture](#4-event-driven-architecture)
5. [Saga Pattern](#5-saga-pattern)
6. [Dead Letter Queue (DLQ)](#6-dead-letter-queue-dlq)
7. [Idempotent Consumer](#7-idempotent-consumer)
8. [Tich hop toan bo](#8-tich-hop-toan-bo)
9. [Cau hinh va Deployment](#9-cau-hinh-va-deployment)

---

## 1. Tong quan kien truc

### Van de can giai quyet

Trong microservices, khi mot hanh dong can:
- Ghi du lieu vao DB **va** gui message len Kafka
- Dam bao **consistency** giua cac service
- Xu ly **failure** mot cach graceful
- Khong mat du lieu, khong xu ly trung lap

### Luong tong the

```
[Client Request]
       |
       v
[Order Service]
       |
       |-- 1. Save Order + Outbox Event (same DB transaction)
       |
       v
[Debezium CDC] --- doc outbox table ---> [Kafka Topic: order-events]
                                              |
                    +-------------------------+-------------------------+
                    |                         |                         |
                    v                         v                         v
            [Payment Service]         [Inventory Service]       [Notification Service]
                    |                         |                         |
                    |-- Idempotent check      |-- Idempotent check      |-- Idempotent check
                    |-- Process               |-- Process               |-- Process
                    |-- Publish event         |-- Publish event         |
                    |                         |                         |
                    +--- Fail? ---> [Retry] ---> [DLQ]                  |
                    |                                                   |
                    v                                                   |
            [Saga Orchestrator / Choreography] <------------------------+
                    |
                    +--- Compensating events neu fail
```

---

## 2. Outbox Pattern

### Van de: Dual Write Problem

```
// SAI - Khong atomic
orderRepository.save(order);       // Step 1: Ghi DB
kafkaTemplate.send("order", event); // Step 2: Gui Kafka
// Neu step 2 fail -> DB co data nhung Kafka khong co event
// Neu step 1 fail sau khi step 2 -> Kafka co event nhung DB khong co data
```

### Giai phap: Outbox Table

Ghi business data va event vao **cung mot DB transaction**.

### 2.1. Outbox Table Schema

```sql
CREATE TABLE outbox_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type  VARCHAR(255) NOT NULL,    -- 'Order', 'Payment'
    aggregate_id    VARCHAR(255) NOT NULL,    -- ID cua entity
    event_type      VARCHAR(255) NOT NULL,    -- 'ORDER_CREATED', 'ORDER_PAID'
    payload         JSON NOT NULL,            -- Noi dung event
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published       BOOLEAN DEFAULT FALSE,    -- Da gui len Kafka chua
    published_at    TIMESTAMP NULL
);

CREATE INDEX idx_outbox_published ON outbox_events(published, created_at);
```

### 2.2. Entity

```java
@Entity
@Table(name = "outbox_events")
@Getter @Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "JSON", nullable = false)
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean published = false;

    private LocalDateTime publishedAt;

    public OutboxEvent(String aggregateType, String aggregateId,
                       String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }
}
```

### 2.3. Service su dung Outbox

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional // Quan trong: 1 transaction duy nhat
    public Order createOrder(CreateOrderRequest request) {
        // 1. Luu business data
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setItems(request.getItems());
        order.setTotalAmount(request.getTotalAmount());
        order.setStatus(OrderStatus.CREATED);
        order = orderRepository.save(order);

        // 2. Luu event vao outbox (CUNG transaction)
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getUserId(),
            order.getTotalAmount(),
            order.getItems()
        );

        OutboxEvent outboxEvent = new OutboxEvent(
            "Order",
            order.getId().toString(),
            "ORDER_CREATED",
            objectMapper.writeValueAsString(event)
        );
        outboxRepository.save(outboxEvent);

        // Neu exception xay ra -> ROLLBACK ca order + outbox event
        return order;
    }
}
```

### 2.4. Luu y quan trong

```
+-------------------------------------------+
|            DB TRANSACTION                  |
|                                            |
|  1. INSERT INTO orders (...)               |
|  2. INSERT INTO outbox_events (...)        |
|                                            |
|  -> COMMIT hoac ROLLBACK ca 2              |
+-------------------------------------------+
        |
        | (Debezium CDC doc outbox_events)
        v
   [Kafka Topic]
```

- **KHONG BAO GIO** gui Kafka trong transaction
- Outbox table chi la "hop thu di" - se duoc xu ly sau

---

## 3. Change Data Capture (CDC)

### CDC la gi?

CDC theo doi moi thay doi trong DB (INSERT, UPDATE, DELETE) va streaming ra Kafka.

### 3.1. Debezium + Kafka Connect

```
[MySQL Binlog] --> [Debezium Connector] --> [Kafka Topic]
```

### 3.2. Docker Compose cho Debezium

```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: demo
    command: >
      --server-id=1
      --log-bin=mysql-bin
      --binlog-format=ROW
      --binlog-row-image=FULL
      --gtid-mode=ON
      --enforce-gtid-consistency=ON

  kafka-connect:
    image: debezium/connect:2.4
    depends_on:
      - kafka
      - mysql
    ports:
      - "8083:8083"
    environment:
      BOOTSTRAP_SERVERS: kafka:29092
      GROUP_ID: connect-cluster
      CONFIG_STORAGE_TOPIC: connect-configs
      OFFSET_STORAGE_TOPIC: connect-offsets
      STATUS_STORAGE_TOPIC: connect-status
```

### 3.3. Cau hinh Debezium Connector

```json
// POST http://localhost:8083/connectors
{
  "name": "outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "root",
    "database.password": "root",
    "database.server.id": "1",
    "topic.prefix": "demo",
    "database.include.list": "demo",
    "table.include.list": "demo.outbox_events",

    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "events.${routedByValue}",

    "schema.history.internal.kafka.bootstrap.servers": "kafka:29092",
    "schema.history.internal.kafka.topic": "schema-changes"
  }
}
```

### 3.4. Ket qua

```
outbox_events table (INSERT aggregate_type='Order')
    |
    | Debezium EventRouter transform
    v
Kafka topic: "events.Order"
    Key:   aggregate_id (e.g. "order-123")
    Value: payload JSON
    Header: event_type = "ORDER_CREATED"
```

### 3.5. Phuong an thay the: Polling Publisher

Neu khong muon dung Debezium, co the dung scheduler:

```java
@Component
@RequiredArgsConstructor
public class OutboxPollingPublisher {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000) // Poll moi 1 giay
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEvent> events = outboxRepository
            .findByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            String topic = "events." + event.getAggregateType();

            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        event.setPublished(true);
                        event.setPublishedAt(LocalDateTime.now());
                        outboxRepository.save(event);
                    }
                });
        }
    }
}
```

> **So sanh**: CDC (Debezium) tot hon vi near-realtime va khong can polling. Nhung Polling Publisher don gian hon khi moi bat dau.

---

## 4. Event-Driven Architecture

### 4.1. Dinh nghia Event

```java
// Base Event
@Getter @Setter
@NoArgsConstructor
public abstract class BaseEvent {
    private String eventId;      // UUID - dung cho idempotency
    private String eventType;
    private LocalDateTime timestamp;
    private int version = 1;

    protected BaseEvent(String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
    }
}

// Order Events
@Getter @Setter
public class OrderCreatedEvent extends BaseEvent {
    private Long orderId;
    private Long userId;
    private BigDecimal totalAmount;
    private List<OrderItem> items;

    public OrderCreatedEvent() { super("ORDER_CREATED"); }

    public OrderCreatedEvent(Long orderId, Long userId,
                              BigDecimal totalAmount, List<OrderItem> items) {
        super("ORDER_CREATED");
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.items = items;
    }
}

@Getter @Setter
public class PaymentCompletedEvent extends BaseEvent {
    private Long orderId;
    private Long paymentId;
    private BigDecimal amount;
    private String transactionRef;

    public PaymentCompletedEvent() { super("PAYMENT_COMPLETED"); }
}

@Getter @Setter
public class PaymentFailedEvent extends BaseEvent {
    private Long orderId;
    private String reason;

    public PaymentFailedEvent() { super("PAYMENT_FAILED"); }
}
```

### 4.2. Kafka Consumer

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "events.Order",
        groupId = "payment-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderEvent(
            @Payload String payload,
            @Header("event_type") String eventType,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment ack
    ) {
        log.info("Received event: type={}, key={}", eventType, key);

        try {
            switch (eventType) {
                case "ORDER_CREATED" -> {
                    OrderCreatedEvent event = objectMapper
                        .readValue(payload, OrderCreatedEvent.class);
                    paymentService.processPayment(event);
                }
                case "ORDER_CANCELLED" -> {
                    OrderCancelledEvent event = objectMapper
                        .readValue(payload, OrderCancelledEvent.class);
                    paymentService.refundPayment(event);
                }
                default -> log.warn("Unknown event type: {}", eventType);
            }
            ack.acknowledge(); // Manual commit offset
        } catch (Exception e) {
            log.error("Error processing event", e);
            throw e; // De Kafka retry hoac chuyen DLQ
        }
    }
}
```

### 4.3. Kafka Producer Config

```java
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Dam bao message khong bi mat
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### 4.4. Kafka Consumer Config

```java
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
            kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties()
            .setAckMode(ContainerProperties.AckMode.MANUAL);

        // Cau hinh retry
        factory.setCommonErrorHandler(errorHandler());

        return factory;
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        // Retry 3 lan voi backoff 1s -> 2s -> 4s
        BackOff backOff = new ExponentialBackOff(1000L, 2.0);
        ((ExponentialBackOff) backOff).setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate()),
            backOff
        );

        // Khong retry cho loi nay (loi logic, retry cung khong fix duoc)
        handler.addNotRetryableExceptions(
            DeserializationException.class,
            IllegalArgumentException.class
        );

        return handler;
    }
}
```

---

## 5. Saga Pattern

### 5.1. Choreography Saga (Event-based)

Moi service lang nghe event va tu quyet dinh hanh dong tiep theo.

```
                    HAPPY PATH
                    ==========

Order Service                Payment Service              Inventory Service
     |                            |                             |
     |-- ORDER_CREATED ---------->|                             |
     |                            |-- PAYMENT_COMPLETED ------->|
     |                            |                             |-- INVENTORY_RESERVED
     |<----------------------------------------------------------|
     |-- ORDER_CONFIRMED                                        |


                    FAILURE PATH (Compensation)
                    ===========================

Order Service                Payment Service              Inventory Service
     |                            |                             |
     |-- ORDER_CREATED ---------->|                             |
     |                            |-- PAYMENT_COMPLETED ------->|
     |                            |                             |-- INVENTORY_FAILED
     |                            |<----------------------------|
     |                            |-- PAYMENT_REFUNDED          |
     |<---------------------------|                             |
     |-- ORDER_CANCELLED                                        |
```

### 5.2. Implement Choreography Saga

```java
// === Payment Service ===
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaHandler {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        log.info("Processing payment for order: {}", event.getOrderId());

        try {
            // Xu ly thanh toan
            Payment payment = new Payment();
            payment.setOrderId(event.getOrderId());
            payment.setAmount(event.getTotalAmount());
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionRef(generateTransactionRef());
            paymentRepository.save(payment);

            // Publish success event qua outbox
            PaymentCompletedEvent completedEvent = new PaymentCompletedEvent();
            completedEvent.setOrderId(event.getOrderId());
            completedEvent.setPaymentId(payment.getId());
            completedEvent.setAmount(payment.getAmount());
            completedEvent.setTransactionRef(payment.getTransactionRef());

            saveToOutbox("Payment", payment.getId().toString(),
                "PAYMENT_COMPLETED", completedEvent);

        } catch (Exception e) {
            // Publish failure event qua outbox
            PaymentFailedEvent failedEvent = new PaymentFailedEvent();
            failedEvent.setOrderId(event.getOrderId());
            failedEvent.setReason(e.getMessage());

            saveToOutbox("Payment", event.getOrderId().toString(),
                "PAYMENT_FAILED", failedEvent);
        }
    }

    @Transactional
    public void refundPayment(InventoryFailedEvent event) {
        log.info("Refunding payment for order: {}", event.getOrderId());

        Payment payment = paymentRepository.findByOrderId(event.getOrderId());
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        // Compensating event
        PaymentRefundedEvent refundedEvent = new PaymentRefundedEvent();
        refundedEvent.setOrderId(event.getOrderId());
        refundedEvent.setPaymentId(payment.getId());
        refundedEvent.setAmount(payment.getAmount());

        saveToOutbox("Payment", payment.getId().toString(),
            "PAYMENT_REFUNDED", refundedEvent);
    }

    private void saveToOutbox(String aggregateType, String aggregateId,
                               String eventType, Object event) {
        OutboxEvent outbox = new OutboxEvent(
            aggregateType, aggregateId, eventType,
            objectMapper.writeValueAsString(event)
        );
        outboxRepository.save(outbox);
    }
}
```

### 5.3. Orchestration Saga (Optional)

Dung mot Saga Orchestrator trung tam dieu phoi:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private final SagaStateRepository sagaRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Saga state machine
    private enum SagaStep {
        PAYMENT_PENDING,
        PAYMENT_COMPLETED,
        INVENTORY_PENDING,
        INVENTORY_RESERVED,
        COMPLETED,
        COMPENSATING,
        FAILED
    }

    @Transactional
    public void startSaga(OrderCreatedEvent event) {
        SagaState saga = new SagaState();
        saga.setSagaId(UUID.randomUUID().toString());
        saga.setOrderId(event.getOrderId());
        saga.setCurrentStep(SagaStep.PAYMENT_PENDING);
        saga.setPayload(objectMapper.writeValueAsString(event));
        sagaRepository.save(saga);

        // Gui command den Payment Service
        sendCommand("payment-commands", new ProcessPaymentCommand(
            saga.getSagaId(), event.getOrderId(), event.getTotalAmount()
        ));
    }

    @KafkaListener(topics = "saga-replies", groupId = "saga-orchestrator")
    public void handleReply(String message) {
        SagaReply reply = objectMapper.readValue(message, SagaReply.class);
        SagaState saga = sagaRepository.findBySagaId(reply.getSagaId());

        switch (saga.getCurrentStep()) {
            case PAYMENT_PENDING -> {
                if (reply.isSuccess()) {
                    saga.setCurrentStep(SagaStep.INVENTORY_PENDING);
                    sagaRepository.save(saga);
                    sendCommand("inventory-commands",
                        new ReserveInventoryCommand(saga));
                } else {
                    saga.setCurrentStep(SagaStep.FAILED);
                    sagaRepository.save(saga);
                    // Khong can compensate vi chua co gi thanh cong
                }
            }
            case INVENTORY_PENDING -> {
                if (reply.isSuccess()) {
                    saga.setCurrentStep(SagaStep.COMPLETED);
                    sagaRepository.save(saga);
                    sendCommand("order-commands",
                        new ConfirmOrderCommand(saga));
                } else {
                    saga.setCurrentStep(SagaStep.COMPENSATING);
                    sagaRepository.save(saga);
                    // Compensate: refund payment
                    sendCommand("payment-commands",
                        new RefundPaymentCommand(saga));
                }
            }
        }
    }
}
```

### 5.4. Choreography vs Orchestration

| Tieu chi             | Choreography          | Orchestration            |
|----------------------|-----------------------|--------------------------|
| Do phuc tap          | Don gian (2-4 steps)  | Phuc tap (5+ steps)      |
| Coupling             | Loose                 | Tight hon (orchestrator) |
| Visibility           | Kho trace             | De theo doi trang thai   |
| Single point failure | Khong                 | Orchestrator             |
| Debug                | Kho                   | De                       |

---

## 6. Dead Letter Queue (DLQ)

### 6.1. DLQ Flow

```
Consumer nhan message
       |
       v
   Xu ly duoc?
      / \
    Yes   No
     |     |
     v     v
  Commit  Retry (1s -> 2s -> 4s)
  offset     |
             v
         Van fail sau 3 lan?
            / \
          No   Yes
           |    |
           v    v
         Retry  Gui vao DLQ topic
                     |
                     v
              DLQ Consumer
              (alert / manual fix / auto retry sau)
```

### 6.2. Cau hinh DLQ trong Spring Kafka

```java
@Configuration
public class DlqConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        // DLQ publisher - gui message fail vao topic *.DLT
        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(template,
                (record, ex) -> {
                    // Custom DLQ topic naming
                    return new TopicPartition(
                        record.topic() + ".DLQ",
                        record.partition()
                    );
                });

        // Retry 3 lan: 1s, 2s, 4s (exponential backoff)
        BackOff backOff = new ExponentialBackOff(1000L, 2.0);
        ((ExponentialBackOff) backOff).setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Nhung exception khong can retry
        handler.addNotRetryableExceptions(
            DeserializationException.class,
            ClassCastException.class,
            NullPointerException.class
        );

        return handler;
    }
}
```

### 6.3. DLQ Consumer - Xu ly message that bai

```java
@Component
@Slf4j
public class DlqConsumer {

    @KafkaListener(
        topics = "events.Order.DLQ",
        groupId = "dlq-handler"
    )
    public void handleDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String errorMessage,
            @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) String originalTopic
    ) {
        log.error("""
            DLQ Message Received:
            Original Topic: {}
            Key: {}
            Value: {}
            Error: {}
            """,
            originalTopic,
            record.key(),
            record.value(),
            errorMessage
        );

        // Lua chon xu ly:
        // 1. Gui alert (Slack, Email, PagerDuty)
        alertService.sendAlert("DLQ message from " + originalTopic, errorMessage);

        // 2. Luu vao DB de manual review
        dlqRepository.save(new DlqRecord(
            originalTopic,
            record.key(),
            record.value(),
            errorMessage,
            LocalDateTime.now()
        ));

        // 3. Hoac tu dong retry sau 1 khoang thoi gian
        // retryService.scheduleRetry(record, Duration.ofMinutes(30));
    }
}
```

### 6.4. DLQ Dashboard (Optional)

```java
@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DlqController {

    private final DlqRepository dlqRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Xem danh sach message trong DLQ
    @GetMapping
    public List<DlqRecord> listDlqMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return dlqRepository.findAllByOrderByCreatedAtDesc(
            PageRequest.of(page, size));
    }

    // Retry mot message cu the
    @PostMapping("/{id}/retry")
    public ResponseEntity<String> retryMessage(@PathVariable Long id) {
        DlqRecord record = dlqRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("DLQ record not found"));

        kafkaTemplate.send(record.getOriginalTopic(),
            record.getKey(), record.getValue());

        record.setRetried(true);
        record.setRetriedAt(LocalDateTime.now());
        dlqRepository.save(record);

        return ResponseEntity.ok("Message retried successfully");
    }
}
```

---

## 7. Idempotent Consumer

### 7.1. Van de: Duplicate Messages

Kafka dam bao **at-least-once delivery**, nghia la consumer co the nhan **cung mot message nhieu lan**:

- Consumer xu ly xong nhung **chua kip commit offset** thi crash
- Khi restart, Kafka gui lai message do
- Ket qua: xu ly 2 lan -> tru tien 2 lan, gui email 2 lan...

### 7.2. Processed Events Table

```sql
CREATE TABLE processed_events (
    event_id    VARCHAR(255) PRIMARY KEY,  -- UUID cua event
    event_type  VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_processed_at (processed_at)   -- De cleanup records cu
);
```

### 7.3. Idempotent Consumer Implementation

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotentConsumer {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Kiem tra va danh dau event da xu ly.
     * Tra ve true neu event CHUA xu ly (can xu ly).
     * Tra ve false neu event DA xu ly (skip).
     */
    @Transactional
    public boolean tryProcess(String eventId, String eventType) {
        // Kiem tra da xu ly chua
        if (processedEventRepository.existsById(eventId)) {
            log.info("Event {} already processed, skipping", eventId);
            return false;
        }

        // Danh dau da xu ly
        ProcessedEvent record = new ProcessedEvent();
        record.setEventId(eventId);
        record.setEventType(eventType);
        record.setProcessedAt(LocalDateTime.now());

        try {
            processedEventRepository.save(record);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Race condition: 2 consumer instances cung xu ly
            log.info("Event {} already processed (concurrent), skipping", eventId);
            return false;
        }
    }
}
```

### 7.4. Su dung trong Consumer

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final IdempotentConsumer idempotentConsumer;
    private final PaymentSagaHandler paymentSagaHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "events.Order", groupId = "payment-service")
    @Transactional
    public void handleOrderEvent(
            @Payload String payload,
            @Header("event_type") String eventType,
            Acknowledgment ack
    ) {
        // Parse event de lay eventId
        BaseEvent baseEvent = objectMapper.readValue(payload, BaseEvent.class);

        // IDEMPOTENCY CHECK
        if (!idempotentConsumer.tryProcess(baseEvent.getEventId(), eventType)) {
            ack.acknowledge(); // Da xu ly roi, skip
            return;
        }

        // Xu ly event binh thuong
        switch (eventType) {
            case "ORDER_CREATED" -> {
                OrderCreatedEvent event = objectMapper
                    .readValue(payload, OrderCreatedEvent.class);
                paymentSagaHandler.processPayment(event);
            }
        }

        ack.acknowledge();
    }
}
```

### 7.5. Cleanup Records Cu

```java
@Component
@RequiredArgsConstructor
public class ProcessedEventCleanup {

    private final ProcessedEventRepository repository;

    @Scheduled(cron = "0 0 2 * * *") // Chay luc 2h sang moi ngay
    @Transactional
    public void cleanupOldRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = repository.deleteByProcessedAtBefore(cutoff);
        log.info("Cleaned up {} old processed event records", deleted);
    }
}
```

---

## 8. Tich hop toan bo

### 8.1. Full Flow - Order E-commerce

```
[User dat hang]
       |
       v
=== ORDER SERVICE ===
  |  @Transactional:
  |  1. save(order)              -- luu order vao DB
  |  2. save(outbox_event)       -- luu event vao outbox
  |  -> COMMIT
       |
       v
=== DEBEZIUM CDC ===
  |  Doc outbox_events table
  |  -> Publish len Kafka topic "events.Order"
       |
       +---------------------------+---------------------------+
       |                           |                           |
       v                           v                           v
=== PAYMENT SERVICE ===    === INVENTORY SERVICE ===   === NOTIFICATION ===
  |                          |                           |
  | 1. Idempotency check     | 1. Idempotency check     | 1. Idempotency check
  | 2. Process payment        | (cho PAYMENT_COMPLETED)  | 2. Gui email xac nhan
  | 3. Outbox: success/fail   |                          |    don hang
  |                           |                           |
  | Fail? -> Retry x3         |                           |
  |       -> DLQ               |                           |
  |                           |                           |
  v                           v                           |
"events.Payment"          "events.Inventory"              |
  |                           |                           |
  +---> PAYMENT_COMPLETED --->+                           |
  |                           |                           |
  |                    2. Reserve inventory                |
  |                    3. Outbox: success/fail             |
  |                           |                           |
  |                    Fail? -> INVENTORY_FAILED           |
  |                           |                           |
  |                           v                           |
  |                    === SAGA COMPENSATION ===           |
  |                    Payment refund                      |
  |                    Order cancelled                     |
  |                           |                           |
  |                           v                           |
  |                    "events.Order" (ORDER_CANCELLED)    |
  |                           |                           |
  |                           +-------------------------->+
  |                                                       |
  |                                              Gui email huy don
  |
  +---> PAYMENT_COMPLETED + INVENTORY_RESERVED
  |
  v
=== ORDER SERVICE ===
  ORDER_CONFIRMED
```

### 8.2. Package Structure

```
com.voda.demo
├── common
│   ├── event
│   │   ├── BaseEvent.java
│   │   ├── OrderCreatedEvent.java
│   │   ├── PaymentCompletedEvent.java
│   │   └── ...
│   ├── outbox
│   │   ├── OutboxEvent.java
│   │   ├── OutboxEventRepository.java
│   │   └── OutboxService.java
│   └── idempotency
│       ├── ProcessedEvent.java
│       ├── ProcessedEventRepository.java
│       └── IdempotentConsumer.java
│
├── order
│   ├── controller/OrderController.java
│   ├── service/OrderService.java
│   ├── entity/Order.java
│   ├── repository/OrderRepository.java
│   └── consumer/OrderEventConsumer.java
│
├── payment
│   ├── service/PaymentService.java
│   ├── entity/Payment.java
│   ├── repository/PaymentRepository.java
│   ├── consumer/PaymentEventConsumer.java
│   └── saga/PaymentSagaHandler.java
│
├── inventory
│   ├── service/InventoryService.java
│   ├── entity/Inventory.java
│   ├── repository/InventoryRepository.java
│   └── consumer/InventoryEventConsumer.java
│
├── notification
│   └── consumer/NotificationConsumer.java
│
├── config
│   ├── KafkaProducerConfig.java
│   ├── KafkaConsumerConfig.java
│   └── DlqConfig.java
│
└── dlq
    ├── entity/DlqRecord.java
    ├── repository/DlqRepository.java
    ├── consumer/DlqConsumer.java
    └── controller/DlqController.java
```

### 8.3. application.yml

```yaml
spring:
  application:
    name: order-service

  datasource:
    url: jdbc:mysql://localhost:3306/demo
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
    consumer:
      group-id: order-service
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        isolation.level: read_committed

# Kafka Topics
app:
  kafka:
    topics:
      order-events: events.Order
      payment-events: events.Payment
      inventory-events: events.Inventory
    dlq:
      suffix: .DLQ
      retry-attempts: 3
      backoff-initial: 1000
      backoff-multiplier: 2.0
```

### 8.4. Maven Dependencies (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

---

## 9. Cau hinh va Deployment

### 9.1. Checklist truoc khi Production

```
[ ] Kafka cluster >= 3 brokers (HA)
[ ] Replication factor = 3 cho moi topic
[ ] min.insync.replicas = 2
[ ] Producer: acks=all, enable.idempotence=true
[ ] Consumer: enable.auto.commit=false (manual commit)
[ ] Consumer: isolation.level=read_committed
[ ] DLQ topic duoc tao cho moi topic chinh
[ ] Monitoring: lag consumer, DLQ message count
[ ] Alert: khi DLQ nhan message moi
[ ] Outbox cleanup job (xoa records cu)
[ ] Processed events cleanup job
[ ] MySQL binlog duoc bat (neu dung CDC)
[ ] Debezium connector duoc monitor
```

### 9.2. Monitoring Metrics

```
# Consumer lag - neu tang lien tuc la van de
kafka_consumer_group_lag{group="payment-service", topic="events.Order"}

# DLQ message rate - nen la 0
kafka_topic_messages_in_total{topic="events.Order.DLQ"}

# Outbox pending - nen gan 0
outbox_events_pending_count

# Saga failure rate
saga_compensation_total
```

### 9.3. Testing Strategy

```
Unit Test:
  - Outbox event duoc tao dung
  - Idempotent consumer skip duplicate
  - Saga compensation logic

Integration Test:
  - Testcontainers (Kafka + MySQL)
  - Full flow: Order -> Payment -> Inventory
  - Failure scenarios: payment fail -> compensation

E2E Test:
  - Docker compose full stack
  - Simulate network failures
  - Verify DLQ handling
```

### 9.4. Testcontainers Example

```java
@SpringBootTest
@Testcontainers
class OrderSagaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("demo");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private OrderService orderService;

    @Test
    void shouldCompleteOrderSagaSuccessfully() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(1L, items, BigDecimal.TEN);

        // When
        Order order = orderService.createOrder(request);

        // Then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        });
    }

    @Test
    void shouldCompensateWhenPaymentFails() {
        // Given - setup payment service to fail
        CreateOrderRequest request = new CreateOrderRequest(
            INVALID_USER_ID, items, BigDecimal.TEN);

        // When
        Order order = orderService.createOrder(request);

        // Then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        });
    }
}
```

---

## Tham khao them

- [Microservices Patterns - Chris Richardson](https://microservices.io/patterns/)
- [Debezium Documentation](https://debezium.io/documentation/)
- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/reference/)
- [Kafka: The Definitive Guide](https://www.confluent.io/resources/kafka-the-definitive-guide-v2/)
