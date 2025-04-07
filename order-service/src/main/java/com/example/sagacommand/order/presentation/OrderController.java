package com.example.sagacommand.order.presentation;

import com.example.sagacommand.order.application.OrderService;
import com.example.sagacommand.order.application.dto.OrderResult;
import com.example.sagacommand.order.presentation.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResult> createOrder(@RequestBody  @Valid CreateOrderRequest request) {
        log.info("[Order] Create request received - customerId: {}", request.customerId());
        OrderResult result = orderService.createOrder(request.toApplicationDto());
        log.info("[Order] Created successfully - orderId: {}", result.orderId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<OrderResult> cancelOrder(@PathVariable String orderId) {
        log.info("[Order] Cancel request received - orderId: {}", orderId);
        OrderResult result = orderService.cancelOrder(orderId);
        log.info("[Order] Cancelled successfully - orderId: {}", result.orderId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResult> getOrder(@PathVariable String orderId) {
        log.info("[Order] Get request received - orderId: {}", orderId);
        OrderResult result = orderService.getOrder(orderId);
        log.info("[Order] Retrieved successfully - orderId: {}", result.orderId());
        return ResponseEntity.ok(result);
    }
}

