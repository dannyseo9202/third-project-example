package com.example.sagacommand.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class InventoryReservationFailedEvent extends Event {
    private UUID orderId;
    private String reason;
}
