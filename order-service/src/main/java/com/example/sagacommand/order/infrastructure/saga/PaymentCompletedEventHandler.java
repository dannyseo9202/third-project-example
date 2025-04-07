package com.example.sagacommand.order.infrastructure.saga;

import com.example.sagacommand.common.command.ScopedCommandInvoker;
import com.example.sagacommand.common.event.PaymentCompletedEvent;
import com.example.sagacommand.common.saga.EventHandler;
import com.example.sagacommand.order.application.command.OrderCommandFactory;
import com.example.sagacommand.order.application.command.UpdateOrderStatusCommand;
import com.example.sagacommand.order.domain.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedEventHandler implements EventHandler<PaymentCompletedEvent> {
    private final OrderCommandFactory factory;

    @Override
    public void handle(PaymentCompletedEvent event, ScopedCommandInvoker invoker) {
        UUID orderId = event.getOrderId();
        try {
            // 1. 주문 상태 업데이트 - PAID
            UpdateOrderStatusCommand updateCommand = factory.updateOrderStatusCommand(
                    orderId, Order.OrderStatus.PAID);
            invoker.executeCommand(updateCommand);

            log.info("결제 완료 처리됨: {}", orderId);
        } catch (Exception e) {
            log.error("결제 완료 처리 실패: {}, 이유: {}", orderId, e.getMessage());
            invoker.undoLastCommand();
        }
    }
}
