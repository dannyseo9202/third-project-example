package com.example.sagacommand.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class PaymentRefundRequestedEvent {
    private UUID orderId;
    private UUID paymentId;
    private LocalDateTime refundedAt;
}
