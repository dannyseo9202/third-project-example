package com.example.sagacommand.order.application.saga;

import com.example.sagacommand.common.command.ScopedCommandInvoker;
import com.example.sagacommand.common.event.EventProcessor;
import com.example.sagacommand.common.event.InventoryReservationFailedEvent;
import com.example.sagacommand.common.event.PaymentCompletedEvent;
import com.example.sagacommand.common.event.PaymentFailedEvent;
import com.example.sagacommand.common.saga.EventHandler;
import com.example.sagacommand.common.saga.EventHandlerRegistry;
import com.example.sagacommand.common.saga.SagaEventProcessor;
import com.example.sagacommand.common.saga.SagaOrchestrator;
import com.example.sagacommand.order.application.OrderEventPublisher;
import com.example.sagacommand.order.application.command.CancelOrderCommand;
import com.example.sagacommand.order.application.command.OrderCommandFactory;
import com.example.sagacommand.order.application.command.RefundPaymentCommand;
import com.example.sagacommand.order.application.command.UpdateOrderStatusCommand;
import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import com.example.sagacommand.order.infrastructure.saga.InventoryReservationFailedEventHandler;
import com.example.sagacommand.order.infrastructure.saga.PaymentCompletedEventHandler;
import com.example.sagacommand.order.infrastructure.saga.PaymentFailedEventHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventPublisher eventPublisher;
    @Mock
    private OrderCommandFactory commandFactory;

    private ScopedCommandInvoker commandInvoker;
    private EventProcessor eventProcessor;

    private UUID orderId;
    private OrderId orderIdObj;
    private Order mockOrder;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        orderIdObj = OrderId.of(orderId);
        mockOrder = Order.create(orderIdObj, UUID.randomUUID(), List.of());
        commandInvoker = spy(new ScopedCommandInvoker());

        List<EventHandler<?>> handlers = List.of(
                new PaymentCompletedEventHandler(commandFactory),
                new PaymentFailedEventHandler(commandFactory),
                new InventoryReservationFailedEventHandler(commandFactory)
        );

        eventProcessor = new SagaEventProcessor(new SagaOrchestrator(commandInvoker, new EventHandlerRegistry(), handlers));
    }

    @Test
    @DisplayName("결제가 완료되면 주문 상태가 PAID로 변경된다")
    void shouldUpdateOrderStatusToPaid_whenPaymentIsCompleted() {
        // given
        when(orderRepository.findById(eq(orderIdObj))).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        PaymentCompletedEvent event = PaymentCompletedEvent.of(orderId, orderId, BigDecimal.valueOf(30000));
        UpdateOrderStatusCommand updateCommand = new UpdateOrderStatusCommand(orderIdObj, Order.OrderStatus.PAID, orderRepository);
        when(commandFactory.updateOrderStatusCommand(eq(orderId), eq(Order.OrderStatus.PAID))).thenReturn(updateCommand);

        // when
        eventProcessor.processEvent(event);

        // then
        verify(commandFactory).updateOrderStatusCommand(eq(orderId), eq(Order.OrderStatus.PAID));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("결제 실패 시 주문 상태가 PAYMENT_FAILED로 변경되고 주문이 취소된다")
    void shouldCancelOrder_whenPaymentFails() {
        // given
        when(orderRepository.findById(eq(orderIdObj))).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        PaymentFailedEvent event = PaymentFailedEvent.of(orderId, "Insufficient funds");
        UpdateOrderStatusCommand updateCommand = new UpdateOrderStatusCommand(orderIdObj, Order.OrderStatus.PAYMENT_FAILED, orderRepository);
        CancelOrderCommand cancelCommand = new CancelOrderCommand(orderIdObj, orderRepository, eventPublisher);
        when(commandFactory.updateOrderStatusCommand(eq(orderId), eq(Order.OrderStatus.PAYMENT_FAILED))).thenReturn(updateCommand);
        when(commandFactory.cancelOrderCommand(eq(orderId))).thenReturn(cancelCommand);

        // when
        eventProcessor.processEvent(event);

        // then
        verify(commandFactory).cancelOrderCommand(eq(orderId));
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @Test
    @DisplayName("재고 예약 실패 시 주문 상태가 INVENTORY_FAILED로 변경되고 주문이 취소된다")
    void shouldCancelOrder_whenInventoryReservationFails() {
        // given
        when(orderRepository.findById(eq(orderIdObj))).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        InventoryReservationFailedEvent event = InventoryReservationFailedEvent.of(orderId, "Out of stock");
        UpdateOrderStatusCommand updateCommand = new UpdateOrderStatusCommand(orderIdObj, Order.OrderStatus.INVENTORY_FAILED, orderRepository);
        CancelOrderCommand cancelCommand = new CancelOrderCommand(orderIdObj, orderRepository, eventPublisher);
        RefundPaymentCommand refundCommand = new RefundPaymentCommand(orderIdObj, orderRepository, eventPublisher);
        when(commandFactory.updateOrderStatusCommand(eq(orderId), eq(Order.OrderStatus.INVENTORY_FAILED))).thenReturn(updateCommand);
        when(commandFactory.cancelOrderCommand(eq(orderId))).thenReturn(cancelCommand);
        when(commandFactory.refundPaymentCommand(eq(orderId))).thenReturn(refundCommand);

        // when
        eventProcessor.processEvent(event);

        // then
        verify(commandFactory).updateOrderStatusCommand(eq(orderId), eq(Order.OrderStatus.INVENTORY_FAILED));
        verify(commandFactory).cancelOrderCommand(eq(orderId));
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 처리 중 예외 발생 시 롤백이 수행된다")
    void shouldRollback_whenExceptionOccursDuringPaymentHandling() {
        // given
        PaymentCompletedEvent event = PaymentCompletedEvent.of(orderId, orderId, BigDecimal.valueOf(50000));
        UpdateOrderStatusCommand mockCommand = mock(UpdateOrderStatusCommand.class);
        when(mockCommand.execute()).thenThrow(new RuntimeException("Database error"));
        when(commandFactory.updateOrderStatusCommand(eq(orderId), any())).thenReturn(mockCommand);

        // when
        eventProcessor.processEvent(event);

        // then
        verify(mockCommand).execute();
        verify(commandInvoker).undoLastCommand();
    }
}
