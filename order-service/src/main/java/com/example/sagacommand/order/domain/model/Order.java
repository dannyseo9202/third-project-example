package com.example.sagacommand.order.domain.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
public class Order {

    @EmbeddedId
    private OrderId id;

    private UUID customerId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private UUID paymentId;

    private Integer appliedPoints;

    @Embedded
    private Money totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> orderItems = new ArrayList<>();

    public static Order create(OrderId id, UUID customerId, List<OrderItem> orderItems) {
        Order order = new Order();
        order.id = id;
        order.customerId = customerId;
        order.orderItems.addAll(orderItems);
        order.status = OrderStatus.CREATED;
        order.calculateTotalAmount();
        return order;
    }

    public UUID getId() {
        return id.getValue();
    }

    public void markAsPaid() {
        validateStatusTransition(OrderStatus.PAID);
        this.status = OrderStatus.PAID;
    }

    public void markAsPaymentFailed() {
        validateStatusTransition(OrderStatus.PAYMENT_FAILED);
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void markAsInventoryReserved() {
        validateStatusTransition(OrderStatus.INVENTORY_RESERVED);
        this.status = OrderStatus.INVENTORY_RESERVED;
    }

    public void markAsInventoryFailed() {
        validateStatusTransition(OrderStatus.INVENTORY_FAILED);
        this.status = OrderStatus.INVENTORY_FAILED;
    }

    public void markAsCreated() {
        this.status = OrderStatus.CREATED;
    }

    public void cancel() {
        // 취소 가능한 상태인지 확인
        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void updatePaymentId(UUID paymentId){
        this.paymentId = paymentId;
    }

    public void applyPoints(int points) {
        this.appliedPoints = points;
    }

    public void removePoints() {
        this.appliedPoints = null;
    }

    private void calculateTotalAmount() {
        this.totalAmount = orderItems.stream()
                .map(item -> item.getPrice().multiply(item.getQuantity()))
                .reduce(Money.ZERO, Money::add);
    }

    private void validateStatusTransition(OrderStatus newStatus) {
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("취소된 주문은 상태를 변경할 수 없습니다.");
        }
    }

    public enum OrderStatus {
        CREATED, PAID, PAYMENT_FAILED,
        INVENTORY_RESERVED, INVENTORY_FAILED,
        POINTS_APPLIED, POINTS_FAILED,
        COMPLETED, CANCELLED
    }
}
