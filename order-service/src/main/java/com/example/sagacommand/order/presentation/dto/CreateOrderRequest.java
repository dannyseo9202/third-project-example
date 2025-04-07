package com.example.sagacommand.order.presentation.dto;

import com.example.sagacommand.order.application.dto.OrderCreation;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "고객 ID는 필수입니다.")
        UUID customerId,

        @NotEmpty(message = "상품 목록은 비어 있을 수 없습니다.")
        @Size(max = 100, message = "최대 100개의 상품만 주문할 수 있습니다.")
        List<UUID> items
) {
    public OrderCreation toApplicationDto() {
        return new OrderCreation(customerId, items);
    }
}
