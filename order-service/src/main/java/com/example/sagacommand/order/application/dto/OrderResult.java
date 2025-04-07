package com.example.sagacommand.order.application.dto;

import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderResult(
        UUID orderId,
        UUID customerId,
        String status,
        UUID paymentId,
        Integer appliedPoints,
        BigDecimal totalAmount,
        List<OrderItemResult> items
) {
    public record OrderItemResult(
            UUID productId,
            int quantity,
            BigDecimal price
    ) {
        public static OrderItemResult from(OrderItem item) {
            return new OrderItemResult(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getPrice().getValue()
            );
        }
    }

    public static OrderResult from(Order order) {
        return new OrderResult(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getPaymentId(),
                order.getAppliedPoints(),
                order.getTotalAmount().getValue(),
                order.getOrderItems().stream()
                        .map(OrderItemResult::from)
                        .toList()
        );
    }
}
