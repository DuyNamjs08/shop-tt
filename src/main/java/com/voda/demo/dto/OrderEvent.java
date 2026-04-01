package com.voda.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderEvent implements Serializable {
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String status;
}
