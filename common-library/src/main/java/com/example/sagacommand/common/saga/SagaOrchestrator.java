package com.example.sagacommand.common.saga;

import com.example.sagacommand.common.command.ScopedCommandInvoker;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * SagaOrchestrator는 Saga 패턴에서 이벤트를 받아서
 * 해당 이벤트에 적절한 핸들러(EventHandler)를 찾아 실행하는 중앙 조정자입니다.
 */
@Slf4j
public class SagaOrchestrator {

    private final ScopedCommandInvoker commandInvoker;
    private final EventHandlerRegistry handlerRegistry;

    /**
     * 생성자에서는 핸들러들을 자동으로 등록합니다.
     * @param commandInvoker Saga 내에서 커맨드 전송 역할
     * @param handlerRegistry 핸들러들을 저장하고 조회할 수 있는 Registry
     * @param eventHandlers 이벤트 핸들러 목록 (Spring 등이 주입 가능)
     */
    public SagaOrchestrator(
            ScopedCommandInvoker commandInvoker,
            EventHandlerRegistry handlerRegistry,
            List<EventHandler<?>> eventHandlers
    ) {
        this.commandInvoker = commandInvoker;
        this.handlerRegistry = handlerRegistry;
        registerHandlers(eventHandlers); // 이벤트 핸들러 자동 등록
    }

    /**
     * 이벤트 핸들러들을 EventHandlerRegistry에 등록하는 메서드입니다.
     * 핸들러가 어떤 이벤트 타입을 다루는지 제네릭 타입을 추출하여 등록합니다.
     */
    private void registerHandlers(List<EventHandler<?>> eventHandlers) {
        log.info("Saga 이벤트 핸들러 등록 시작");

        for (EventHandler<?> handler : eventHandlers) {
            try {
                Class<?> eventType = extractEventType(handler); // 제네릭 타입 추출
                handlerRegistry.registerHandler(eventType, handler); // 등록
                log.debug("핸들러 등록 완료: [{}] → [{}]", eventType.getSimpleName(), handler.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("핸들러 등록 실패: {}", handler.getClass().getName(), e);
                throw new IllegalStateException("Saga 이벤트 핸들러 초기화 실패", e);
            }
        }

        log.info("Saga 이벤트 핸들러 등록 완료 (총 {}개)", eventHandlers.size());
    }

    /**
     * 제네릭 타입 EventHandler<T>에서 이벤트 타입(T)을 추출하는 헬퍼 메서드입니다.
     * handler 클래스가 implements EventHandler<T> 를 명시했을 때만 동작합니다.
     */
    private Class<?> extractEventType(EventHandler<?> handler) {
        for (Type iface : handler.getClass().getGenericInterfaces()) {
            if (iface instanceof ParameterizedType parameterized) {
                Type rawType = parameterized.getRawType();
                if (rawType instanceof Class<?> rawClass && EventHandler.class.isAssignableFrom(rawClass)) {
                    Type actualType = parameterized.getActualTypeArguments()[0];
                    if (actualType instanceof Class<?> actualClass) {
                        return actualClass; // T 타입 반환
                    }
                }
            }
        }
        throw new IllegalStateException("이벤트 핸들러 제네릭 타입 추출 실패: " + handler.getClass().getName());
    }

    /**
     * 실제 외부에서 이벤트를 전달받아 처리하는 핵심 메서드입니다.
     * 이벤트 타입에 맞는 핸들러가 존재하면 실행하고,
     * 존재하지 않으면 경고 로그를 출력합니다.
     */
    public final void processEvent(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("이벤트는 null일 수 없습니다");
        }

        Class<?> eventType = event.getClass();
        String eventName = eventType.getSimpleName();

        // 등록되지 않은 이벤트인 경우 처리하지 않음
        if (!handlerRegistry.hasHandler(eventType)) {
            log.warn("등록되지 않은 Saga 이벤트 수신: {}", eventName);
            return;
        }

        log.info("Saga 이벤트 처리 시작: {}", eventName);

        try {
            // 핸들러 꺼내서 실행 (타입 안전성을 위해 캐스팅)
            @SuppressWarnings("unchecked")
            EventHandler<Object> handler = handlerRegistry.getHandler((Class<Object>) eventType);
            handler.handle(event, commandInvoker);
            log.info("Saga 이벤트 처리 완료: {}", eventName);
        } catch (Exception e) {
            log.error("Saga 이벤트 처리 중 오류 ({}): {}", eventName, e.getMessage(), e);
        } finally {
            // 커맨드 인보커 종료
            try {
                commandInvoker.close();
            } catch (Exception e) {
                log.warn("commandInvoker 종료 중 예외 발생", e);
            }
        }
    }
}
