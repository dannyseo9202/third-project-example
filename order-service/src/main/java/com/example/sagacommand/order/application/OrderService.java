package com.example.sagacommand.order.application;

import com.example.sagacommand.common.command.ScopedCommandInvoker;
import com.example.sagacommand.common.event.Event;
import com.example.sagacommand.common.event.EventProcessor;
import com.example.sagacommand.order.application.command.CancelOrderCommand;
import com.example.sagacommand.order.application.command.CreateOrderCommand;
import com.example.sagacommand.order.application.command.OrderCommandFactory;
import com.example.sagacommand.order.application.dto.OrderCreation;
import com.example.sagacommand.order.application.dto.OrderResult;
import com.example.sagacommand.order.application.exception.OrderCancellationException;
import com.example.sagacommand.order.application.exception.OrderCreationException;
import com.example.sagacommand.order.application.exception.OrderNotFoundException;
import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderCommandFactory orderCommandFactory;
    private final ScopedCommandInvoker commandInvoker;
    private final EventProcessor eventProcessor;
    private final OrderRepository orderRepository;

    // 주문 생성 - 커맨드 실행
    public OrderResult createOrder(OrderCreation request) {
        try {
            CreateOrderCommand command = orderCommandFactory.createOrderCommand(request);
            Order order = commandInvoker.executeCommand(command);
            log.info("주문 생성 성공: {}", order.getId());
            return OrderResult.from(order);
        } catch (Exception e) {
            log.error("주문 생성 실패: {}", e.getMessage());
            throw new OrderCreationException("주문 생성 중 오류 발생: " + e.getMessage());
        }
    }

    // 주문 취소 - 커맨드 실행
    public OrderResult cancelOrder(String orderId) {
        try {
            CancelOrderCommand command = orderCommandFactory.cancelOrderCommand(UUID.fromString(orderId));
            Order order = commandInvoker.executeCommand(command);
            log.info("주문 취소 성공: {}", order.getId());
            return OrderResult.from(order);
        } catch (Exception e) {
            log.error("주문 취소 실패: {}", e.getMessage());
            throw new OrderCancellationException("주문 취소 중 오류 발생: " + e.getMessage());
        }
    }

    /*
    선택 가이드:
        1. 커맨드 패턴 사용: 애플리케이션의 일관성을 유지하고 싶거나, 추후 복잡한 조회 로직이 추가될 가능성이 있는 경우
        ex) GetOrderCommand, SearchOrderCommand
        2. 리포지토리 직접 사용: 간단한 조회만 필요하고 커맨드 패턴의 오버헤드를 줄이고 싶은 경우
     */
    public OrderResult getOrder(String orderId) {
        return OrderResult.from(orderRepository.findById(OrderId.of(UUID.fromString(orderId)))
                .orElseThrow(() -> {
                    log.warn("주문을 찾을 수 없음: {}", orderId);
                    return new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId);
                }));

    }

    // 이벤트 리스너 - 카프카에서 이벤트 수신
    @KafkaListener(topics = {"payment-events", "inventory-events"})
    public void handlePaymentEvents(Event event) {
        log.info("이벤트 수신: {}", event.getClass().getSimpleName());
        eventProcessor.processEvent(event);
    }
}
