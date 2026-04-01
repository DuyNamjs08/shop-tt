# RabbitMQ Learning Roadmap - Tu Zero den Master

> Thoi gian uoc tinh: 2-4 thang (2-3h/ngay)
> Ngon ngu: Java + Spring Boot
> Muc tieu: Thiet ke va van hanh he thong messaging voi RabbitMQ

---

## Muc luc

1. [Phase 1: Nen tang (1-2 tuan)](#phase-1-nen-tang-1-2-tuan)
2. [Phase 2: Trung cap (2-3 tuan)](#phase-2-trung-cap-2-3-tuan)
3. [Phase 3: Nang cao (3-4 tuan)](#phase-3-nang-cao-3-4-tuan)
4. [Phase 4: Production-ready (2-3 tuan)](#phase-4-production-ready-2-3-tuan)
5. [Tai lieu tham khao](#tai-lieu-tham-khao)
6. [Meo hoc hieu qua](#meo-hoc-hieu-qua)

---

## Phase 1: Nen tang (1-2 tuan)

### Muc tieu
- [ ] Hieu RabbitMQ la gi, khi nao can dung
- [ ] Cai dat va chay duoc RabbitMQ local
- [ ] Hieu mo hinh AMQP
- [ ] Viet duoc Producer va Consumer voi Spring Boot

### Tuan 1: Khai niem + Setup

#### RabbitMQ hoat dong nhu the nao

```
Producer --> Exchange --> Binding --> Queue --> Consumer

Chi tiet:
                          +---> [Queue A] ---> Consumer 1
                          |
Producer ---> [Exchange] -+---> [Queue B] ---> Consumer 2
                          |
                          +---> [Queue C] ---> Consumer 3

Exchange = "Buu dien" - nhan thu va phan phoi vao dung hop thu (queue)
Binding  = "Quy tac" - xac dinh message nao vao queue nao
Queue    = "Hop thu" - luu message cho den khi consumer doc
```

#### So sanh voi Kafka

```
Kafka:    Producer -> Topic -> Partition -> Consumer (pull)
RabbitMQ: Producer -> Exchange -> Queue -> Consumer (push)

Kafka:    Luu message vinh vien (theo retention)
RabbitMQ: Xoa message sau khi consumer ack

Kafka:    Consumer tu keo message (pull)
RabbitMQ: Broker day message cho consumer (push)
```

#### Khai niem bat buoc phai hieu

| Khai niem | Giai thich | Vi du |
|-----------|-----------|-------|
| **Producer** | Gui message | Order Service gui event |
| **Consumer** | Nhan message | Email Service nhan event |
| **Queue** | Hang doi luu message | "email-queue", "payment-queue" |
| **Exchange** | Bo dinh tuyen message | Nhan message va chuyen vao queue |
| **Binding** | Lien ket Exchange voi Queue | Theo routing key hoac header |
| **Routing Key** | "Dia chi" cua message | "order.created", "user.deleted" |
| **Virtual Host** | Namespace cach ly | "/production", "/staging" |
| **Connection** | Ket noi TCP toi RabbitMQ | 1 connection per application |
| **Channel** | Kenh logic trong connection | Nhieu channel trong 1 connection |
| **Acknowledgment** | Consumer xac nhan da xu ly | ack / nack / reject |

#### 4 loai Exchange

```
1. DIRECT Exchange
   Routing key KHOP CHINH XAC voi binding key

   Producer --("order.created")--> [Direct Exchange]
                                        |
                          binding key = "order.created"
                                        |
                                        v
                                   [Order Queue] --> Consumer

2. TOPIC Exchange
   Routing key KHOP THEO PATTERN (*, #)

   * = dung 1 tu
   # = 0 hoac nhieu tu

   Producer --("order.created")--> [Topic Exchange]
                                        |
                          binding key = "order.*"     --> [Order Queue]
                          binding key = "order.#"     --> [All Order Queue]
                          binding key = "*.created"   --> [Created Events Queue]

3. FANOUT Exchange
   GUI TAT CA message vao MOI queue (broadcast)

   Producer --> [Fanout Exchange]
                     |
                     +--> [Queue A] --> Consumer 1
                     +--> [Queue B] --> Consumer 2
                     +--> [Queue C] --> Consumer 3

4. HEADERS Exchange
   Routing theo HEADER cua message (khong dung routing key)

   Producer --(headers: {type: "order", priority: "high"})--> [Headers Exchange]
                                                                    |
                                                  match: type=order --> [Order Queue]
                                                  match: priority=high --> [Priority Queue]
```

#### Thuc hanh

**Bai 1.1: Setup RabbitMQ bang Docker**

```yaml
# docker-compose.yml
version: '3.8'
services:
  rabbitmq:
    image: rabbitmq:3.13-management
    ports:
      - "5672:5672"     # AMQP port
      - "15672:15672"   # Management UI
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: admin
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq

volumes:
  rabbitmq_data:
```

```bash
docker-compose up -d

# Management UI: http://localhost:15672
# Login: admin / admin
```

**Bai 1.2: Spring Boot + RabbitMQ co ban**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: admin
```

```java
// === Cau hinh Queue + Exchange ===
@Configuration
public class RabbitMQConfig {

    // Queue
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable("order-queue")
            .build();
    }

    // Exchange
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange("order-exchange");
    }

    // Binding
    @Bean
    public Binding orderBinding(Queue orderQueue, DirectExchange orderExchange) {
        return BindingBuilder
            .bind(orderQueue)
            .to(orderExchange)
            .with("order.created");  // routing key
    }
}

// === Producer ===
@Service
@RequiredArgsConstructor
public class OrderProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendOrderCreated(OrderEvent event) {
        rabbitTemplate.convertAndSend(
            "order-exchange",       // exchange
            "order.created",        // routing key
            event                   // message
        );
        log.info("Sent order event: {}", event);
    }
}

// === Consumer ===
@Component
@Slf4j
public class OrderConsumer {

    @RabbitListener(queues = "order-queue")
    public void handleOrderCreated(OrderEvent event) {
        log.info("Received order event: {}", event);
        // Xu ly logic
    }
}

// === DTO ===
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderEvent implements Serializable {
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String status;
}
```

**Bai 1.3: Thu 4 loai Exchange**

```java
@Configuration
public class ExchangeExamplesConfig {

    // === 1. DIRECT Exchange ===
    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange("direct-exchange");
    }

    @Bean
    public Queue emailQueue() {
        return new Queue("email-queue");
    }

    @Bean
    public Queue smsQueue() {
        return new Queue("sms-queue");
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue())
            .to(directExchange()).with("notification.email");
    }

    @Bean
    public Binding smsBinding() {
        return BindingBuilder.bind(smsQueue())
            .to(directExchange()).with("notification.sms");
    }

    // === 2. TOPIC Exchange ===
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange("topic-exchange");
    }

    @Bean
    public Queue allOrderQueue() {
        return new Queue("all-order-queue");
    }

    @Bean
    public Queue createdEventsQueue() {
        return new Queue("created-events-queue");
    }

    @Bean
    public Binding allOrderBinding() {
        return BindingBuilder.bind(allOrderQueue())
            .to(topicExchange()).with("order.#");  // order.created, order.paid, order.shipped
    }

    @Bean
    public Binding createdBinding() {
        return BindingBuilder.bind(createdEventsQueue())
            .to(topicExchange()).with("*.created");  // order.created, user.created
    }

    // === 3. FANOUT Exchange ===
    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange("fanout-exchange");
    }

    @Bean
    public Queue analyticsQueue() {
        return new Queue("analytics-queue");
    }

    @Bean
    public Queue loggingQueue() {
        return new Queue("logging-queue");
    }

    @Bean
    public Binding analyticsBinding() {
        return BindingBuilder.bind(analyticsQueue())
            .to(fanoutExchange());  // Khong can routing key
    }

    @Bean
    public Binding loggingBinding() {
        return BindingBuilder.bind(loggingQueue())
            .to(fanoutExchange());
    }

    // === 4. HEADERS Exchange ===
    @Bean
    public HeadersExchange headersExchange() {
        return new HeadersExchange("headers-exchange");
    }

    @Bean
    public Queue priorityQueue() {
        return new Queue("priority-queue");
    }

    @Bean
    public Binding priorityBinding() {
        return BindingBuilder.bind(priorityQueue())
            .to(headersExchange())
            .whereAll(Map.of("priority", "high", "type", "order"))
            .match();
    }
}
```

### Bai tap tong hop Phase 1

```
Du an: Notification System

