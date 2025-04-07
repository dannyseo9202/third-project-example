package com.example.sagacommand.order.application;

import com.example.sagacommand.common.event.*;
import com.example.sagacommand.order.application.dto.OrderCreation;
import com.example.sagacommand.order.application.dto.OrderResult;
import com.example.sagacommand.order.domain.model.Order;
import com.example.sagacommand.order.domain.model.OrderId;
import com.example.sagacommand.order.domain.repository.OrderRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper;
import org.springframework.kafka.support.mapping.Jackson2JavaTypeMapper;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1,
        topics = {"order-events", "payment-events", "inventory-events", "shipping-events"})
class OrderServiceKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Value("${app.kafka.topics.order-events:order-events}")
    private String orderEventsTopic;

    @Value("${app.kafka.topics.payment-events:payment-events}")
    private String paymentEventsTopic;
    @Value("${app.kafka.topics.inventory-events:inventory-events}")
    private String inventoryEventsTopic;

    private Consumer<String, Object> orderEventsConsumer;
    private Producer<String, Object> paymentEventsProducer;
    private Producer<String, Object> inventoryEventsProducer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-order-events-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("com.example");

        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.TYPE_ID);
        typeMapper.setIdClassMapping(Map.of(
                "order-created", OrderCreatedEvent.class,
                "order-cancelled", OrderCancelledEvent.class,
                "payment-completed", PaymentCompletedEvent.class,
                "payment-failed", PaymentFailedEvent.class,
                "inventory-reservation-failed", InventoryReservationFailedEvent.class
        ));
        deserializer.setTypeMapper(typeMapper);

        orderEventsConsumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                deserializer
        ).createConsumer();
        orderEventsConsumer.subscribe(Collections.singletonList(orderEventsTopic));

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", JsonSerializer.class);

        JsonSerializer<Object> serializer = new JsonSerializer<>();
        serializer.setAddTypeInfo(true);

        paymentEventsProducer = new DefaultKafkaProducerFactory<>(
                producerProps,
                new StringSerializer(),
                serializer
        ).createProducer();

        inventoryEventsProducer = new DefaultKafkaProducerFactory<>(
                producerProps,
                new StringSerializer(),
                serializer
        ).createProducer();
    }

    @AfterEach
    void tearDown() {
        if (orderEventsConsumer != null) orderEventsConsumer.close();
        if (paymentEventsProducer != null) paymentEventsProducer.close();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void shouldPublishOrderCreatedEvent_whenOrderIsCreated() {
        OrderCreation request = createSampleOrderRequest();
        OrderResult result = orderService.createOrder(request);

        assertNotNull(result);

        ConsumerRecord<String, Object> singleRecord = KafkaTestUtils.getSingleRecord(
                orderEventsConsumer, orderEventsTopic, Duration.of(10, ChronoUnit.SECONDS));

        assertThat(singleRecord).isNotNull();
        assertThat(singleRecord.value()).isInstanceOf(OrderCreatedEvent.class);

        OrderCreatedEvent event = (OrderCreatedEvent) singleRecord.value();
        assertThat(event.getOrderId()).isEqualTo(result.orderId());
        assertThat(event.getCustomerId()).isEqualTo(request.customerId());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void shouldUpdateOrderStatusToPaid_whenPaymentCompletedEventReceived() {
        OrderCreation request = createSampleOrderRequest();
        OrderResult result = orderService.createOrder(request);
        UUID orderId = result.orderId();

        assertEquals(Order.OrderStatus.CREATED, Order.OrderStatus.valueOf(result.status()));

        PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.of(orderId, orderId, BigDecimal.valueOf(50000));
        paymentEventsProducer.send(new ProducerRecord<>(paymentEventsTopic, orderId.toString(), paymentEvent));
        paymentEventsProducer.flush();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updatedOrder = orderRepository.findById(OrderId.of(orderId)).orElse(null);
            assertNotNull(updatedOrder);
            assertEquals(Order.OrderStatus.PAID, updatedOrder.getStatus());
        });
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void shouldUpdateOrderStatusToPaymentFailed_whenPaymentFailedEventReceived() {
        OrderCreation request = createSampleOrderRequest();
        OrderResult result = orderService.createOrder(request);
        UUID orderId = result.orderId();

        assertEquals(Order.OrderStatus.CREATED, Order.OrderStatus.valueOf(result.status()));

        PaymentFailedEvent paymentEvent = PaymentFailedEvent.of(orderId, "Insufficient funds");
        paymentEventsProducer.send(new ProducerRecord<>(paymentEventsTopic, orderId.toString(), paymentEvent));
        paymentEventsProducer.flush();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updatedOrder = orderRepository.findById(OrderId.of(orderId)).orElse(null);
            assertNotNull(updatedOrder);
            assertEquals(Order.OrderStatus.CANCELLED, updatedOrder.getStatus());
        });
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void shouldUpdateOrderStatusToInventoryFailedAndCancelOrder_whenInventoryReservationFailedEventReceived() {
        OrderCreation request = createSampleOrderRequest();
        OrderResult result = orderService.createOrder(request);
        UUID orderId = result.orderId();

        assertEquals(Order.OrderStatus.CREATED, Order.OrderStatus.valueOf(result.status()));

        InventoryReservationFailedEvent inventoryEvent = InventoryReservationFailedEvent.of(orderId, "Out of stock");
        inventoryEventsProducer.send(new ProducerRecord<>(inventoryEventsTopic, orderId.toString(), inventoryEvent));
        inventoryEventsProducer.flush();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updatedOrder = orderRepository.findById(OrderId.of(orderId)).orElse(null);
            assertNotNull(updatedOrder);
            assertEquals(Order.OrderStatus.CANCELLED, updatedOrder.getStatus());
        });

        ConsumerRecord<String, Object> cancelRecord = null;

        Iterable<ConsumerRecord<String, Object>> records = KafkaTestUtils.getRecords(
                orderEventsConsumer, Duration.of(10, ChronoUnit.SECONDS), 2).records(orderEventsTopic);

        for (ConsumerRecord<String, Object> record : records) {
            if (record.value() instanceof OrderCancelledEvent) {
                cancelRecord = record;
                break;
            }
        }

        assertThat(cancelRecord).isNotNull();
        OrderCancelledEvent cancelEvent = (OrderCancelledEvent) cancelRecord.value();
        assertThat(cancelEvent.getOrderId()).isEqualTo(orderId);
    }

    private OrderCreation createSampleOrderRequest() {
        return new OrderCreation(UUID.randomUUID(), List.of());
    }
}
