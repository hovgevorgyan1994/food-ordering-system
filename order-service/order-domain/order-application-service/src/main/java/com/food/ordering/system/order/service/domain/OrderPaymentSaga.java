package com.food.ordering.system.order.service.domain;

import static com.food.ordering.system.domain.DomainConstants.UTC;
import static com.food.ordering.system.saga.SagaStatus.COMPENSATING;
import static java.util.UUID.fromString;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import com.food.ordering.system.domain.valueobject.OrderStatus;
import com.food.ordering.system.domain.valueobject.PaymentStatus;
import com.food.ordering.system.order.service.domain.dto.message.PaymentResponse;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.event.OrderPaidEvent;
import com.food.ordering.system.order.service.domain.exception.OrderDomainException;
import com.food.ordering.system.order.service.domain.mapper.OrderDataMapper;
import com.food.ordering.system.order.service.domain.outbox.model.approval.OrderApprovalOutboxMessage;
import com.food.ordering.system.order.service.domain.outbox.model.payment.OrderPaymentOutboxMessage;
import com.food.ordering.system.order.service.domain.outbox.scheduler.approval.ApprovalOutboxHelper;
import com.food.ordering.system.outbox.OutboxStatus;
import com.food.ordering.system.saga.SagaStatus;
import com.food.ordering.system.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Component
@Validated
@Transactional
@RequiredArgsConstructor
public class OrderPaymentSaga implements SagaStep<PaymentResponse> {

    private final OrderSagaHelper orderSagaHelper;
    private final OrderDomainService orderDomainService;
    private final PaymentOutboxHelper paymentOutboxHelper;
    private final ApprovalOutboxHelper approvalOutboxHelper;
    private final OrderDataMapper orderDataMapper;

    @Override
    public void process(PaymentResponse paymentResponse) {
        Optional<OrderPaymentOutboxMessage> optionalOrderPaymentOutboxMessage =
            paymentOutboxHelper.getPaymentOutboxByTypeAndSagaIdAndSagaStatus(
                fromString(paymentResponse.getSagaId()),
                SagaStatus.STARTED);

        if (optionalOrderPaymentOutboxMessage.isEmpty()) {
            log.info("An outbox message with saga id: {} is already processed!", paymentResponse.getSagaId());
            return;
        }

        OrderPaidEvent orderPaidEvent = completePaymentForOrder(paymentResponse);

        SagaStatus sagaStatus = orderSagaHelper.orderStatusToSagaStatus(orderPaidEvent.getOrder().getOrderStatus());

        paymentOutboxHelper.save(getUpdatedOrderPaymentOutboxMessage(optionalOrderPaymentOutboxMessage.get(),
                                                                     orderPaidEvent.getOrder().getOrderStatus(),
                                                                     sagaStatus));

        approvalOutboxHelper.saveApprovalOutboxMessage(
            orderDataMapper.orderPaidEventToOrderApprovalEventPayload(orderPaidEvent),
            orderPaidEvent.getOrder().getOrderStatus(),
            sagaStatus,
            OutboxStatus.STARTED,
            fromString(paymentResponse.getSagaId()));

        log.info("Order with id: {} is paid", orderPaidEvent.getOrder().getId().getValue());
    }

    @Override
    public void rollback(PaymentResponse paymentResponse) {
        Optional<OrderPaymentOutboxMessage> orderPaymentOutboxMessageResponse =
            paymentOutboxHelper.getPaymentOutboxByTypeAndSagaIdAndSagaStatus(fromString(paymentResponse.getSagaId()),
                                                                             getCurrentSagaStatus(
                                                                                 paymentResponse.getPaymentStatus()));
        if (orderPaymentOutboxMessageResponse.isEmpty()) {
            log.info("An outbox message with saga id: {} is already rolled back", paymentResponse.getSagaId());
            return;
        }
        OrderPaymentOutboxMessage orderPaymentOutboxMessage = orderPaymentOutboxMessageResponse.get();
        Order order = rollbackPaymentForOrder(paymentResponse);
        SagaStatus sagaStatus = orderSagaHelper.orderStatusToSagaStatus(order.getOrderStatus());

        paymentOutboxHelper.save(
            getUpdatedOrderPaymentOutboxMessage(orderPaymentOutboxMessage, order.getOrderStatus(), sagaStatus));

        if (paymentResponse.getPaymentStatus() == PaymentStatus.CANCELLED) {
            approvalOutboxHelper.save(
                getUpdatedApprovalOutboxMessage(paymentResponse.getSagaId(), order.getOrderStatus(), sagaStatus));
        }
        log.info("Order with id: {} is cancelled", order.getId().getValue());
    }

    private OrderPaymentOutboxMessage getUpdatedOrderPaymentOutboxMessage(
        OrderPaymentOutboxMessage orderPaymentOutboxMessage,
        OrderStatus orderStatus,
        SagaStatus sagaStatus) {
        orderPaymentOutboxMessage.setProcessedAt(ZonedDateTime.now(ZoneId.of(UTC)));
        orderPaymentOutboxMessage.setOrderStatus(orderStatus);
        orderPaymentOutboxMessage.setSagaStatus(sagaStatus);
        return orderPaymentOutboxMessage;
    }

    private OrderPaidEvent completePaymentForOrder(PaymentResponse paymentResponse) {
        log.info("Completing payment for order with id: {}", paymentResponse.getOrderId());
        Order order = orderSagaHelper.findOrder(paymentResponse.getOrderId());
        OrderPaidEvent orderPaidEvent = orderDomainService.payOrder(order);
        orderSagaHelper.saveOrder(order);
        return orderPaidEvent;
    }

    private SagaStatus[] getCurrentSagaStatus(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case COMPLETED -> new SagaStatus[]{SagaStatus.STARTED};
            case CANCELLED -> new SagaStatus[]{SagaStatus.PROCESSING};
            case FAILED -> new SagaStatus[]{SagaStatus.STARTED, SagaStatus.PROCESSING};
        };
    }

    private Order rollbackPaymentForOrder(PaymentResponse paymentResponse) {
        log.info("Cancelling order with id: {}", paymentResponse.getOrderId());
        Order order = orderSagaHelper.findOrder(paymentResponse.getOrderId());
        orderDomainService.cancelOrder(order, paymentResponse.getFailureMessages());
        orderSagaHelper.saveOrder(order);
        return order;
    }

    private OrderApprovalOutboxMessage getUpdatedApprovalOutboxMessage(String sagaId,
                                                                       OrderStatus orderStatus,
                                                                       SagaStatus sagaStatus) {
        Optional<OrderApprovalOutboxMessage> optionalOrderApprovalOutboxMessage =
            approvalOutboxHelper.getApprovalOutboxMessageBySagaIdAndSagaStatus(fromString(sagaId), COMPENSATING);

        if (optionalOrderApprovalOutboxMessage.isEmpty()) {
            throw new OrderDomainException(
                String.format("Approval outbox message could not be found in %s status!", COMPENSATING.name()));
        }

        OrderApprovalOutboxMessage orderApprovalOutboxMessage = optionalOrderApprovalOutboxMessage.get();
        orderApprovalOutboxMessage.setProcessedAt(ZonedDateTime.now(ZoneId.of(UTC)));
        orderApprovalOutboxMessage.setOrderStatus(orderStatus);
        orderApprovalOutboxMessage.setSagaStatus(sagaStatus);

        return orderApprovalOutboxMessage;
    }
}