Yeu cau:
1. REST API nhan yeu cau gui thong bao (email, sms, push)
2. Producer gui message voi routing key tuong ung
3. 3 Consumer rieng biet: EmailConsumer, SmsConsumer, PushConsumer
4. Dung Topic Exchange voi pattern: notification.email, notification.sms, notification.push

Test:
- Gui notification.email -> chi EmailConsumer nhan
- Gui notification.* -> tat ca deu nhan (neu dung fanout)
- Xem tren Management UI: queues, messages, consumers

Nang cao:
- Thu dung Fanout Exchange de broadcast cho tat ca
- Thu dung Headers Exchange de routing theo priority
```

### Kiem tra kien thuc Phase 1

```
[ ] 4 loai Exchange khac nhau the nao
[ ] Routing key dung de lam gi
[ ] * va # trong Topic Exchange khac gi nhau
[ ] Queue durable vs non-durable
[ ] Connection vs Channel khac gi nhau
[ ] Tai sao dung nhieu Channel trong 1 Connection
[ ] RabbitMQ Management UI xem duoc nhung gi
```

---

## Phase 2: Trung cap (2-3 tuan)

### Muc tieu
- [ ] Acknowledgment va message durability
- [ ] Dead Letter Exchange (DLX)
- [ ] Retry mechanism
- [ ] Request/Reply pattern (RPC)
- [ ] Message TTL va Priority Queue

### Tuan 3: Reliability - Dam bao khong mat message

#### Message Lifecycle

```
Producer                    RabbitMQ                     Consumer
   |                           |                            |
   |-- publish (persistent) -->|                            |
   |                           |-- luu vao disk             |
   |<-- publisher confirm -----|                            |
   |                           |                            |
   |                           |-- push message ----------->|
   |                           |                            |-- xu ly
   |                           |<-- ack -------------------|
   |                           |-- xoa message khoi queue   |
   |                           |                            |
   |                           |   NEU consumer KHONG ack:  |
   |                           |-- requeue message          |
   |                           |-- gui lai cho consumer khac|
```

#### 3 tang dam bao khong mat message

```
Tang 1: PRODUCER  -> Publisher Confirms (RabbitMQ da nhan message)
Tang 2: RABBITMQ  -> Durable Queue + Persistent Message (luu disk)
Tang 3: CONSUMER  -> Manual Acknowledgment (xu ly xong moi ack)
```

#### Tang 1: Publisher Confirms

```java
@Configuration
public class ReliableProducerConfig {

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);

        // Bat publisher confirms
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.info("Message delivered successfully: {}",
                    correlationData != null ? correlationData.getId() : "unknown");
            } else {
                log.error("Message delivery failed: {}", cause);
                // Retry hoac luu vao DB de gui lai
            }
        });

        // Bat return callback (message khong route duoc vao queue nao)
        template.setReturnsCallback(returned -> {
            log.error("Message returned: exchange={}, routingKey={}, message={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                new String(returned.getMessage().getBody()));
        });

        template.setMandatory(true);  // Bat buoc phai route duoc
        return template;
    }
}

// application.yml
spring:
  rabbitmq:
    publisher-confirm-type: correlated  # Bat publisher confirms
    publisher-returns: true             # Bat returns
```

#### Tang 2: Durable Queue + Persistent Message

```java
@Configuration
public class DurableQueueConfig {

    @Bean
    public Queue durableQueue() {
        return QueueBuilder
            .durable("payment-queue")     // Queue ton tai sau khi restart
            .build();
    }
}

// Gui message persistent
@Service
public class ReliableProducer {

    public void send(String exchange, String routingKey, Object message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
            msg.getMessageProperties().setDeliveryMode(
                MessageDeliveryMode.PERSISTENT  // Luu vao disk
            );
            return msg;
        });
    }
}
```

#### Tang 3: Manual Acknowledgment

```java
// application.yml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual  # Tat auto-ack

// Consumer voi manual ack
@Component
@Slf4j
public class ReliableConsumer {

