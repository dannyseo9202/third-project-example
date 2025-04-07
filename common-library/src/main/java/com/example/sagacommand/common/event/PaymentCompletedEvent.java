package com.example.sagacommand.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class PaymentCompletedEvent extends Event {
    private UUID orderId;
    private UUID paymentId;
    private BigDecimal amount;

}
