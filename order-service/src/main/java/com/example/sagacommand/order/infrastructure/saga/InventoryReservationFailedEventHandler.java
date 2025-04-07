package com.example.sagacommand.order.infrastructure.saga;

import com.example.sagacommand.common.command.ScopedCommandInvoker;
import com.example.sagacommand.common.event.InventoryReservationFailedEvent;
import com.example.sagacommand.common.saga.EventHandler;
import com.example.sagacommand.order.application.command.CancelOrderCommand;
import com.example.sagacommand.order.application.command.OrderCommandFactory;
import com.example.sagacommand.order.application.command.RefundPaymentCommand;
import com.example.sagacommand.order.application.command.UpdateOrderStatusCommand;
import com.example.sagacommand.order.domain.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReservationFailedEventHandler implements EventHandler<InventoryReservationFailedEvent> {
    private final OrderCommandFactory factory;

    @Override
    public void handle(InventoryReservationFailedEvent event, ScopedCommandInvoker invoker) {
        UUID orderId = event.getOrderId();
        try {
            // 주문 상태 업데이트
            UpdateOrderStatusCommand updateCommand = factory.updateOrderStatusCommand(
                    orderId, Order.OrderStatus.INVENTORY_FAILED);
            invoker.executeCommand(updateCommand);

            // 보상 트랜잭션: 결제 환불
            RefundPaymentCommand refundCommand = factory.refundPaymentCommand(orderId);
            invoker.executeCommand(refundCommand);

            // 주문 취소 (최종 보상 트랜잭션)
            CancelOrderCommand cancelCommand = factory.cancelOrderCommand(orderId);
            invoker.executeCommand(cancelCommand);

            log.info("재고 예약 실패 처리 및 결제 환불, 주문 취소됨: {}", orderId);
        } catch (Exception e) {
            log.error("재고 예약 실패 처리 오류: {}, 이유: {}", orderId, e.getMessage());
        }
    }
}
