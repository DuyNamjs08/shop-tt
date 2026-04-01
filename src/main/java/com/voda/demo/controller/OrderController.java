package com.voda.demo.controller;

import com.voda.demo.dto.OrderEvent;
import com.voda.demo.service.OrderProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order", description = "Gửi order event qua RabbitMQ")
public class OrderController {

    private final OrderProducer orderProducer;

    @Operation(summary = "Gửi order event", description = "Gửi order event vào RabbitMQ queue")
    @PostMapping("/send")
    public ResponseEntity<String> sendOrder(@RequestBody OrderEvent event) {
        orderProducer.sendOrderCreated(event);
        return ResponseEntity.ok("Order event sent: " + event.getOrderId());
    }

    @Operation(summary = "Test gửi order", description = "Tạo order mẫu và gửi vào queue để test")
    @GetMapping("/test")
    public ResponseEntity<String> testSend() {
        OrderEvent event = new OrderEvent(1L, 100L, BigDecimal.valueOf(99.99), "CREATED");
        orderProducer.sendOrderCreated(event);
        return ResponseEntity.ok("Test order event sent!");
    }
}
