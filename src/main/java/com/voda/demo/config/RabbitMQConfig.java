package com.voda.demo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