    @RabbitListener(queues = "payment-queue")
    public void handle(Message message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            String body = new String(message.getBody());
            log.info("Processing: {}", body);

            // Xu ly business logic
            processPayment(body);

            // ACK - xu ly thanh cong, xoa message
            channel.basicAck(deliveryTag, false);

        } catch (RecoverableException e) {
            // NACK + REQUEUE - loi tam thoi, gui lai queue
            log.warn("Temporary error, requeueing", e);
            channel.basicNack(deliveryTag, false, true);

        } catch (Exception e) {
            // REJECT - loi vinh vien, khong requeue
            // Message se vao Dead Letter Exchange (neu co cau hinh)
            log.error("Permanent error, rejecting", e);
            channel.basicReject(deliveryTag, false);
        }
    }
}
```

#### Prefetch Count

```java
// Gioi han so message gui cho consumer cung luc
// Tranh 1 consumer bi qua tai trong khi consumer khac ranh

spring:
  rabbitmq:
    listener:
      simple:
        prefetch: 10  # Moi consumer chi nhan toi da 10 message chua ack

// Giai thich:
// prefetch = 1:  Cham nhung cong bang (round-robin deu)
// prefetch = 10: Nhanh hon nhung co the khong deu
// prefetch = 250: Default, throughput cao nhung co the mat can bang
```

#### Bai tap

```
Bai 2.1: Implement 3 tang reliability
         -> Kill consumer giua chung xu ly -> message co requeue khong?
         -> Restart RabbitMQ -> message durable co mat khong?
         -> Gui message sai routing key -> publisher return co bat duoc khong?

Bai 2.2: Thu prefetch = 1 vs 10 vs 100
         -> Gui 1000 messages, 2 consumers (1 nhanh, 1 cham)
         -> Observe phan bo message
```

### Tuan 4: Dead Letter Exchange (DLX) + Retry

#### DLX la gi

```
Message bi reject/expire/max-length
       |
       v
[Dead Letter Exchange] --> [Dead Letter Queue]
       |
       v
  DLQ Consumer (log, alert, retry)

3 truong hop message vao DLX:
1. Consumer REJECT (basicReject/basicNack, requeue=false)
2. Message TTL het han
3. Queue dat max-length
```

#### Cau hinh DLX

```java
@Configuration
public class DlxConfig {

    // Dead Letter Exchange
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("dlx-exchange");
    }

    // Dead Letter Queue
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("dead-letter-queue").build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
            .to(deadLetterExchange()).with("dead-letter");
    }

    // Main Queue - tro DLX khi message bi reject
    @Bean
    public Queue mainQueue() {
        return QueueBuilder.durable("main-queue")
            .deadLetterExchange("dlx-exchange")         // Tro den DLX
            .deadLetterRoutingKey("dead-letter")         // Routing key cho DLX
            .build();
    }

    // DLQ Consumer
    @Component
    @Slf4j
    public class DlqConsumer {

        @RabbitListener(queues = "dead-letter-queue")
        public void handleDeadLetter(Message message) {
            log.error("Dead letter received: {}",
                new String(message.getBody()));
            log.error("Original exchange: {}",
                message.getMessageProperties()
                    .getHeader("x-first-death-exchange"));
            log.error("Death reason: {}",
                message.getMessageProperties()
                    .getHeader("x-first-death-reason"));

            // Luu DB, gui alert, hoac schedule retry
        }
    }
}
```

#### Retry voi Delay (Retry Queue Pattern)

```
Main Queue --> Consumer (FAIL)
                  |
                  v (reject)
            [DLX Exchange]
                  |
                  v
            [Retry Queue] (TTL = 5s)
                  |
                  v (expire)
            [Main Exchange]
                  |
                  v
            [Main Queue] --> Consumer (retry)
```

```java
@Configuration
public class RetryConfig {

    // === Main ===
    @Bean
    public DirectExchange mainExchange() {
        return new DirectExchange("main-exchange");
    }

    @Bean
    public Queue mainQueue() {
        return QueueBuilder.durable("main-queue")
            .deadLetterExchange("retry-exchange")
            .deadLetterRoutingKey("retry")
            .build();
    }

    @Bean
    public Binding mainBinding() {
        return BindingBuilder.bind(mainQueue())
            .to(mainExchange()).with("main");
    }

    // === Retry (delay queue) ===
    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange("retry-exchange");
    }

    @Bean
    public Queue retryQueue() {
        return QueueBuilder.durable("retry-queue")
            .deadLetterExchange("main-exchange")   // Khi TTL het -> quay ve main
            .deadLetterRoutingKey("main")
            .ttl(5000)                              // Doi 5 giay truoc khi retry
            .build();
    }

    @Bean
    public Binding retryBinding() {
        return BindingBuilder.bind(retryQueue())
            .to(retryExchange()).with("retry");
    }

    // === DLQ (sau khi retry het) ===
    @Bean
    public Queue finalDlq() {
        return QueueBuilder.durable("final-dlq").build();
    }
}

// Consumer voi retry count
@Component
@Slf4j
public class RetryConsumer {

    private static final int MAX_RETRIES = 3;

    @RabbitListener(queues = "main-queue")
    public void handle(Message message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        int retryCount = getRetryCount(message);

        try {
            processMessage(message);
            channel.basicAck(tag, false);

        } catch (Exception e) {
            if (retryCount < MAX_RETRIES) {
                log.warn("Retry {}/{} for message", retryCount + 1, MAX_RETRIES);
                // Reject -> DLX -> Retry Queue -> doi 5s -> Main Queue
                channel.basicReject(tag, false);
            } else {
                log.error("Max retries reached, sending to final DLQ");
                // Gui thang vao final DLQ
                rabbitTemplate.convertAndSend("", "final-dlq", message);
                channel.basicAck(tag, false);
            }
        }
    }

    private int getRetryCount(Message message) {
        List<Map<String, ?>> deaths = message.getMessageProperties()
            .getHeader("x-death");
        if (deaths == null || deaths.isEmpty()) return 0;
        return ((Long) deaths.get(0).get("count")).intValue();
    }
}
```

#### Spring Retry (don gian hon)

```java
// application.yml
spring:
  rabbitmq:
    listener:
      simple:
        retry:
          enabled: true
          initial-interval: 1000     # 1s
          multiplier: 2.0            # 1s -> 2s -> 4s
          max-attempts: 3
          max-interval: 10000        # Toi da 10s

