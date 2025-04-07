package com.example.sagacommand.order.infrastructure.messaging;

import com.example.sagacommand.order.application.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements OrderEventPublisher {
    private static final String ORDER_EVENT_TOPIC = "order-events";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishEvent(String key, Object event) {
        kafkaTemplate.send(ORDER_EVENT_TOPIC, key, event);
    }
}
