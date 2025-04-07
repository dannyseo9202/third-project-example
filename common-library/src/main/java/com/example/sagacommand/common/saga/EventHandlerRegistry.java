package com.example.sagacommand.common.saga;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 이벤트 타입에 따라 해당 이벤트를 처리할 핸들러를 등록 및 조회하는 레지스트리입니다.
 */
public class EventHandlerRegistry {
    private final ConcurrentMap<Class<?>, EventHandler<?>> handlers = new ConcurrentHashMap<>();

    /**
     * 이벤트 타입에 해당하는 핸들러를 등록합니다.
     *
     * @param eventType 이벤트 타입
     * @param handler 해당 이벤트를 처리할 핸들러
     * @param <T> 이벤트 타입
     */
    public <T> void registerHandler(Class<?> eventType, EventHandler<T> handler) {
        handlers.putIfAbsent(eventType, handler);
    }

    /**
     * 이벤트 타입에 등록된 핸들러를 조회합니다.
     *
     * @param eventType 이벤트 타입
     * @param <T> 이벤트 타입
     * @return 해당 이벤트를 처리할 핸들러 (없을 경우 null)
     */
    @SuppressWarnings("unchecked")
    public <T> EventHandler<T> getHandler(Class<T> eventType) {
        return (EventHandler<T>) handlers.get(eventType);
    }

    /**
     * 해당 이벤트 타입에 대한 핸들러가 등록되어 있는지 확인합니다.
     *
     * @param eventType 이벤트 타입
     * @return true: 핸들러 있음 / false: 없음
     */
    public boolean hasHandler(Class<?> eventType) {
        return handlers.containsKey(eventType);
    }
}
