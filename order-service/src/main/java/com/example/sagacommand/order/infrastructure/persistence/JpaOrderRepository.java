package com.example.sagacommand.order.infrastructure.persistence;

import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOrderRepository extends OrderRepository, JpaRepository<Order, Long> {

}