// Consumer - Spring tu dong retry, khong can code thu cong
@RabbitListener(queues = "order-queue")
public void handle(OrderEvent event) {
    // Neu throw exception -> Spring retry 3 lan
    // Sau 3 lan fail -> message bi discard (hoac vao DLX neu co cau hinh)
    processOrder(event);
}
```

#### Bai tap

```
Bai 2.3: Implement DLX
         -> Reject message -> xem co vao DLQ khong
         -> Xem x-death headers trong DLQ message

Bai 2.4: Implement Retry Queue voi delay 5s
         -> Consumer fail -> doi 5s -> retry
         -> Fail 3 lan -> vao final DLQ

Bai 2.5: So sanh Spring Retry vs Retry Queue pattern
         -> Uu nhuoc diem cua tung cach
```

### Tuan 5: RPC Pattern + TTL + Priority

#### Request/Reply (RPC) Pattern

```
Client                       RabbitMQ                    Server
  |                             |                           |
  |-- request (replyTo=xxx) --->|                           |
  |                             |-- push to rpc-queue ----->|
  |                             |                           |-- process
  |                             |<-- reply to xxx ----------|
  |<-- receive reply -----------|                           |
```

```java
// === Server ===
@RabbitListener(queues = "rpc-queue")
public String handleRpcRequest(String request) {
    log.info("RPC request: {}", request);
    // Xu ly va tra ket qua
    int result = fibonacci(Integer.parseInt(request));
    return String.valueOf(result);  // Tu dong gui vao replyTo queue
}

// === Client ===
@Service
@RequiredArgsConstructor
public class RpcClient {

    private final RabbitTemplate rabbitTemplate;

    public String callFibonacci(int n) {
        // Gui request va doi response (blocking)
        Object response = rabbitTemplate.convertSendAndReceive(
            "rpc-exchange",
            "rpc",
            String.valueOf(n)
        );
        return response != null ? response.toString() : null;
    }
}

// === Async RPC voi CompletableFuture ===
@Service
public class AsyncRpcClient {

    private final AsyncRabbitTemplate asyncTemplate;

    public CompletableFuture<String> callFibonacciAsync(int n) {
        return asyncTemplate
            .convertSendAndReceive("rpc-exchange", "rpc", String.valueOf(n))
            .thenApply(Object::toString);
    }
}
```

#### Message TTL (Time-To-Live)

```java
// TTL tren Queue (ap dung cho MOI message trong queue)
@Bean
public Queue ttlQueue() {
    return QueueBuilder.durable("ttl-queue")
        .ttl(60000)  // 60 giay, sau do message bi xoa (hoac vao DLX)
        .deadLetterExchange("dlx-exchange")
        .build();
}

// TTL tren tung Message
public void sendWithTtl(String message, int ttlMs) {
    rabbitTemplate.convertAndSend("exchange", "key", message, msg -> {
        msg.getMessageProperties().setExpiration(String.valueOf(ttlMs));
        return msg;
    });
}

// Use case: Delayed notification
// Gui message TTL=30min -> het han -> DLX -> Consumer gui notification
```

#### Priority Queue

```java
// Tao queue voi priority
@Bean
public Queue priorityQueue() {
    return QueueBuilder.durable("priority-queue")
        .maxPriority(10)  // Priority tu 0 (thap) den 10 (cao)
        .build();
}

// Gui message voi priority
public void sendWithPriority(String message, int priority) {
    rabbitTemplate.convertAndSend("exchange", "key", message, msg -> {
        msg.getMessageProperties().setPriority(priority);
        return msg;
    });
}

// Message voi priority cao duoc xu ly truoc
// Luu y: Chi hieu qua khi queue co nhieu message dang cho
```

#### Bai tap

```
Bai 2.6: Implement RPC pattern
         -> Client gui so, Server tra fibonacci
         -> Thu async RPC

Bai 2.7: Delayed notification voi TTL + DLX
         -> Gui message voi TTL=10s
         -> Sau 10s -> DLX -> consumer gui notification

Bai 2.8: Priority Queue
         -> Gui 100 messages voi random priority
         -> Xac nhan consumer nhan message priority cao truoc
```

### Bai tap tong hop Phase 2

```
Du an: Task Processing System

Yeu cau:
1. REST API submit task (voi priority)
2. Worker pool (3 consumers) xu ly task
3. Failed task -> retry 3 lan voi delay
4. Sau 3 lan fail -> DLQ + alert
5. Task co TTL (het han thi huy)
6. Dashboard: xem pending/processing/failed tasks

Kiem tra:
- [ ] Task priority cao xu ly truoc
- [ ] Worker crash -> task requeue
- [ ] Retry 3 lan roi vao DLQ
- [ ] Task het TTL -> tu dong huy
```

### Kiem tra kien thuc Phase 2

```
[ ] 3 tang dam bao khong mat message la gi
[ ] Ack vs Nack vs Reject khac nhau the nao
[ ] Prefetch count anh huong gi
[ ] Dead Letter Exchange hoat dong nhu the nao
[ ] 3 truong hop message vao DLX
[ ] Retry Queue pattern hoat dong nhu the nao
[ ] RPC pattern trong RabbitMQ
[ ] TTL dung tren Queue vs tren Message khac gi
[ ] Khi nao dung Priority Queue
```

---

## Phase 3: Nang cao (3-4 tuan)

### Muc tieu
- [ ] Clustering va High Availability
- [ ] Quorum Queues (thay the mirrored queues)
- [ ] Shovel va Federation
- [ ] Design patterns nang cao
- [ ] Spring Cloud Stream

### Tuan 6-7: Clustering va HA

#### RabbitMQ Cluster

```
                    +---> [Node 1] (queue master)
                    |
Client ----------->|---> [Node 2] (queue mirror)
   (load balancer)  |
                    +---> [Node 3] (queue mirror)

- Exchanges va Bindings: replicate tu dong tren tat ca nodes
- Queues: MAC DINH chi ton tai tren 1 node
  -> Can cau hinh Quorum Queue hoac Mirrored Queue de HA
