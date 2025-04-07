package com.example.sagacommand.order.application;

public interface OrderEventPublisher {
    void publishEvent(String key, Object event);
}
