package com.voda.demo.service;

import com.voda.demo.dto.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderConsumer {

    @RabbitListener(queues = "order-queue")
    public void handleOrderCreated(OrderEvent event) {
        log.info("Received order event: {}", event);
    }
}
