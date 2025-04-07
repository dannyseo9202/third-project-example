package com.example.sagacommand.order.infrastructure.config;

import com.example.sagacommand.common.command.ScopedCommandInvoker;
import com.example.sagacommand.common.event.EventProcessor;
import com.example.sagacommand.common.saga.EventHandler;
import com.example.sagacommand.common.saga.EventHandlerRegistry;
import com.example.sagacommand.common.saga.SagaEventProcessor;
import com.example.sagacommand.common.saga.SagaOrchestrator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SagaConfig {
    /**
     * ScopedCommandInvoker: Saga 내에서 커맨드를 실행하기 위한 유틸 컴포넌트
     * 상태를 가지고 있을 수 있으므로 요청 스코프나 명시적 종료가 중요할 수 있음
     */
    @Bean
    public ScopedCommandInvoker scopedCommandInvoker() {
        return new ScopedCommandInvoker();
    }

    /**
     * EventHandlerRegistry: 이벤트 타입별로 핸들러를 등록 및 조회하는 레지스트리
     */
    @Bean
    public EventHandlerRegistry eventHandlerRegistry() {
        return new EventHandlerRegistry();
    }

    /**
     * EventProcessor: Event를 받아 처리하는 추상화된 컴포넌트.
     * 내부적으로 SagaOrchestrator를 사용하지만 외부에서는 몰라도 됨.
     * <p>
     * - commandInvoker: Saga 실행 시 내부 커맨드를 실행하는 데 사용
     * - registry: 이벤트 핸들러를 보관하고 찾아주는 역할
     * - handlers: @Component 등으로 등록된 EventHandler<?> 들이 자동 주입됨
     * <p>
     * 결과적으로 이 Bean 하나로 Saga 흐름이 전부 동작하게 됨
     */
    @Bean
    public EventProcessor eventProcessor(
            ScopedCommandInvoker commandInvoker,
            EventHandlerRegistry registry,
            List<EventHandler<?>> handlers
    ) {
        // SagaOrchestrator를 생성하고, 이를 위임하는 EventProcessor 구현체를 생성
        return new SagaEventProcessor(new SagaOrchestrator(commandInvoker, registry, handlers));
    }
}