```

#### Docker Compose Cluster

```yaml
version: '3.8'
services:
  rabbitmq-1:
    image: rabbitmq:3.13-management
    hostname: rabbit1
    environment:
      RABBITMQ_ERLANG_COOKIE: "secret_cookie"
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: admin
    ports:
      - "5672:5672"
      - "15672:15672"

  rabbitmq-2:
    image: rabbitmq:3.13-management
    hostname: rabbit2
    environment:
      RABBITMQ_ERLANG_COOKIE: "secret_cookie"
    depends_on:
      - rabbitmq-1

  rabbitmq-3:
    image: rabbitmq:3.13-management
    hostname: rabbit3
    environment:
      RABBITMQ_ERLANG_COOKIE: "secret_cookie"
    depends_on:
      - rabbitmq-1
```

```bash
# Join cluster
docker exec rabbitmq-2 rabbitmqctl stop_app
docker exec rabbitmq-2 rabbitmqctl join_cluster rabbit@rabbit1
docker exec rabbitmq-2 rabbitmqctl start_app

docker exec rabbitmq-3 rabbitmqctl stop_app
docker exec rabbitmq-3 rabbitmqctl join_cluster rabbit@rabbit1
docker exec rabbitmq-3 rabbitmqctl start_app

# Kiem tra cluster status
docker exec rabbitmq-1 rabbitmqctl cluster_status
```

#### Quorum Queues (khuyen dung tu RabbitMQ 3.8+)

```java
// Quorum Queue = queue replicate tren nhieu nodes
// Thay the Classic Mirrored Queues (deprecated)

@Bean
public Queue quorumQueue() {
    return QueueBuilder.durable("orders-quorum")
        .quorum()                           // Bat quorum mode
        .deliveryLimit(3)                   // Max redelivery (nhu retry)
        .deadLetterExchange("dlx-exchange") // DLX khi vuot delivery limit
        .build();
}

// Quorum Queue vs Classic Queue
// +------------------+------------------+-------------------+
// |                  | Classic Queue    | Quorum Queue      |
// +------------------+------------------+-------------------+
// | Data safety      | Co the mat data  | Khong mat data    |
// | Replication      | Async (mirror)   | Raft consensus    |
// | Performance      | Nhanh hon        | Cham hon 1 chut   |
// | Memory           | It hon           | Nhieu hon         |
// | Non-durable      | Ho tro           | Khong             |
// | Priority         | Ho tro           | Khong             |
// | Lazy mode        | Ho tro           | Mac dinh lazy     |
// +------------------+------------------+-------------------+
//
// Ket luan: Dung Quorum Queue cho production, Classic cho dev/test
```

#### Bai tap

```
Bai 3.1: Setup 3-node cluster
         -> Gui message, kill 1 node -> message co mat khong?

Bai 3.2: So sanh Classic Queue vs Quorum Queue
         -> Kill node chua queue master
         -> Classic: mat message
         -> Quorum: khong mat

Bai 3.3: Network partition simulation
         -> Chia cluster thanh 2 nhom -> observe behavior
```

### Tuan 8: Design Patterns

#### Pattern 1: Work Queue (Competing Consumers)

```
                              +---> Consumer 1 (xu ly msg 1, 3, 5...)
Producer --> [Queue] ---------+
                              +---> Consumer 2 (xu ly msg 2, 4, 6...)

Use case: Phan bo task cho nhieu workers
Luu y: Set prefetch=1 de phan bo deu
```

#### Pattern 2: Pub/Sub (Fanout)

```
                              +---> [Queue A] ---> Consumer 1 (email)
Producer --> [Fanout Exchange]-+---> [Queue B] ---> Consumer 2 (sms)
                              +---> [Queue C] ---> Consumer 3 (push)

Use case: Broadcast event cho nhieu service
```

#### Pattern 3: Routing (Direct)

```
Producer --(severity: error)--> [Direct Exchange]
                                      |
                    routing key = "error" --> [Error Queue] --> Alert Service
                    routing key = "info"  --> [Info Queue]  --> Log Service
                    routing key = "debug" --> [Debug Queue] --> Dev Tool

Use case: Log routing theo severity
```

#### Pattern 4: Saga Pattern voi RabbitMQ

```java
// Choreography Saga - tuong tu Kafka nhung don gian hon

// === Order Service ===
@Service
public class OrderSagaService {

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request));

        // Gui event bat dau saga
        rabbitTemplate.convertAndSend(
            "saga-exchange", "order.created",
            new OrderCreatedEvent(order)
        );
        return order;
    }

    // Lang nghe ket qua tu cac service khac
    @RabbitListener(queues = "order-result-queue")
    public void handleResult(SagaResultEvent event) {
        if (event.isSuccess()) {
            orderRepository.updateStatus(event.getOrderId(), CONFIRMED);
        } else {
            orderRepository.updateStatus(event.getOrderId(), CANCELLED);
        }
    }
}

// === Payment Service ===
@Component
public class PaymentSagaConsumer {

    @RabbitListener(queues = "payment-saga-queue")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            paymentService.charge(event.getUserId(), event.getAmount());

            rabbitTemplate.convertAndSend(
                "saga-exchange", "payment.completed",
                new PaymentCompletedEvent(event.getOrderId())
            );
        } catch (Exception e) {
            rabbitTemplate.convertAndSend(
                "saga-exchange", "payment.failed",
                new PaymentFailedEvent(event.getOrderId(), e.getMessage())
            );
        }
    }

    // Compensating action
    @RabbitListener(queues = "payment-compensate-queue")
    public void compensate(CompensatePaymentEvent event) {
        paymentService.refund(event.getOrderId());
    }
}
```

#### Pattern 5: Delayed Message (Plugin)

```bash
# Cai plugin delayed message
rabbitmq-plugins enable rabbitmq_delayed_message_exchange
```

```java
// Delayed Exchange - gui message sau 1 khoang thoi gian
@Bean
public CustomExchange delayedExchange() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-delayed-type", "direct");
    return new CustomExchange("delayed-exchange",
        "x-delayed-message", true, false, args);
}

// Gui message delay 30 giay
public void sendDelayed(String message, int delayMs) {
    rabbitTemplate.convertAndSend("delayed-exchange", "key", message, msg -> {
        msg.getMessageProperties().setDelay(delayMs);  // 30000 = 30s
        return msg;
    });
}

