package com.voda.demo.service;

import com.voda.demo.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendOrderCreated(OrderEvent event) {
        rabbitTemplate.convertAndSend(
            "order-exchange",
            "order.created",
            event
        );
        log.info("Sent order event: {}", event);
    }
}
