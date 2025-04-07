package com.example.sagacommand.order.presentation.dto;

import com.example.sagacommand.order.application.dto.OrderCancellation;

import java.util.UUID;

public record CancelOrderRequest(UUID orderId) {
    public OrderCancellation toApplicationDto() {
        return new OrderCancellation(orderId);
    }
}
