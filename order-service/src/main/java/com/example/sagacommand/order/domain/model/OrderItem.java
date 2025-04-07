package com.example.sagacommand.order.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

import java.util.UUID;

@Entity
@Getter
public class OrderItem {
    @Id
    private UUID id;
    private UUID productId;
    private int quantity;
    private Money price;

    public static OrderItem create(UUID productId, int quantity, Money price) {
        OrderItem item = new OrderItem();
        item.productId = productId;
        item.quantity = quantity;
        item.price = price;
        return item;
    }
}

