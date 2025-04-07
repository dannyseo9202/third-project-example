package com.example.sagacommand.order.application.command;

import com.example.sagacommand.common.command.Command;
import com.example.sagacommand.common.event.PaymentRefundRequestedEvent;
import com.example.sagacommand.order.application.OrderEventPublisher;
import com.example.sagacommand.order.application.exception.OrderNotFoundException;
import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 환불을 요청하는 커맨드입니다.
 * 주문에 결제 정보가 존재할 경우 환불 요청 이벤트를 발행합니다.
 */
@Slf4j
@RequiredArgsConstructor
public class RefundPaymentCommand implements Command<Boolean> {

    private final OrderId orderId;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    @Override
    public Boolean execute() {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

        UUID paymentId = order.getPaymentId();

        if (paymentId == null) {
            log.warn("환불할 결제 정보 없음: 주문={}", orderId);
            return false;
        }

        publishRefundEvent(orderId, paymentId);
        return true;
    }

    private void publishRefundEvent(OrderId orderId, UUID paymentId) {
        PaymentRefundRequestedEvent event = PaymentRefundRequestedEvent.of(
                orderId.getValue(), paymentId, LocalDateTime.now());

        orderEventPublisher.publishEvent(orderId.toString(), event);
        log.info("결제 환불 요청 이벤트 발행: 주문={}, 결제={}", orderId, paymentId);
    }

    @Override
    public void undo() {
        log.warn("환불 취소 요청됨 (수동 처리 필요): 주문={}", orderId);
    }
}
