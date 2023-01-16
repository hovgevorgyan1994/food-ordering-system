package com.food.ordering.system.payment.service.domain.mapper;

import static java.util.UUID.fromString;

import java.util.UUID;

import com.food.ordering.system.domain.valueobject.CustomerId;
import com.food.ordering.system.domain.valueobject.Money;
import com.food.ordering.system.domain.valueobject.OrderId;
import com.food.ordering.system.payment.service.domain.dto.PaymentRequest;
import com.food.ordering.system.payment.service.domain.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentDataMapper {
    public Payment paymentRequestToPayment(PaymentRequest paymentRequest) {
        return Payment.builder()
            .orderId(new OrderId(fromString(paymentRequest.getOrderId())))
            .customerId(new CustomerId(fromString(paymentRequest.getCustomerId())))
            .price(new Money(paymentRequest.getPrice()))
            .build();
    }
}
