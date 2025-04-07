package com.example.sagacommand.order.application.command;

import com.example.sagacommand.common.command.CommandFactory;
import com.example.sagacommand.order.application.OrderEventPublisher;
import com.example.sagacommand.order.application.dto.OrderCreation;
import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderCommandFactory implements CommandFactory {
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public CreateOrderCommand createOrderCommand(OrderCreation creation) {
        return new CreateOrderCommand(creation, orderRepository, orderEventPublisher);
    }

    // 주문 취소 명령 객체 생성
    public CancelOrderCommand cancelOrderCommand(UUID orderId) {
        return new CancelOrderCommand(OrderId.of(orderId), orderRepository, orderEventPublisher);
    }

    // 주문 상태 업데이트 명령 객체 생성
    public UpdateOrderStatusCommand updateOrderStatusCommand(UUID orderId, Order.OrderStatus newStatus) {
        return new UpdateOrderStatusCommand(OrderId.of(orderId), newStatus, orderRepository);
    }

    public RefundPaymentCommand refundPaymentCommand(UUID orderId) {
        return new RefundPaymentCommand(OrderId.of(orderId), orderRepository, orderEventPublisher);
    }
}