// Use case:
// - Gui email nhac nho sau 24h
// - Tu dong huy order sau 30 phut neu chua thanh toan
// - Scheduled notifications
```

#### Bai tap

```
Bai 3.4: Implement Saga Pattern (Order -> Payment -> Inventory)
         -> Happy path + compensation khi fail

Bai 3.5: Delayed Message
         -> Tu dong huy order sau 5 phut neu chua thanh toan
         -> Gui reminder email sau 1 phut

Bai 3.6: Competing Consumers
         -> 5 workers xu ly image processing
         -> Observe phan bo task voi prefetch=1 vs prefetch=10
```

### Tuan 9: Spring Cloud Stream (Optional)

#### Spring Cloud Stream la gi

```
Code cua ban --> [Spring Cloud Stream] --> RabbitMQ / Kafka / ...
                                              ^
                                    Chi can doi cau hinh, khong doi code

Loi ich: Doi tu RabbitMQ sang Kafka chi can doi application.yml
```

```java
// === Functional style (khuyen dung) ===

@Configuration
public class StreamConfig {

    // Consumer: nhan message tu input channel
    @Bean
    public Consumer<OrderEvent> processOrder() {
        return event -> {
            log.info("Processing order: {}", event);
            // Business logic
        };
    }

    // Supplier: gui message dinh ky
    @Bean
    public Supplier<OrderEvent> generateOrder() {
        return () -> new OrderEvent(
            System.currentTimeMillis(), 1L, BigDecimal.TEN, "CREATED"
        );
    }

    // Function: nhan message, xu ly, gui tiep
    @Bean
    public Function<OrderEvent, PaymentEvent> orderToPayment() {
        return order -> new PaymentEvent(
            order.getOrderId(),
            order.getAmount()
        );
    }
}
```

```yaml
# application.yml
spring:
  cloud:
    stream:
      bindings:
        processOrder-in-0:
          destination: order-events
          group: payment-service
        generateOrder-out-0:
          destination: order-events
        orderToPayment-in-0:
          destination: order-events
        orderToPayment-out-0:
          destination: payment-events
      rabbit:
        bindings:
          processOrder-in-0:
            consumer:
              auto-bind-dlq: true
              max-attempts: 3
```

#### Bai tap

```
Bai 3.7: Chuyen du an Phase 2 sang Spring Cloud Stream
Bai 3.8: Implement Function pipeline:
         Order -> Payment -> Notification
```

### Kiem tra kien thuc Phase 3

```
[ ] RabbitMQ cluster hoat dong nhu the nao
[ ] Quorum Queue khac Classic Queue the nao
[ ] Erlang Cookie dung de lam gi
[ ] Network partition xu ly nhu the nao
[ ] Saga Pattern voi RabbitMQ
[ ] Delayed Message plugin
[ ] Spring Cloud Stream loi ich gi
[ ] Shovel vs Federation khac nhau the nao
```

---

## Phase 4: Production-ready (2-3 tuan)

### Muc tieu
- [ ] Monitoring va alerting
- [ ] Performance tuning
- [ ] Security
- [ ] Troubleshooting
- [ ] Backup va Recovery

### Tuan 10: Monitoring

#### Built-in Management UI

```
http://localhost:15672

Xem duoc:
- Overview: message rates, connections, channels
- Queues: depth, consumer count, message rate
- Exchanges: message rate per exchange
- Connections: client connections
- Channels: channel usage
- Admin: users, vhosts, policies
```

#### Prometheus + Grafana

```yaml
# Bat Prometheus plugin (tu RabbitMQ 3.8+)
# rabbitmq.conf
prometheus.return_per_object_metrics = true

# Docker
services:
  rabbitmq:
    image: rabbitmq:3.13-management
    command: >
      bash -c "rabbitmq-plugins enable rabbitmq_prometheus &&
               rabbitmq-server"
    ports:
      - "15692:15692"  # Prometheus metrics endpoint

  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
```

```yaml
# prometheus.yml
scrape_configs:
  - job_name: rabbitmq
    static_configs:
      - targets: ['rabbitmq:15692']
    metrics_path: /metrics
```

#### Metrics quan trong

```
=== QUEUE METRICS ===
rabbitmq_queue_messages                    # Tong message trong queue
rabbitmq_queue_messages_ready              # Message san sang de deliver
rabbitmq_queue_messages_unacked            # Message da deliver nhung chua ack
rabbitmq_queue_consumers                   # So consumer

=== CONNECTION METRICS ===
rabbitmq_connections                       # Tong connections
rabbitmq_channels                          # Tong channels

=== NODE METRICS ===
rabbitmq_process_resident_memory_bytes     # Memory usage
rabbitmq_disk_space_available_bytes        # Disk space
rabbitmq_fd_used                           # File descriptors used

=== Alerts nen co ===
- Queue depth > 10000 (consumer cham)
- Unacked messages > 1000 (consumer bi treo)
- Memory > 80% (can scale hoac kiem tra memory leak)
- Disk space < 20% (can cleanup hoac them disk)
- Consumer count = 0 (khong ai xu ly)
- Connection spike (co the bi attack hoac connection leak)
```

#### Health Check

```java
// Spring Boot Actuator + RabbitMQ Health
// pom.xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

// application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  health:
    rabbit:
      enabled: true

// GET /actuator/health
// {
//   "status": "UP",
//   "components": {
//     "rabbit": {
//       "status": "UP",
//       "details": {
//         "version": "3.13.0"
//       }
//     }
//   }
// }
```

#### Bai tap

```
Bai 4.1: Setup Prometheus + Grafana cho RabbitMQ
         -> Import Grafana dashboard (ID: 10991)

Bai 4.2: Tao alert khi queue depth > 100
         -> Stop consumer, gui 200 messages -> alert fire

Bai 4.3: Monitor connection va channel count
         -> Tao connection leak -> observe metrics
```

### Tuan 11: Performance Tuning

#### Producer Tuning

```java
// === Batch Publishing ===
// Gui nhieu message trong 1 channel operation
@Service
public class BatchProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendBatch(List<OrderEvent> events) {
        rabbitTemplate.invoke(operations -> {
            for (OrderEvent event : events) {
                operations.convertAndSend("exchange", "key", event);
            }
            // Doi tat ca confirms
            operations.waitForConfirmsOrDie(5000);
            return null;
        });
    }
}

