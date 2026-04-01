# Kafka Learning Roadmap - Tu Zero den Master

> Thoi gian uoc tinh: 6-12 thang (2-3h/ngay)
> Ngon ngu: Java + Spring Boot
> Muc tieu: Thiet ke va van hanh he thong Kafka production-ready

---

## Muc luc

1. [Phase 1: Nen tang (2-3 tuan)](#phase-1-nen-tang-2-3-tuan)
2. [Phase 2: Trung cap (3-4 tuan)](#phase-2-trung-cap-3-4-tuan)
3. [Phase 3: Nang cao (4-6 tuan)](#phase-3-nang-cao-4-6-tuan)
4. [Phase 4: Production-ready (4-6 tuan)](#phase-4-production-ready-4-6-tuan)
5. [Phase 5: Expert (ongoing)](#phase-5-expert-ongoing)
6. [Tai lieu tham khao](#tai-lieu-tham-khao)
7. [Meo hoc hieu qua](#meo-hoc-hieu-qua)

---

## Phase 1: Nen tang (2-3 tuan)

### Muc tieu
- [ ] Hieu Kafka la gi, tai sao can dung
- [ ] Cai dat va chay duoc Kafka local
- [ ] Viet duoc Producer va Consumer don gian
- [ ] Hieu cac khai niem core

### Tuan 1: Khai niem + Setup

#### Ly thuyet can nam

```
Kafka Cluster
├── Broker 1
│   ├── Topic: "orders" 
│   │   ├── Partition 0: [msg0, msg1, msg2, msg3]  <- offset
│   │   ├── Partition 1: [msg0, msg1, msg2]
│   │   └── Partition 2: [msg0, msg1]
│   └── Topic: "payments"
│       └── ...
├── Broker 2 (replica)
└── Broker 3 (replica)

Producer ----> [Topic/Partition] ----> Consumer Group
                                        ├── Consumer 1 (Partition 0)
                                        ├── Consumer 2 (Partition 1)
                                        └── Consumer 3 (Partition 2)
```

#### Khai niem bat buoc phai hieu

| Khai niem | Giai thich | Vi du |
|-----------|-----------|-------|
| **Broker** | Mot Kafka server | 1 may chay Kafka |
| **Topic** | Kenh chua message | "orders", "payments" |
| **Partition** | Chia nho topic de xu ly song song | Topic "orders" co 3 partitions |
| **Offset** | Vi tri cua message trong partition | Message thu 5 trong partition 0 |
| **Producer** | Gui message vao topic | Order Service gui event |
| **Consumer** | Doc message tu topic | Payment Service doc event |
| **Consumer Group** | Nhom consumer chia nhau doc | 3 instance cua Payment Service |
| **Replication** | Sao chep du lieu sang broker khac | Replication factor = 3 |
| **Leader/Follower** | Partition co 1 leader, nhieu follower | Leader xu ly read/write |
| **Zookeeper/KRaft** | Quan ly metadata cua cluster | (KRaft thay Zookeeper tu 3.x) |

#### Thuc hanh

**Bai 1.1: Setup Kafka bang Docker**

```yaml
# docker-compose.yml
version: '3.8'
services:
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,HOST://localhost:9092
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093,HOST://0.0.0.0:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
    depends_on:
      - kafka
```

```bash
# Chay
docker-compose up -d

# Xem Kafka UI tai http://localhost:8090
```

**Bai 1.2: CLI co ban**

```bash
# Vao container kafka
docker exec -it <kafka-container> bash

# Tao topic
kafka-topics --create --topic my-first-topic \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# List topics
kafka-topics --list --bootstrap-server localhost:9092

# Describe topic
kafka-topics --describe --topic my-first-topic \
  --bootstrap-server localhost:9092

# Gui message (Producer)
kafka-console-producer --topic my-first-topic \
  --bootstrap-server localhost:9092
> Hello Kafka
> Message thu 2

# Doc message (Consumer) - mo terminal khac
kafka-console-consumer --topic my-first-topic \
  --bootstrap-server localhost:9092 \
  --from-beginning

# Doc voi consumer group
kafka-console-consumer --topic my-first-topic \
  --bootstrap-server localhost:9092 \
  --group my-group
```

### Tuan 2: Spring Kafka co ban

**Bai 1.3: Spring Boot Producer + Consumer**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      group-id: my-app
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

```java
// Producer
@RestController
@RequiredArgsConstructor
public class MessageController {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping("/send")
    public String send(@RequestParam String message) {
        kafkaTemplate.send("my-first-topic", message);
        return "Sent: " + message;
    }

    // Gui voi key (cung key -> cung partition -> dam bao thu tu)
    @PostMapping("/send/{key}")
    public String sendWithKey(@PathVariable String key,
                               @RequestParam String message) {
        kafkaTemplate.send("my-first-topic", key, message);
        return "Sent to key " + key + ": " + message;
    }
}

// Consumer
@Component
@Slf4j
public class MessageConsumer {

    @KafkaListener(topics = "my-first-topic", groupId = "my-app")
    public void listen(String message) {
        log.info("Received: {}", message);
    }

    // Consumer voi metadata
    @KafkaListener(topics = "my-first-topic", groupId = "my-app-v2")
    public void listenWithMetadata(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received: {} from partition: {}, offset: {}",
            message, partition, offset);
    }
}
```

### Bai tap tong hop Phase 1

**Du an: Chat Application**

```
Yeu cau:
1. User gui message qua REST API
2. Message duoc publish len Kafka topic "chat-messages"
3. Consumer doc va luu vao DB
4. API doc lich su chat

Muc tieu hoc:
- Producer/Consumer co ban
- Hieu partition va ordering
- Hieu consumer group

Nang cao (optional):
- Nhieu chat room = nhieu topic hoac dung key
- Nhieu consumer instances
```

### Kiem tra kien thuc Phase 1

```
[ ] Giai thich duoc Kafka khac gi RabbitMQ/ActiveMQ
[ ] Giai thich partition dung de lam gi
[ ] Tai sao cung key thi vao cung partition
[ ] Consumer group hoat dong nhu the nao
[ ] Dieu gi xay ra khi consumer nhieu hon partition
[ ] Offset la gi, commit offset nghia la gi
[ ] Replication factor anh huong gi
```

---

## Phase 2: Trung cap (3-4 tuan)

### Muc tieu
- [ ] Xu ly loi va retry
- [ ] Serialization nang cao (JSON, Avro)
- [ ] Manual commit va cac che do commit
- [ ] Hieu ve delivery semantics
- [ ] Partitioning strategy

### Tuan 3: Serialization va Schema

#### JSON Serialization

```java
// Config
@Configuration
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public ConsumerFactory<String, OrderEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.voda.demo.*");
        return new DefaultKafkaConsumerFactory<>(props);
    }
}

// Su dung
kafkaTemplate.send("orders", order.getId().toString(), orderEvent);
```

#### Avro + Schema Registry (khuyen dung cho production)

```
Producer                    Schema Registry              Consumer
   |                              |                          |
   |-- Register schema ---------->|                          |
   |<-- Schema ID ----------------|                          |
   |                              |                          |
   |-- Send (schema ID + data) ---|------------------------->|
   |                              |                          |
   |                              |<-- Get schema (by ID) ---|
   |                              |-- Return schema -------->|
   |                              |                          |-- Deserialize
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
    <version>7.5.0</version>
</dependency>
```

```avro
// src/main/avro/order_event.avsc
{
  "type": "record",
  "name": "OrderEvent",
  "namespace": "com.voda.demo.avro",
  "fields": [
    {"name": "orderId", "type": "long"},
    {"name": "userId", "type": "long"},
    {"name": "amount", "type": "double"},
    {"name": "status", "type": "string"},
    {"name": "timestamp", "type": "long", "logicalType": "timestamp-millis"}
  ]
}
```

#### Bai tap

```
Bai 2.1: Chuyen tu String serialization sang JSON
Bai 2.2: Setup Schema Registry va dung Avro
Bai 2.3: Thu thay doi schema (them field) va xem backward compatibility
```

### Tuan 4: Error Handling va Delivery Semantics

#### 3 muc do delivery

```
1. AT-MOST-ONCE  (mat message duoc, khong duplicate)
   Producer: acks=0, fire-and-forget
   Consumer: auto-commit

2. AT-LEAST-ONCE (khong mat, co the duplicate)  <-- MAC DINH
   Producer: acks=all, retries > 0
   Consumer: manual commit SAU khi xu ly

3. EXACTLY-ONCE  (khong mat, khong duplicate)
   Producer: enable.idempotence=true + transactional.id
   Consumer: read_committed + idempotent processing
```

#### Manual Commit

```java
@Configuration
public class ConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
            kafkaListenerContainerFactory() {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory());

        // Tat auto commit, bat manual
        factory.getContainerProperties()
            .setAckMode(ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}

@Component
public class OrderConsumer {

    // MANUAL commit - an toan nhat
    @KafkaListener(topics = "orders")
    public void listen(String message, Acknowledgment ack) {
        try {
            processOrder(message);
            ack.acknowledge();  // Chi commit khi xu ly THANH CONG
        } catch (Exception e) {
            // KHONG commit -> Kafka gui lai message
            log.error("Failed to process", e);
            throw e;
        }
    }
}
```

#### Cac che do AckMode

```
RECORD          - Commit sau moi record
BATCH           - Commit sau moi batch (default cua Spring Kafka)
MANUAL          - Goi ack.acknowledge() thu cong
MANUAL_IMMEDIATE - Commit ngay lap tuc khi goi ack
```

#### Error Handler voi Retry + DLQ

```java
@Bean
public DefaultErrorHandler errorHandler(
        KafkaTemplate<String, String> kafkaTemplate) {

    // Khi fail het retry -> gui vao DLQ
    var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

    // Retry 3 lan: 1s -> 2s -> 4s
    var backOff = new ExponentialBackOff(1000L, 2.0);

    var handler = new DefaultErrorHandler(recoverer, backOff);

    // Loi khong the retry (retry cung khong fix duoc)
    handler.addNotRetryableExceptions(
        DeserializationException.class,
        IllegalArgumentException.class
    );

    return handler;
}
```

#### Bai tap

```
Bai 2.4: Implement manual commit, thu kill consumer giua chung xu ly
        -> Xem message co duoc gui lai khong
Bai 2.5: Setup retry + DLQ, thu throw exception trong consumer
        -> Xem message co vao DLQ khong
Bai 2.6: So sanh at-most-once vs at-least-once bang cach dem so message
```

### Tuan 5: Partitioning va Consumer Groups

#### Partitioning Strategy

```
Default: hash(key) % num_partitions

Key = userId -> cung user luon vao cung partition -> dam bao thu tu

Key = null -> round-robin (khong dam bao thu tu)
```

```java
// Custom partitioner
public class OrderPartitioner implements Partitioner {

    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                          Object value, byte[] valueBytes, Cluster cluster) {

        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();

        if (key == null) {
            return ThreadLocalRandom.current().nextInt(numPartitions);
        }

        // VIP orders -> partition 0 (xu ly uu tien)
        if (key.toString().startsWith("VIP-")) {
            return 0;
        }

        // Cac order khac -> hash binh thuong
        return Math.abs(key.hashCode()) % numPartitions;
    }
}
```

#### Consumer Group Rebalancing

```
Ban dau: 3 partitions, 2 consumers
  Consumer A: [P0, P1]
  Consumer B: [P2]

Them Consumer C:
  -> REBALANCE xay ra
  Consumer A: [P0]
  Consumer B: [P1]
  Consumer C: [P2]

Consumer C chet:
  -> REBALANCE xay ra
  Consumer A: [P0, P2]
  Consumer B: [P1]

Luu y: Trong luc rebalance, KHONG consumer nao xu ly message
  -> Nen tranh rebalance khong can thiet
```

#### Bai tap

```
Bai 2.7: Gui 1000 message voi key = userId
        -> Xac nhan cung userId luon vao cung partition
Bai 2.8: Chay 3 consumer instances, observe rebalancing
        -> Kill 1 instance, xem chuyen gi xay ra
Bai 2.9: Thu chay 5 consumers cho 3 partitions
        -> Xac nhan 2 consumers idle
```

### Kiem tra kien thuc Phase 2

```
[ ] Khi nao dung JSON, khi nao dung Avro
[ ] Schema evolution la gi, backward vs forward compatibility
[ ] 3 delivery semantics va trade-off cua tung cai
[ ] Tai sao manual commit an toan hon auto commit
[ ] Rebalance xay ra khi nao va anh huong gi
[ ] Lam sao dam bao thu tu message
[ ] DLQ dung de lam gi
```

---

## Phase 3: Nang cao (4-6 tuan)

### Muc tieu
- [ ] Outbox Pattern + CDC
- [ ] Saga Pattern
- [ ] Idempotent Consumer
- [ ] Kafka Streams co ban
- [ ] Kafka Transactions

### Tuan 6-7: Outbox + CDC + Idempotent Consumer

> Chi tiet xem file `kafka-patterns-guide.md`

#### Tuan 6: Outbox Pattern

```
Bai 3.1: Implement Outbox table + Polling Publisher
  - Tao outbox_events table
  - Ghi business data + outbox event trong 1 transaction
  - Scheduler doc outbox va publish len Kafka

Bai 3.2: Nang cap len CDC voi Debezium
  - Setup Debezium connector
  - Cau hinh EventRouter transform
  - So sanh latency giua Polling vs CDC
```

#### Tuan 7: Idempotent Consumer + DLQ nang cao

```
Bai 3.3: Implement Idempotent Consumer
  - Tao processed_events table
  - Check duplicate truoc khi xu ly
  - Thu gui cung message 2 lan -> xac nhan chi xu ly 1 lan

Bai 3.4: DLQ voi monitoring
  - DLQ consumer luu vao DB
  - REST API xem va retry DLQ messages
  - Thu nhieu failure scenarios
```

### Tuan 8-9: Saga Pattern

```
Bai 3.5: Choreography Saga
  Xay dung flow:
  Order Service -> Payment Service -> Inventory Service

  Happy path:
    ORDER_CREATED -> PAYMENT_COMPLETED -> INVENTORY_RESERVED -> ORDER_CONFIRMED

  Failure path:
    ORDER_CREATED -> PAYMENT_COMPLETED -> INVENTORY_FAILED
    -> PAYMENT_REFUNDED -> ORDER_CANCELLED

  Kiem tra:
  - [ ] Happy path chay dung
  - [ ] Payment fail -> order cancelled
  - [ ] Inventory fail -> payment refunded -> order cancelled
  - [ ] Duplicate event -> xu ly dung 1 lan (idempotent)

Bai 3.6: Orchestration Saga (optional)
  - Tao SagaOrchestrator voi state machine
  - So sanh voi Choreography
```

### Tuan 10-11: Kafka Streams

#### Kafka Streams la gi

```
Kafka Streams: thu vien xu ly stream data TRONG Kafka
  - Khong can cluster rieng (khac Flink, Spark)
  - Chay nhu 1 Java application binh thuong
  - Stateful processing (aggregation, join, windowing)

Input Topic ──> [Kafka Streams App] ──> Output Topic
                     |
                 [State Store]
                 (RocksDB local)
```

#### Vi du co ban

```java
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration streamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "order-analytics");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public KStream<String, String> orderStream(StreamsBuilder builder) {
        KStream<String, String> stream = builder.stream("events.Order");

        // 1. Filter: chi lay ORDER_CREATED events
        KStream<String, String> createdOrders = stream
            .filter((key, value) -> value.contains("ORDER_CREATED"));

        // 2. Count orders per user (stateful)
        KTable<String, Long> orderCountPerUser = createdOrders
            .groupBy((key, value) -> extractUserId(value))
            .count(Materialized.as("order-count-store"));

        // 3. Windowed aggregation: orders per hour
        KTable<Windowed<String>, Long> ordersPerHour = createdOrders
            .groupBy((key, value) -> "all")
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofHours(1)))
            .count(Materialized.as("orders-per-hour-store"));

        // 4. Output to new topic
        orderCountPerUser.toStream()
            .to("analytics.order-count-per-user");

        return stream;
    }
}
```

#### Bai tap

```
Bai 3.7: Kafka Streams - dem so order theo user (realtime)
Bai 3.8: Kafka Streams - tinh tong doanh thu theo gio (windowed aggregation)
Bai 3.9: Kafka Streams - join order stream voi user table (KStream-KTable join)
```

### Tuan 11: Kafka Transactions

```java
// Transactional Producer: dam bao atomic writes
@Configuration
public class TransactionalProducerConfig {

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-tx-");

        DefaultKafkaProducerFactory<String, String> factory =
            new DefaultKafkaProducerFactory<>(props);
        factory.setTransactionIdPrefix("order-tx-");
        return factory;
    }
}

// Su dung
@Service
public class OrderService {

    @Transactional
    public void createOrderWithEvents(Order order) {
        kafkaTemplate.executeInTransaction(ops -> {
            // Ca 2 message duoc gui ATOMIC
            ops.send("events.Order", orderCreatedEvent);
            ops.send("events.Notification", notificationEvent);
            return null;
            // Neu 1 trong 2 fail -> ROLLBACK ca 2
        });
    }
}

// Consumer: chi doc committed messages
// Consumer config: isolation.level = read_committed
```

#### Bai tap

```
Bai 3.10: Implement transactional producer
         -> Gui 2 messages atomic
         -> Thu throw exception giua 2 send -> xac nhan rollback
Bai 3.11: Consumer voi read_committed
         -> Xac nhan khong doc duoc uncommitted messages
```

### Kiem tra kien thuc Phase 3

```
[ ] Giai thich Outbox Pattern giai quyet van de gi
[ ] CDC hoat dong nhu the nao (binlog -> Debezium -> Kafka)
[ ] Choreography vs Orchestration Saga: khi nao dung cai nao
[ ] Compensating transaction la gi
[ ] Idempotent consumer giai quyet van de gi
[ ] Kafka Streams khac gi Consumer thong thuong
[ ] KTable vs KStream
[ ] Kafka Transaction dam bao dieu gi
```

---

## Phase 4: Production-ready (4-6 tuan)

### Muc tieu
- [ ] Setup Kafka cluster multi-broker
- [ ] Monitoring va alerting
- [ ] Performance tuning
- [ ] Security
- [ ] Troubleshooting production issues

### Tuan 12-13: Kafka Cluster va Operations

#### Multi-broker Setup

```yaml
# docker-compose-cluster.yml
version: '3.8'
services:
  kafka-1:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093,HOST://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:29092,HOST://localhost:9092
      # ...

  kafka-2:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_NODE_ID: 2
      # ...

  kafka-3:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_NODE_ID: 3
      # ...
```

#### Topic Configuration cho Production

```bash
# Tao topic voi cau hinh production
kafka-topics --create --topic events.Order \
  --bootstrap-server localhost:9092 \
  --partitions 6 \
  --replication-factor 3 \
  --config min.insync.replicas=2 \
  --config retention.ms=604800000 \        # 7 ngay
  --config cleanup.policy=delete \
  --config max.message.bytes=1048576       # 1MB
```

#### Chon so Partition

```
Quy tac chung:
  Partitions >= so consumer instances (max)

Vi du:
  - Hien tai: 3 consumers
  - Tuong lai: co the scale len 12
  -> Dat 12 partitions

Luu y:
  - Nhieu partition = nhieu file handles, nhieu memory
  - Khong the GIAM partition (chi tang duoc)
  - Thu tu chi dam bao TRONG 1 partition
  - Khuyen nghi: 6-12 cho hau het truong hop
```

#### Bai tap

```
Bai 4.1: Setup 3-broker cluster
Bai 4.2: Tao topic voi replication-factor=3, kill 1 broker
         -> Xac nhan cluster van hoat dong
Bai 4.3: Kill leader broker -> observe leader election
Bai 4.4: Thu cau hinh min.insync.replicas=2, kill 2 brokers
         -> Xac nhan producer khong gui duoc (acks=all)
```

### Tuan 14-15: Monitoring va Alerting

#### Kafka Metrics quan trong

```
=== BROKER METRICS ===
kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec
kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec
kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec
kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions  # > 0 la van de
kafka.server:type=ReplicaManager,name=OfflinePartitionsCount      # > 0 la nghiem trong
kafka.controller:type=KafkaController,name=ActiveControllerCount  # Phai = 1

=== CONSUMER METRICS ===
kafka.consumer:type=consumer-fetch-manager-metrics,name=records-lag-max
  # Consumer lag: so message chua xu ly
  # Neu tang lien tuc -> consumer cham qua hoac chet

=== PRODUCER METRICS ===
kafka.producer:type=producer-metrics,name=record-error-rate
  # > 0 nghia la co message gui that bai
```

#### Prometheus + Grafana Setup

```yaml
# docker-compose-monitoring.yml
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin

  jmx-exporter:
    # Export Kafka JMX metrics cho Prometheus
    # Cau hinh chi tiet: https://github.com/prometheus/jmx_exporter
```

#### Alerts nen co

```yaml
# Prometheus alert rules
groups:
  - name: kafka-alerts
    rules:
      # Consumer lag qua cao
      - alert: HighConsumerLag
        expr: kafka_consumer_group_lag > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Consumer group {{ $labels.group }} lag cao"

      # Under-replicated partitions
      - alert: UnderReplicatedPartitions
        expr: kafka_server_replica_manager_under_replicated_partitions > 0
        for: 1m
        labels:
          severity: critical

      # Offline partitions
      - alert: OfflinePartitions
        expr: kafka_controller_offline_partitions_count > 0
        for: 1m
        labels:
          severity: critical

      # DLQ co message
      - alert: DlqMessagesReceived
        expr: increase(kafka_topic_messages_in_total{topic=~".*DLQ.*"}[5m]) > 0
        labels:
          severity: warning
        annotations:
          summary: "DLQ nhan message moi"
```

#### Bai tap

```
Bai 4.5: Setup Prometheus + Grafana, import Kafka dashboard
Bai 4.6: Tao alert cho consumer lag
Bai 4.7: Simulate consumer lag (stop consumer, gui 10k messages)
         -> Observe metrics va alert
```

### Tuan 16-17: Performance Tuning

#### Producer Tuning

```properties
# Batch messages truoc khi gui (tang throughput)
batch.size=16384              # 16KB (default)
linger.ms=5                   # Doi 5ms de gom batch (default=0)

# Nen message (giam bandwidth)
compression.type=lz4          # lz4 nhanh, snappy tot, gzip nen nhieu nhat

# Buffer
buffer.memory=33554432         # 32MB buffer cho producer

# Throughput vs Latency
# Throughput cao:  batch.size=65536, linger.ms=20, compression=lz4
# Latency thap:   batch.size=1, linger.ms=0, compression=none
```

#### Consumer Tuning

```properties
# So records fetch moi lan poll
max.poll.records=500           # default = 500

# Thoi gian xu ly toi da truoc khi Kafka coi consumer la chet
max.poll.interval.ms=300000    # 5 phut (default)

# Heartbeat
session.timeout.ms=45000       # 45s
heartbeat.interval.ms=15000    # 15s (nen = 1/3 session timeout)

# Fetch size
fetch.min.bytes=1              # Fetch ngay khi co data
fetch.max.wait.ms=500          # Doi toi da 500ms

# Concurrency (Spring Kafka)
spring.kafka.listener.concurrency=3  # So threads xu ly
```

#### Benchmark

```java
// Producer benchmark
@Component
public class KafkaProducerBenchmark {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void benchmark(int messageCount, int messageSize) {
        String payload = "x".repeat(messageSize);
        long start = System.currentTimeMillis();

        List<CompletableFuture<SendResult<String, String>>> futures =
            new ArrayList<>();

        for (int i = 0; i < messageCount; i++) {
            futures.add(kafkaTemplate.send("benchmark-topic",
                String.valueOf(i), payload));
        }

        // Doi tat ca gui xong
        CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])).join();

        long elapsed = System.currentTimeMillis() - start;
        double throughput = messageCount * 1000.0 / elapsed;

        log.info("Sent {} messages in {}ms = {:.0f} msg/s",
            messageCount, elapsed, throughput);
    }
}
```

#### Bai tap

```
Bai 4.8: Benchmark producer voi cac cau hinh khac nhau
         -> So sanh throughput va latency
Bai 4.9: Benchmark consumer voi concurrency 1 vs 3 vs 6
Bai 4.10: Tim ra cau hinh toi uu cho 10k msg/s
```

### Tuan 17: Security

```properties
# === SSL/TLS ===
# Encrypt data in transit
security.protocol=SSL
ssl.truststore.location=/path/to/truststore.jks
ssl.truststore.password=changeit
ssl.keystore.location=/path/to/keystore.jks
ssl.keystore.password=changeit

# === SASL ===
# Authentication
security.protocol=SASL_SSL
sasl.mechanism=SCRAM-SHA-256
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required \
  username="myuser" \
  password="mypassword";

# === ACL ===
# Authorization
kafka-acls --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:order-service \
  --operation Read \
  --topic events.Order

kafka-acls --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:order-service \
  --operation Write \
  --topic events.Payment
```

### Kiem tra kien thuc Phase 4

```
[ ] Chon so partition nhu the nao
[ ] min.insync.replicas la gi, tai sao quan trong
[ ] Consumer lag la gi, lam sao giam
[ ] batch.size va linger.ms anh huong throughput/latency nhu the nao
[ ] Kafka security gom nhung gi
[ ] Under-replicated partition la gi, xu ly nhu the nao
[ ] Cach benchmark Kafka
```

---

## Phase 5: Expert (ongoing)

### Cac chu de nang cao de nghien cuu tiep

```
1. Multi-DC Replication
   - MirrorMaker 2
   - Active-Active vs Active-Passive
   - Conflict resolution

2. Kafka Connect Deep Dive
   - Custom connectors
   - Transforms (SMT)
   - Converter patterns

3. ksqlDB
   - Stream processing bang SQL
   - Materialized views
   - Push vs Pull queries

4. Kafka trên Kubernetes
   - Strimzi operator
   - StatefulSet cho Kafka
   - Persistent volumes

5. Event Sourcing + CQRS
   - Full event sourcing voi Kafka
   - Materialized views tu events
   - Snapshots

6. Advanced Kafka Streams
   - Custom StateStore
   - Processor API (thay vi DSL)
   - Interactive queries
   - Exactly-once processing

7. Capacity Planning
   - Disk: message_size * messages_per_sec * retention * replication_factor
   - Network: bytes_in + bytes_out * (replication_factor - 1)
   - Memory: page cache sizing

8. Incident Response
   - Broker mat data -> xu ly nhu the nao
   - Consumer lag dot ngot -> debug steps
   - Full disk -> emergency procedures
   - Split brain scenario
```

---

## Tai lieu tham khao

### Sach

| Sach | Muc do | Ghi chu |
|------|--------|---------|
| Kafka: The Definitive Guide (v2) | Beginner-Advanced | Sach nen tang tot nhat |
| Designing Data-Intensive Applications | Advanced | Hieu ly thuyet distributed systems |
| Kafka Streams in Action | Intermediate | Stream processing |
| Building Event-Driven Microservices | Advanced | Architecture patterns |

### Khoa hoc online

```
1. Confluent Developer (mien phi)
   https://developer.confluent.io/learn-kafka/
   -> Khoa hoc chinh thuc tu Confluent, rat chat luong

2. Conduktor Kafkademy (mien phi)
   -> Bai tap thuc hanh tot

3. Udemy - Apache Kafka Series (Stephane Maarek)
   -> Day du va de hieu, phu hop nguoi moi
```

### Tools nen biet

```
1. Kafka UI         - Web UI quan ly Kafka (khuyen dung)
2. AKHQ             - Kafka admin UI thay the
3. Conduktor        - Desktop app quan ly Kafka
4. kcat (kafkacat)  - CLI tool manh hon kafka-console-*
5. Offset Explorer  - GUI xem topic/partition/offset
6. Testcontainers   - Kafka trong unit test
```

### Cong dong

```
1. Confluent Community Slack
2. r/apachekafka (Reddit)
3. Stack Overflow [apache-kafka]
4. Confluent Blog (rat nhieu bai hay)
```

---

## Meo hoc hieu qua

### 1. Hoc bang cach pha

```
- Kill broker giua chung -> xem chuyen gi xay ra
- Stop consumer roi gui 10k messages -> observe lag
- Gui message sai format -> xem error handling
- Fill disk cua broker -> xem Kafka xu ly the nao

-> Hieu failure modes = hieu Kafka that su
```

### 2. Doc source code

```
Khong can doc het, nhung nen doc:
- ProducerRecord.java     -> hieu producer gui gi
- ConsumerRecord.java     -> hieu consumer nhan gi
- KafkaProducer.send()    -> hieu flow gui message
- KafkaConsumer.poll()    -> hieu flow nhan message
```

### 3. Viet blog / ghi chep

```
Sau moi phase, viet lai nhung gi da hoc:
- Lam gi
- Gap loi gi
- Giai quyet nhu the nao
- Rut ra bai hoc gi

-> Viet lai = hieu sau hon 10 lan
```

### 4. Side project thuc te

```
Goi y project:
- Phase 1: Chat app realtime
- Phase 2: Log aggregation system (nhu ELK nhung don gian)
- Phase 3: E-commerce order system (Outbox + Saga + DLQ)
- Phase 4: IoT data pipeline (100k events/s)
```

### 5. Nguyen tac 70-20-10

```
70% thuc hanh code + lab
20% doc tai lieu + blog
10% xem video / khoa hoc

-> KHONG DOC SUONG, phai lam thi moi nho
```

---

## Tracking tien do

```
Ngay bat dau: _______________

Phase 1: Nen tang
  Bat dau: ___________  Hoan thanh: ___________
  [ ] Setup Kafka local
  [ ] CLI co ban
  [ ] Spring Kafka Producer/Consumer
  [ ] Chat app project
  [ ] Kiem tra kien thuc

Phase 2: Trung cap
  Bat dau: ___________  Hoan thanh: ___________
  [ ] JSON/Avro serialization
  [ ] Manual commit + Error handling
  [ ] Delivery semantics
  [ ] Partitioning + Consumer groups
  [ ] Kiem tra kien thuc

Phase 3: Nang cao
  Bat dau: ___________  Hoan thanh: ___________
  [ ] Outbox + CDC
  [ ] Idempotent Consumer
  [ ] Saga Pattern
  [ ] Kafka Streams
  [ ] Kafka Transactions
  [ ] E-commerce project
  [ ] Kiem tra kien thuc

Phase 4: Production-ready
  Bat dau: ___________  Hoan thanh: ___________
  [ ] Multi-broker cluster
  [ ] Monitoring + Alerting
  [ ] Performance tuning
  [ ] Security
  [ ] Benchmark 10k msg/s
  [ ] Kiem tra kien thuc

Phase 5: Expert
  [ ] Bat dau nghien cuu: ___________
  [ ] Chu de dang hoc: ________________
```
