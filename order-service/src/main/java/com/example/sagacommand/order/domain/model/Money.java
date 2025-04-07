package com.example.sagacommand.order.domain.model;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Embeddable
@Access(AccessType.FIELD)
@EqualsAndHashCode
public class Money {
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private BigDecimal amount; // JPA를 위해 final 제거

    protected Money() {
        // JPA 기본 생성자
    }

    public Money(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getValue() {
        return amount;
    }

    public Money multiply(int multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)));
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }
}