// === Connection va Channel Pool ===
spring:
  rabbitmq:
    cache:
      connection:
        mode: CONNECTION
        size: 5           # So connection trong pool
      channel:
        size: 25          # So channel trong pool
        checkout-timeout: 1000  # Timeout khi het channel

// === Concurrency ===
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 5      # Min consumers
        max-concurrency: 20  # Max consumers (auto scale)
        prefetch: 10
```

#### Queue Tuning

```java
// === Lazy Queue (tiet kiem memory) ===
@Bean
public Queue lazyQueue() {
    return QueueBuilder.durable("lazy-queue")
        .lazy()  // Luu message vao disk thay vi memory
        .build();
    // Tot cho queue co nhieu message nhung consumer cham
}

// === Max Length ===
@Bean
public Queue boundedQueue() {
    return QueueBuilder.durable("bounded-queue")
        .maxLength(100000)                          // Toi da 100k messages
        .overflow(QueueBuilder.Overflow.rejectPublish)  // Reject khi day
        // Hoac: .overflow(QueueBuilder.Overflow.dropHead)  // Xoa message cu nhat
        .build();
}

// === Single Active Consumer ===
// Chi 1 consumer xu ly tai 1 thoi diem (dam bao thu tu)
@Bean
public Queue singleActiveQueue() {
    return QueueBuilder.durable("ordered-queue")
        .singleActiveConsumer()
        .build();
}
```

#### Benchmark

```java
@Component
public class RabbitBenchmark {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void benchmarkProducer(int messageCount) {
        String payload = "x".repeat(1024);  // 1KB message
        long start = System.currentTimeMillis();

        for (int i = 0; i < messageCount; i++) {
            rabbitTemplate.convertAndSend("bench-exchange", "bench", payload);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Sent {} messages in {}ms = {} msg/s",
            messageCount, elapsed, messageCount * 1000L / elapsed);
    }
}

// Ket qua tham khao (single node, persistent, 1KB messages):
// Without confirms:  ~20,000 msg/s
// With confirms:     ~5,000-10,000 msg/s
// Batch confirms:    ~15,000 msg/s
```

#### Bai tap

```
Bai 4.4: Benchmark producer: voi/khong publisher confirms
Bai 4.5: Benchmark consumer: prefetch 1 vs 10 vs 100
Bai 4.6: Test Lazy Queue vs Normal Queue voi 1M messages
         -> So sanh memory usage
Bai 4.7: Auto-scaling consumers (concurrency 1-10)
         -> Gui burst 10k messages -> observe scale up
```

### Tuan 12: Security + Backup + Troubleshooting

#### Security

```bash
# === User Management ===
rabbitmqctl add_user app_user strong_password
rabbitmqctl set_user_tags app_user monitoring  # Tags: administrator, monitoring, management
rabbitmqctl set_permissions -p /production app_user "^order-.*" "^order-.*" "^order-.*"
#                                                    ^configure   ^write      ^read
# Regex: user chi truy cap queues/exchanges bat dau bang "order-"

# === Virtual Hosts (namespace isolation) ===
rabbitmqctl add_vhost /production
rabbitmqctl add_vhost /staging

# === TLS ===
# rabbitmq.conf
listeners.ssl.default = 5671
ssl_options.cacertfile = /path/to/ca_certificate.pem
ssl_options.certfile   = /path/to/server_certificate.pem
ssl_options.keyfile    = /path/to/server_key.pem
ssl_options.verify     = verify_peer
ssl_options.fail_if_no_peer_cert = true
```

```yaml
# Spring Boot TLS config
spring:
  rabbitmq:
    ssl:
      enabled: true
      key-store: classpath:client-keystore.p12
      key-store-password: changeit
      trust-store: classpath:truststore.jks
      trust-store-password: changeit
```

#### Backup va Recovery

```bash
# === Export Definitions (exchanges, queues, bindings, users, vhosts) ===
rabbitmqctl export_definitions /backup/definitions.json

# === Import Definitions ===
rabbitmqctl import_definitions /backup/definitions.json

# === Luu y: Definitions KHONG bao gom messages trong queue ===
# Messages nam trong Mnesia database:
#   /var/lib/rabbitmq/mnesia/

# Backup strategy:
# 1. Definitions: export hang ngay (cron job)
# 2. Messages: dung Quorum Queue (replicate tu dong)
# 3. Full backup: snapshot Mnesia dir (khi can)
```

#### Troubleshooting Guide

```bash
# === Common Issues ===

# 1. Queue bi full, memory cao
rabbitmqctl list_queues name messages consumers memory
# Fix: Tang consumers, set max-length, dung lazy queue

# 2. Connection bi dong dot ngot
rabbitmqctl list_connections name state timeout
# Fix: Tang heartbeat timeout, kiem tra network

# 3. Consumer khong nhan message
rabbitmqctl list_consumers
rabbitmqctl list_bindings
# Fix: Kiem tra binding, routing key, exchange type

# 4. Cluster node bi tach
rabbitmqctl cluster_status
rabbitmqctl forget_cluster_node rabbit@dead-node  # Xoa node chet
# Fix: Kiem tra network, Erlang cookie, DNS

# 5. Alarms (memory hoac disk)
rabbitmqctl status | grep alarms
# memory alarm: RabbitMQ STOP nhan message moi
# disk alarm: tuong tu
# Fix: Tang memory/disk hoac purge queue khong can

# === Useful Commands ===
rabbitmqctl list_queues name messages consumers memory state
rabbitmqctl list_exchanges name type
rabbitmqctl list_bindings source_name destination_name routing_key
rabbitmqctl list_connections name state channels
rabbitmqctl list_channels name consumer_count messages_unacknowledged
rabbitmqctl environment  # Xem cau hinh hien tai
rabbitmqctl report       # Full report de debug
```

#### Bai tap

```
Bai 4.8: Setup user permissions
         -> User A chi gui duoc, User B chi doc duoc
         -> Test: A doc -> denied, B gui -> denied

Bai 4.9: Export/Import definitions
         -> Xoa queue -> import lai -> queue xuat hien lai

Bai 4.10: Troubleshooting lab
          -> Kill 1 cluster node -> fix
          -> Tao memory alarm -> observe behavior
          -> Consumer khong nhan message -> debug binding
