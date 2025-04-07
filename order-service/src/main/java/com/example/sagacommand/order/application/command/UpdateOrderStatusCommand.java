package com.example.sagacommand.order.application.command;

import com.example.sagacommand.common.command.Command;
import com.example.sagacommand.order.application.exception.OrderNotFoundException;
import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class UpdateOrderStatusCommand implements Command<Order> {
    private final OrderId orderId;
    private final Order.OrderStatus newStatus;
    private final OrderRepository orderRepository;

    private Order.OrderStatus previousStatus;  // undo를 위한 상태 저장

    @Override
    @Transactional
    public Order execute() {
        Order order = orderRepository.findById(OrderId.of(orderId.getValue()))
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

        previousStatus = order.getStatus();  // 이전 상태 저장

        switch (newStatus) {
            case PAID:
                order.markAsPaid();
                break;
            case PAYMENT_FAILED:
                order.markAsPaymentFailed();
                break;
            case INVENTORY_FAILED:
                order.markAsInventoryFailed();
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 상태: " + newStatus);
        }

        log.info("주문 상태 업데이트: {} -> {}", previousStatus, newStatus);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public void undo() {
        if (previousStatus != null) {
            Order order = orderRepository.findById(OrderId.of(orderId.getValue()))
                    .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

            // 이전 상태로 되돌리기
            switch (previousStatus) {
                case CREATED:
                    order.markAsCreated();
                    break;
                case PAID:
                    order.markAsPaid();
                    break;
                default:
                    break;
            }

            orderRepository.save(order);
            log.info("주문 상태 롤백: {} -> {}", order.getStatus(), previousStatus);
        }
    }
}