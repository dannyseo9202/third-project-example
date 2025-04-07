package com.example.sagacommand.common.saga;

import com.example.sagacommand.common.event.Event;
import com.example.sagacommand.common.event.EventProcessor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SagaEventProcessor implements EventProcessor {
    private final SagaOrchestrator sagaOrchestrator;

    @Override
    public void processEvent(Event event) {
        sagaOrchestrator.processEvent(event);
    }
}
