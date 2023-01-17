package com.food.ordering.system.payment.service.messaging.mapper;

import java.util.UUID;

import com.food.ordering.system.domain.valueobject.PaymentOrderStatus;
import com.food.ordering.system.kafka.order.avro.model.PaymentRequestAvroModel;
import com.food.ordering.system.kafka.order.avro.model.PaymentResponseAvroModel;
import com.food.ordering.system.kafka.order.avro.model.PaymentStatus;
import com.food.ordering.system.payment.service.domain.dto.PaymentRequest;
import com.food.ordering.system.payment.service.domain.event.PaymentCancelledEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentCompletedEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentFailedEvent;
import org.springframework.stereotype.Component;

@Component
public class PaymentMessagingDataMapper {

    public PaymentResponseAvroModel paymentCompletedEventToPaymentResponseAvroModel(
        PaymentCompletedEvent paymentCompletedEvent) {
        return paymentEventToPaymentResponseAvroModel(paymentCompletedEvent);
    }

    public PaymentResponseAvroModel paymentCancelledEventToPaymentResponseAvroModel(
        PaymentCancelledEvent paymentCancelledEvent) {
        return paymentEventToPaymentResponseAvroModel(paymentCancelledEvent);
    }

    public PaymentResponseAvroModel paymentFailedEventToPaymentResponseAvroModel(
        PaymentFailedEvent paymentFailedEvent) {
        return paymentEventToPaymentResponseAvroModel(paymentFailedEvent);
    }

    public PaymentRequest paymentRequestAvroModelToPaymentRequest(PaymentRequestAvroModel paymentResponseAvroModel) {
        return PaymentRequest.builder()
            .id(paymentResponseAvroModel.getId())
            .sagaId(paymentResponseAvroModel.getSagaId())
            .customerId(paymentResponseAvroModel.getCustomerId())
            .orderId(paymentResponseAvroModel.getOrderId())
            .price(paymentResponseAvroModel.getPrice())
            .createdAt(paymentResponseAvroModel.getCreatedAt())
            .paymentOrderStatus(PaymentOrderStatus.valueOf(paymentResponseAvroModel.getPaymentOrderStatus().name()))
            .build();
    }

    private PaymentResponseAvroModel paymentEventToPaymentResponseAvroModel(
        PaymentEvent paymentEvent) {
        return PaymentResponseAvroModel.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setSagaId("")
            .setPaymentId(paymentEvent.getPayment().getId().getValue().toString())
            .setCustomerId(paymentEvent.getPayment().getCustomerId().getValue().toString())
            .setOrderId(paymentEvent.getPayment().getOrderId().getValue().toString())
            .setPrice(paymentEvent.getPayment().getPrice().getAmount())
            .setCreatedAt(paymentEvent.getCreatedAt().toInstant())
            .setPaymentStatus(PaymentStatus.valueOf(paymentEvent.getPayment().getPaymentStatus().name()))
            .setFailureMessages(paymentEvent.getFailureMessages())
            .build();
    }
}
