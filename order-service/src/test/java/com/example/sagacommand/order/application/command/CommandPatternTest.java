package com.example.sagacommand.order.application.command;

import com.example.sagacommand.common.command.ScopedCommandInvoker;
import com.example.sagacommand.common.event.OrderCreatedEvent;
import com.example.sagacommand.common.event.PaymentRefundRequestedEvent;
import com.example.sagacommand.order.application.OrderEventPublisher;
import com.example.sagacommand.order.application.dto.OrderCreation;
import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandPatternTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventPublisher eventPublisher;

    private ScopedCommandInvoker commandInvoker;
    private CreateOrderCommand createOrderCommand;
    private UpdateOrderStatusCommand updateOrderStatusCommand;
    private RefundPaymentCommand refundPaymentCommand;

    @BeforeEach
    void setUp() {
        commandInvoker = new ScopedCommandInvoker();

        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        OrderId orderIdObj = OrderId.of(orderId);
        Order mockOrder = Order.create(orderIdObj, customerId, List.of());
        mockOrder.updatePaymentId(UUID.randomUUID());

        lenient().when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);
        lenient().when(orderRepository.findById(any(OrderId.class))).thenReturn(Optional.of(mockOrder));

        createOrderCommand = new CreateOrderCommand(new OrderCreation(customerId, List.of()), orderRepository, eventPublisher);

        updateOrderStatusCommand = new UpdateOrderStatusCommand(orderIdObj, Order.OrderStatus.PAID, orderRepository);
        refundPaymentCommand = new RefundPaymentCommand(orderIdObj, orderRepository, eventPublisher);
    }

    @Test
    @DisplayName("주문 생성 커맨드 실행 시 주문이 저장되고 이벤트가 발행된다")
    void shouldSaveOrderAndPublishEvent_whenCreateOrderCommandExecuted() {
        Order result = commandInvoker.executeCommand(createOrderCommand);

        assertNotNull(result);
        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publishEvent(anyString(), any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("주문 생성 이후 Undo 실행 시 주문 취소 처리가 수행된다")
    void shouldUndoOrder_whenUndoAfterCreateOrderCommand() {
        Order order = commandInvoker.executeCommand(createOrderCommand);
        assertNotNull(order);

        commandInvoker.undoLastCommand();

        verify(orderRepository).findById(any(OrderId.class));
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(eventPublisher, times(2)).publishEvent(anyString(), any());
    }

    @Test
    @DisplayName("주문 상태 변경 커맨드 실행 시 주문 상태가 갱신된다")
    void shouldUpdateOrderStatus_whenUpdateOrderStatusCommandExecuted() {
        Order result = commandInvoker.executeCommand(updateOrderStatusCommand);

        assertNotNull(result);
        verify(orderRepository).findById(any(OrderId.class));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("결제 정보가 있는 주문에 대해 환불 요청 시 이벤트가 발행된다")
    void shouldPublishRefundEvent_whenOrderHasPaymentInfo() {
        Boolean result = commandInvoker.executeCommand(refundPaymentCommand);

        assertTrue(result);
        verify(orderRepository).findById(any(OrderId.class));
        verify(eventPublisher).publishEvent(anyString(), any(PaymentRefundRequestedEvent.class));
    }

    @Test
    @DisplayName("여러 커맨드 실행 후 UndoAll 호출 시 순차적으로 모두 취소된다")
    void shouldUndoAllCommandsInReverseOrder_whenUndoAllCalled() {
        commandInvoker.executeCommand(createOrderCommand);
        commandInvoker.executeCommand(updateOrderStatusCommand);

        commandInvoker.undoAllCommands();

        verify(orderRepository, times(3)).findById(any(OrderId.class));
        verify(orderRepository, times(4)).save(any(Order.class));
    }
}
