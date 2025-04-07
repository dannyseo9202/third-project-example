package com.example.sagacommand.order.infrastructure.saga;

import com.example.sagacommand.common.command.ScopedCommandInvoker;
import com.example.sagacommand.common.event.PaymentFailedEvent;
import com.example.sagacommand.common.saga.EventHandler;
import com.example.sagacommand.order.application.command.CancelOrderCommand;
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
public class PaymentFailedEventHandler implements EventHandler<PaymentFailedEvent> {
    private final OrderCommandFactory factory;

    @Override
    public void handle(PaymentFailedEvent event, ScopedCommandInvoker invoker) {
        UUID orderId = event.getOrderId();
        try {
            // 상태 업데이트 명령 실행
            UpdateOrderStatusCommand updateCommand = factory.updateOrderStatusCommand(
                    orderId, Order.OrderStatus.PAYMENT_FAILED);
            invoker.executeCommand(updateCommand);

            // 보상 트랜잭션으로 주문 취소 명령 실행
            CancelOrderCommand cancelCommand = factory.cancelOrderCommand(orderId);
            invoker.executeCommand(cancelCommand);

            log.info("결제 실패 처리 및 보상 트랜잭션 완료: {}", orderId);
        } catch (Exception e) {
            log.error("결제 실패 처리 오류: {}, 이유: {}", orderId, e.getMessage());
        }
    }
}
