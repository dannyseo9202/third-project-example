package com.example.sagacommand.order.application.dto;

import java.util.List;
import java.util.UUID;

public record OrderCreation(UUID customerId, List<UUID> items) {
}
