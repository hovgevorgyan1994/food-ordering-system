package com.food.ordering.system.order.service.domain.outbox.scheduler.payment;

import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Optional;

import com.food.ordering.system.order.service.domain.PaymentOutboxHelper;
import com.food.ordering.system.order.service.domain.outbox.model.payment.OrderPaymentOutboxMessage;
import com.food.ordering.system.outbox.OutboxScheduler;
import com.food.ordering.system.outbox.OutboxStatus;
import com.food.ordering.system.saga.SagaStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxCleanerScheduler implements OutboxScheduler {

    private final PaymentOutboxHelper paymentOutboxHelper;

    @Override
    @Scheduled(cron = "@midnight")
    public void processOutboxMessage() {
        Optional<List<OrderPaymentOutboxMessage>> outboxMessageResponse =
            paymentOutboxHelper.getPaymentOutboxMessageByOutboxStatusAndSagaStatus(OutboxStatus.COMPLETED,
                                                                                   SagaStatus.SUCCEEDED,
                                                                                   SagaStatus.FAILED,
                                                                                   SagaStatus.COMPENSATED);

        if (outboxMessageResponse.isPresent()) {
            List<OrderPaymentOutboxMessage> outboxMessages = outboxMessageResponse.get();
            log.info("Received {} OrderPaymentOutboxMessage for clean-up. The payloads: {}",
                     outboxMessages.size(),
                     outboxMessages.stream()
                         .map(OrderPaymentOutboxMessage::getPayload)
                         .collect(joining("\n")));

            paymentOutboxHelper.deletePaymentOutboxMessageByOutboxStatusAndSagaStatus(OutboxStatus.COMPLETED,
                                                                                      SagaStatus.SUCCEEDED,
                                                                                      SagaStatus.FAILED,
                                                                                      SagaStatus.COMPENSATED);
            log.info("{} OrderPaymentOutboxMessage deleted!", outboxMessages.size());
        }
    }
}
