# Saga & Command Pattern Implementation

해당 예시 코드는 분산 트랜잭션을 효과적으로 관리하기 위한 Saga 패턴과 명령 실행을 추상화하는 Command 패턴을 통합하여 만들었습니다.


## 핵심 기능
- 커맨드 패턴을 사용한 작업 추상화 및 롤백 메커니즘
- 사가 패턴을 통한 분산 트랜잭션 관리
- 이벤트 기반 아키텍처를 사용한 마이크로서비스 간 통신
- Kafka를 활용한 이벤트 발행 및 구독

## 아키텍처 개요
애플리케이션은 DDD의 4계층 아키텍처를 적용하였습니다:

- **표현 계층**: 클라이언트의 요청을 처리
- **애플리케이션 계층**: 유스케이스 구현 및 커맨드 정의
- **도메인 계층**: 핵심 비즈니스 로직과 엔티티
- **인프라스트럭처 계층**: 외부 시스템 통합 및 이벤트 처리

## Command 패턴 구현
커맨드 패턴은 요청을 객체로 캡슐화하여 매개변수화된 클라이언트, 요청 대기열, 로깅, 트랜잭션 롤백 등을 지원합니다.

### 핵심 컴포넌트

```java
public interface Command<T> {
    T execute();
    void undo();
}
```

모든 명령은 이 인터페이스를 구현하여 실행 로직과 롤백 로직을 정의합니다.

### 구현 예시: 주문 생성 커맨드

```java
public class CreateOrderCommand implements Command<Order> {
    private final OrderCreation orderRequest;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private Order createdOrder;

    @Override
    public Order execute() {
        Order order = Order.create(
                OrderId.newOrderId(),
                orderRequest.customerId(),
                orderRequest.items()
        );
        
        createdOrder = orderRepository.save(order);
        
        OrderCreatedEvent event = OrderCreatedEvent.of(
                createdOrder.getId().getValue(),
                createdOrder.getCustomerId(),
                LocalDateTime.now()
        );
        
        eventPublisher.publishEvent(createdOrder.getId().toString(), event);
        return createdOrder;
    }

    @Override
    public void undo() {
        if (createdOrder != null) {
            createdOrder.cancel();
            orderRepository.save(createdOrder);
            
            OrderCancelledEvent event = OrderCancelledEvent.of(
                    createdOrder.getId().getValue(),
                    "Command rollback",
                    LocalDateTime.now()
            );
            
            eventPublisher.publishEvent(createdOrder.getId().toString(), event);
        }
    }
}

@Component
@RequiredArgsConstructor
public class OrderCommandFactory implements CommandFactory {
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    // 주문 생성 명령 객체 생성
    public CreateOrderCommand createOrderCommand(OrderCreation creation) {
        return new CreateOrderCommand(creation, orderRepository, orderEventPublisher);
    }
    다른 커맨드 ...
}
```

### 커맨드 인보커 (Command Invoker)

커맨드 인보커는 명령 실행과 롤백을 관리합니다:

```java
public class ScopedCommandInvoker {
    private final Deque<Command<?>> executedCommands = new ArrayDeque<>();

    public <T> T executeCommand(Command<T> command) {
        try {
            T result = command.execute();
            executedCommands.push(command);
            return result;
        } catch (Exception e) {
            undoLastCommand();
            throw e;
        }
    }

    public void undoLastCommand() {
        if (!executedCommands.isEmpty()) {
            Command<?> command = executedCommands.pop();
            command.undo();
        }
    }

    public void undoAllCommands() {
        while (!executedCommands.isEmpty()) {
            undoLastCommand();
        }
    }
}
```

## Saga 패턴 구현

Saga 패턴은 분산 트랜잭션을 여러 독립적인 로컬 트랜잭션으로 분할하여 장애 발생 시 보상 트랜잭션을 통해 일관성을 유지합니다.

### 핵심 컴포넌트

```java
public interface EventHandler<T> {
    /**
     * 주어진 이벤트를 처리합니다.
     *
     * @param event   이벤트 객체
     * @param invoker Saga 내부에서 사용할 명령 인보커
     */
    void handle(T event, ScopedCommandInvoker invoker);
}
```

각 이벤트 핸들러는 특정 유형의 이벤트를 처리할 수 있으며, 필요한 커맨드를 실행합니다.

### 이벤트 핸들러 레지스트리

```java
public class EventHandlerRegistry {
    private final ConcurrentMap<Class<?>, EventHandler<?>> handlers = new ConcurrentHashMap<>();

    /**
     * 이벤트 타입에 해당하는 핸들러를 등록합니다.
     */
    public <T> void registerHandler(Class<?> eventType, EventHandler<T> handler) {
        handlers.putIfAbsent(eventType, handler);
    }

    /**
     * 이벤트 타입에 등록된 핸들러를 조회합니다.
     */
    @SuppressWarnings("unchecked")
    public <T> EventHandler<T> getHandler(Class<T> eventType) {
        return (EventHandler<T>) handlers.get(eventType);
    }

    /**
     * 해당 이벤트 타입에 대한 핸들러가 등록되어 있는지 확인합니다.
     */
    public boolean hasHandler(Class<?> eventType) {
        return handlers.containsKey(eventType);
    }
}
```

### 구현 예시: 결제 완료 이벤트 핸들러

```java
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
```

### 사가 오케스트레이터 (Saga Orchestrator)

사가 오케스트레이터는 이벤트 핸들러를 호출하고 롤백 메커니즘을 관리합니다:

```java
public class SagaOrchestrator {
    private final ScopedCommandInvoker commandInvoker;
    private final EventHandlerRegistry handlerRegistry;

    public SagaOrchestrator(
            ScopedCommandInvoker commandInvoker,
            EventHandlerRegistry handlerRegistry,
            List<EventHandler<?>> eventHandlers
    ) {
        this.commandInvoker = commandInvoker;
        this.handlerRegistry = handlerRegistry;
        registerHandlers(eventHandlers); // 이벤트 핸들러 자동 등록
    }

    private void registerHandlers(List<EventHandler<?>> eventHandlers) {
        // 핸들러 등록 로직...
    }

    public final void processEvent(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("이벤트는 null일 수 없습니다");
        }

        Class<?> eventType = event.getClass();
        
        // 등록되지 않은 이벤트인 경우 처리하지 않음
        if (!handlerRegistry.hasHandler(eventType)) {
            log.warn("등록되지 않은 Saga 이벤트 수신: {}", eventType.getSimpleName());
            return;
        }

        try {
            // 핸들러 꺼내서 실행 (타입 안전성을 위해 캐스팅)
            @SuppressWarnings("unchecked")
            EventHandler<Object> handler = handlerRegistry.getHandler((Class<Object>) eventType);
            handler.handle(event, commandInvoker);
        } catch (Exception e) {
            log.error("Saga 이벤트 처리 중 오류: {}", e.getMessage(), e);
        } finally {
            // 커맨드 인보커 종료
            commandInvoker.close();
        }
    }
}
```

## 복잡한 Saga 흐름 예시: 재고 예약 실패

```java
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
```

## 통합 이벤트

이 구현에서는 다양한 통합 이벤트를 사용하여 분산 시스템 간의 통신을 처리합니다:

- `OrderCreatedEvent`: 주문 생성 시 발행
- `OrderCancelledEvent`: 주문 취소 시 발행
- `PaymentCompletedEvent`: 결제 완료 시 외부 시스템에서 발행
- `PaymentFailedEvent`: 결제 실패 시 외부 시스템에서 발행
- `InventoryReservationFailedEvent`: 재고 예약 실패 시 외부 시스템에서 발행

## 이벤트 처리 흐름

1. 사용자 요청이 시스템에 들어오면 적절한 커맨드 객체가 생성됩니다.
2. 커맨드는 도메인 모델을 변경하고 도메인 이벤트를 발행합니다.
3. 외부 시스템은 이벤트를 구독하고 적절한 비즈니스 로직을 실행합니다.
4. 외부 시스템에서 발행된 이벤트를 수신하면 `SagaOrchestrator`가 이벤트 타입에 맞는 핸들러를 `EventHandlerRegistry`에서 찾습니다.
5. 이벤트 핸들러는 새로운 커맨드를 생성하고 `ScopedCommandInvoker`를 통해 실행합니다.
6. 오류가 발생하면 `ScopedCommandInvoker`의 `undoLastCommand()`를 통해 롤백이 수행됩니다.
7. 복잡한 워크플로우의 경우 여러 커맨드가 순차적으로 실행되고, 성공적으로 완료되거나 오류 발생 시 보상 트랜잭션이 실행됩니다.

## 테스트

아래는 테스트 예시입니다, 직접 코드에서 확인해보시면 조금 더 이해하시기 쉬울거에요

### 단위 테스트 예시

```java
@Test
@DisplayName("주문 생성 커맨드 실행 시 주문이 저장되고 이벤트가 발행된다")
void shouldSaveOrderAndPublishEvent_whenCreateOrderCommandExecuted() {
    Order result = commandInvoker.executeCommand(createOrderCommand);

    assertNotNull(result);
    verify(orderRepository).save(any(Order.class));
    verify(eventPublisher).publishEvent(anyString(), any(OrderCreatedEvent.class));
}
```

### 통합 테스트 예시: Kafka와 함께 Saga 패턴 테스트

```java
@Test
@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
void shouldUpdateOrderStatusToInventoryFailedAndCancelOrder_whenInventoryReservationFailedEventReceived() {
    OrderCreation request = createSampleOrderRequest();
    OrderResult result = orderService.createOrder(request);
    UUID orderId = result.orderId();
    
    assertEquals(Order.OrderStatus.CREATED, Order.OrderStatus.valueOf(result.status()));
    
    InventoryReservationFailedEvent inventoryEvent = InventoryReservationFailedEvent.of(orderId, "Out of stock");
    inventoryEventsProducer.send(new ProducerRecord<>(inventoryEventsTopic, orderId.toString(), inventoryEvent));
    inventoryEventsProducer.flush();
    
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
        Order updatedOrder = orderRepository.findById(OrderId.of(orderId)).orElse(null);
        assertNotNull(updatedOrder);
        assertEquals(Order.OrderStatus.CANCELLED, updatedOrder.getStatus());
    });
    
    // 주문 생성 이벤트 이후에 발행된 취소 이벤트를 확인하기 위해
    // 소비된 모든 레코드를 확인하고 OrderCancelledEvent 타입을 찾습니다
    ConsumerRecord<String, Object> cancelRecord = null;
    
    // 여러 레코드를 가져옵니다
    Iterable<ConsumerRecord<String, Object>> records = KafkaTestUtils.getRecords(
            orderEventsConsumer, Duration.of(10, ChronoUnit.SECONDS), 2).records(orderEventsTopic);
    
    // 레코드들을 순회하며 OrderCancelledEvent를 찾습니다
    for (ConsumerRecord<String, Object> record : records) {
        if (record.value() instanceof OrderCancelledEvent) {
            cancelRecord = record;
            break;
        }
    }
    
    assertThat(cancelRecord).isNotNull();
    OrderCancelledEvent cancelEvent = (OrderCancelledEvent) cancelRecord.value();
    assertThat(cancelEvent.getOrderId()).isEqualTo(orderId);
}
```
