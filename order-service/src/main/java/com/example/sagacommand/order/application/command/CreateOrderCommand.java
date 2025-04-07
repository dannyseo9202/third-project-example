package com.example.sagacommand.order.application.command;

import com.example.sagacommand.common.command.Command;
import com.example.sagacommand.common.event.OrderCancelledEvent;
import com.example.sagacommand.common.event.OrderCreatedEvent;
import com.example.sagacommand.order.application.OrderEventPublisher;
import com.example.sagacommand.order.application.dto.OrderCreation;
import com.example.sagacommand.order.domain.model.Money;
import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;
import com.example.sagacommand.order.domain.model.OrderItem;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class CreateOrderCommand implements Command<Order> {
    private final OrderCreation request;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private OrderId orderId;

    @Override
    @Transactional
    public Order execute() {
        orderId = OrderId.generate();
        List<OrderItem> items = request.items().stream().map(e -> OrderItem.create(UUID.randomUUID(), 1, Money.ZERO)).toList();

        Order savedOrder = orderRepository.save(Order.create(orderId, request.customerId(), items));
        OrderCreatedEvent event = OrderCreatedEvent.of(savedOrder.getId(), savedOrder.getCustomerId());
        orderEventPublisher.publishEvent(savedOrder.getId().toString(), event);

        log.info("주문 생성 완료: {}", savedOrder.getId());
        return savedOrder;
    }

    @Override
    @Transactional
    public void undo() {
        if (orderId == null) return;

        orderRepository.findById(OrderId.of(orderId.getValue())).ifPresent(retrievedOrder -> {
            retrievedOrder.cancel();
            orderRepository.save(retrievedOrder);

            OrderCancelledEvent event = OrderCancelledEvent.of(orderId.getValue());
            orderEventPublisher.publishEvent(retrievedOrder.getId().toString(), event);

            log.info("주문 생성 취소(Undo): {}", retrievedOrder.getId());
        });
    }
}

