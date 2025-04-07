package com.example.sagacommand.common.saga;

import com.example.sagacommand.common.command.ScopedCommandInvoker;

/**
 * 특정 이벤트를 처리하기 위한 핸들러입니다.
 *
 * @param <T> 처리할 이벤트의 타입
 */
public interface EventHandler<T> {
    /**
     * 주어진 이벤트를 처리합니다.
     *
     * @param event   이벤트 객체
     * @param invoker Saga 내부에서 사용할 명령 인보커
     */
    void handle(T event, ScopedCommandInvoker invoker);
}
