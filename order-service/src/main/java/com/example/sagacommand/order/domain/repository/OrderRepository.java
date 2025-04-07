package com.example.sagacommand.order.domain.repository;

import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;

import java.util.Optional;

public interface OrderRepository {
    Order save(Order entity);

    Optional<Order> findById(OrderId orderId);
}