```

### Kiem tra kien thuc Phase 4

```
[ ] RabbitMQ metrics nao quan trong nhat
[ ] Lam sao biet consumer bi cham (lag)
[ ] Prefetch anh huong performance nhu the nao
[ ] Lazy Queue khi nao nen dung
[ ] RabbitMQ permissions model (configure, write, read)
[ ] Virtual Host dung de lam gi
[ ] Backup nhung gi, khong backup duoc gi
[ ] Memory alarm xay ra thi sao
[ ] Troubleshoot consumer khong nhan message nhu the nao
```

---

## So sanh RabbitMQ vs Kafka - Khi nao dung cai nao

```
+-----------------------------+-------------------+-------------------+
|         Tieu chi            |    RabbitMQ        |      Kafka        |
+-----------------------------+-------------------+-------------------+
| Do kho hoc                  | De (1-2 thang)     | Kho (3-6 thang)   |
| Setup                       | 1 container        | Nhieu components  |
| Routing linh hoat           | Rat tot (4 loai)   | Don gian (topic)  |
| Throughput                  | 10k-50k msg/s      | 100k-1M+ msg/s   |
| Message replay              | Khong              | Co                |
| Message ordering            | Kho dam bao        | Theo partition    |
| Consumer model              | Push (smart broker) | Pull (smart consumer) |
| Message TTL                 | Co (built-in)      | Retention-based   |
| Priority Queue              | Co                 | Khong             |
| Request/Reply (RPC)         | Built-in support   | Phuc tap          |
| Delayed Message             | Plugin             | Khong built-in    |
| Stream Processing           | Khong (co Streams) | Kafka Streams     |
| Protocol                    | AMQP, MQTT, STOMP  | Custom protocol   |
+-----------------------------+-------------------+-------------------+

Chon RabbitMQ:
  - Task queue / Background jobs
  - Request/Reply (RPC)
  - Complex routing
  - Priority processing
  - IoT (MQTT support)
  - Team nho, project vua

Chon Kafka:
  - Event streaming / Event sourcing
  - High throughput (>100k msg/s)
  - Data pipeline / ETL
  - Message replay
  - Stream processing
  - Microservices event-driven lon

Chon CA HAI:
  - RabbitMQ cho task queue + RPC
  - Kafka cho event streaming + analytics
  -> Nhieu company lon dung ca 2
```

---

## Tai lieu tham khao

### Sach

| Sach | Muc do | Ghi chu |
|------|--------|---------|
| RabbitMQ in Action | Beginner | Sach nhap mon tot nhat |
| RabbitMQ in Depth | Intermediate | Chi tiet hon, co patterns |
| Enterprise Integration Patterns | Advanced | Messaging patterns tong quat |

### Khoa hoc online

```
1. RabbitMQ Official Tutorials (mien phi)
   https://www.rabbitmq.com/getstarted.html
   -> 6 tutorials co ban, BAT BUOC lam het

2. CloudAMQP Blog (mien phi)
   https://www.cloudamqp.com/blog/
   -> Nhieu bai thuc te, best practices

3. Udemy - RabbitMQ & Java (Stephane Maarek)
   -> Day du va thuc hanh
```

### Tools nen biet

```
1. Management UI        - Built-in web UI (port 15672)
2. rabbitmqctl          - CLI admin tool
3. rabbitmq-diagnostics - Diagnostic tool
4. PerfTest             - Benchmarking tool chinh thuc
5. Testcontainers       - RabbitMQ trong unit test
6. rabbitmqadmin        - CLI cho HTTP API
```

---

## Meo hoc hieu qua

### 1. Bat dau tu RabbitMQ Tutorials

```
Lam 6 tutorials chinh thuc theo thu tu:
1. Hello World       (Producer/Consumer co ban)
2. Work Queues       (Competing consumers)
3. Publish/Subscribe (Fanout exchange)
4. Routing           (Direct exchange)
5. Topics            (Topic exchange)
6. RPC               (Request/Reply)

-> Lam bang Java, KHONG copy paste, tu go tay
```

### 2. So sanh truc quan

```
Moi khi hoc khai niem moi, tu hoi:
- Kafka lam dieu nay nhu the nao?
- SQL database lam dieu nay nhu the nao?
- Neu khong co RabbitMQ, minh lam dieu nay bang cach nao?

-> Hieu WHY quan trong hon hieu HOW
```

### 3. Pha va sua

```
- Gui message sai routing key -> message di dau?
- Kill consumer giua chung xu ly -> message co mat?
- Day queue cho den khi memory alarm -> chuyen gi xay ra?
- Network partition trong cluster -> nodes xu ly the nao?

-> Hieu failure modes = hieu RabbitMQ that su
```

### 4. Side projects

```
Phase 1: Notification system (email, sms, push)
Phase 2: Task processing voi retry va DLQ
Phase 3: Microservices e-commerce (Saga pattern)
Phase 4: Multi-tenant SaaS voi vhosts + monitoring
```

---

## Tracking tien do

```
Ngay bat dau: _______________

Phase 1: Nen tang (1-2 tuan)
  Bat dau: ___________  Hoan thanh: ___________
  [ ] Setup RabbitMQ local
  [ ] Hieu 4 loai Exchange
  [ ] Spring Boot Producer/Consumer
  [ ] Notification system project
  [ ] Kiem tra kien thuc

Phase 2: Trung cap (2-3 tuan)
  Bat dau: ___________  Hoan thanh: ___________
  [ ] 3 tang reliability
  [ ] Dead Letter Exchange
  [ ] Retry mechanism
  [ ] RPC pattern
  [ ] TTL + Priority Queue
  [ ] Task processing project
  [ ] Kiem tra kien thuc

Phase 3: Nang cao (3-4 tuan)
  Bat dau: ___________  Hoan thanh: ___________
  [ ] Clustering + Quorum Queue
  [ ] Saga Pattern
  [ ] Delayed Message
  [ ] Spring Cloud Stream
  [ ] E-commerce project
  [ ] Kiem tra kien thuc

Phase 4: Production-ready (2-3 tuan)
  Bat dau: ___________  Hoan thanh: ___________
  [ ] Monitoring (Prometheus + Grafana)
  [ ] Performance tuning + Benchmark
  [ ] Security (users, vhosts, TLS)
  [ ] Backup + Recovery
  [ ] Troubleshooting lab
  [ ] Kiem tra kien thuc
```
