package com.food.ordering.system.order.service.domain.outbox.scheduler.approval;

import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Optional;

import com.food.ordering.system.order.service.domain.outbox.model.approval.OrderApprovalOutboxMessage;
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
public class RestaurantApprovalOutboxCleanerScheduler implements OutboxScheduler {

    private final ApprovalOutboxHelper approvalOutboxHelper;

    @Override
    @Scheduled(cron = "@midnight")
    public void processOutboxMessage() {
        Optional<List<OrderApprovalOutboxMessage>> outboxStatusAndSagaStatus =
            approvalOutboxHelper.getApprovalOutboxMessageByOutboxStatusAndSagaStatus(OutboxStatus.COMPLETED,
                                                                                     SagaStatus.SUCCEEDED,
                                                                                     SagaStatus.FAILED,
                                                                                     SagaStatus.COMPENSATED);
        if (outboxStatusAndSagaStatus.isPresent()) {
            List<OrderApprovalOutboxMessage> orderApprovalOutboxMessages = outboxStatusAndSagaStatus.get();
            log.info("Received {} OrderApprovalOutboxMessage for clean-up. The payloads: {}",
                     orderApprovalOutboxMessages.size(),
                     orderApprovalOutboxMessages.stream()
                         .map(OrderApprovalOutboxMessage::getPayload)
                         .collect(joining("\n")));

            approvalOutboxHelper.deleteApprovalOutboxMessageByOutboxStatusAndSagaStatus(OutboxStatus.COMPLETED,
                                                                                        SagaStatus.SUCCEEDED,
                                                                                        SagaStatus.FAILED,
                                                                                        SagaStatus.COMPENSATED);

            log.info("{} OrderApprovalOutboxMessage deleted!", orderApprovalOutboxMessages.size());
        }
    }
}
