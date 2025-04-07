package com.example.sagacommand.order.application.command;

import com.example.sagacommand.common.command.Command;
import com.example.sagacommand.common.event.OrderCancelledEvent;
import com.example.sagacommand.order.application.OrderEventPublisher;
import com.example.sagacommand.order.application.exception.OrderNotFoundException;
import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CancelOrderCommand implements Command<Order> {
    private final OrderId orderId;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    private Order.OrderStatus previousStatus;
    private Order order;

    @Override
    @Transactional
    public Order execute() {
        order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

        previousStatus = order.getStatus();
        order.cancel();

        Order savedOrder = orderRepository.save(order);

        // 주문 취소 이벤트 발행
        OrderCancelledEvent event = OrderCancelledEvent.of(savedOrder.getId());
        orderEventPublisher.publishEvent(savedOrder.getId().toString(), event);

        log.info("주문 취소 완료: {}", savedOrder.getId());

        return savedOrder;
    }

    @Override
    @Transactional
    public void undo() {
        if (order != null && previousStatus != null) {
            // 이전 상태로 되돌림 (실제로는 상태 관리가 더 복잡할 수 있음)
            Order refreshedOrder = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

            // 상태를 수동으로 롤백 (실제로는 이벤트 소싱 등을 활용해 더 견고하게 구현 필요)
            switch (previousStatus) {
                case CREATED:
                    refreshedOrder.markAsCreated();
                    break;
                case PAID:
                    refreshedOrder.markAsPaid();
                    break;
                case INVENTORY_RESERVED:
                    refreshedOrder.markAsInventoryReserved();
                    break;
                // 다른 상태에 대한 처리...
                default:
                    break;
            }

            orderRepository.save(refreshedOrder);
            log.info("주문 취소 취소(Undo): {}, 이전 상태: {}", refreshedOrder.getId(), previousStatus);
        }
    }
}
