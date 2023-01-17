package com.food.ordering.system.order.service.domain.outbox.scheduler.payment;

import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.food.ordering.system.order.service.domain.PaymentOutboxHelper;
import com.food.ordering.system.order.service.domain.outbox.model.payment.OrderPaymentOutboxMessage;
import com.food.ordering.system.order.service.domain.ports.output.message.publisher.payment.PaymentRequestMessagePublisher;
import com.food.ordering.system.outbox.OutboxScheduler;
import com.food.ordering.system.outbox.OutboxStatus;
import com.food.ordering.system.saga.SagaStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxScheduler implements OutboxScheduler {

    private final PaymentOutboxHelper paymentOutboxHelper;
    private final PaymentRequestMessagePublisher paymentRequestMessagePublisher;

    @Override
    @Scheduled(
        fixedDelayString = "${order-service.outbox-scheduler-fixed-rate}",
        initialDelayString = "${order-service.outbox-scheduler-initial-delay}")
    @Transactional
    public void processOutboxMessage() {
        Optional<List<OrderPaymentOutboxMessage>> optionalOutboxMessages =
            paymentOutboxHelper.getPaymentOutboxMessageByOutboxStatusAndSagaStatus(OutboxStatus.STARTED,
                                                                                   SagaStatus.STARTED,
                                                                                   SagaStatus.COMPENSATING);

        if (optionalOutboxMessages.isPresent() && optionalOutboxMessages.get().size() > 0) {
            List<OrderPaymentOutboxMessage> outboxMessages = optionalOutboxMessages.get();
            log.info("Received {} OrderPaymentOutboxMessage with ids: {}, sending to message bus!",
                     outboxMessages.size(),
                     outboxMessages.stream()
                         .map(OrderPaymentOutboxMessage::getId)
                         .map(UUID::toString)
                         .collect(joining(",")));

            outboxMessages.forEach(
                outboxMessage -> paymentRequestMessagePublisher.publish(outboxMessage, this::updateOutboxStatus));

            log.info("{} OrderPaymentOutboxMessage sent to message bus!", outboxMessages.size());

        }
    }

    private void updateOutboxStatus(OrderPaymentOutboxMessage outboxMessage, OutboxStatus outboxStatus) {
        outboxMessage.setOutboxStatus(outboxStatus);
        paymentOutboxHelper.save(outboxMessage);
        log.info("OrderPaymentOutboxMessage is updated with outbox status: {}", outboxStatus.name());
    }
}
