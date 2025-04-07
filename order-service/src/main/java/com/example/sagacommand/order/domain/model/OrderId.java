package com.example.sagacommand.order.domain.model;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@EqualsAndHashCode
@Access(AccessType.FIELD)
@Embeddable
public class OrderId implements Serializable {
    @Column(name = "\"value\"")
    private UUID value;

    protected OrderId() {

    }

    private OrderId(UUID value) {
        this.value = value;
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}