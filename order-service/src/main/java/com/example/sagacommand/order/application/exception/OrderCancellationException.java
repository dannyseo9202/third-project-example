package com.example.sagacommand.order.application.exception;

public class OrderCancellationException extends RuntimeException{
    public OrderCancellationException(String message) {
        super(message);
    }
}
