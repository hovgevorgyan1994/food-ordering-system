package com.food.ordering.system.payment.service.domain;

import static com.food.ordering.system.domain.DomainConstants.UTC;
import static com.food.ordering.system.domain.valueobject.PaymentStatus.CANCELLED;
import static com.food.ordering.system.domain.valueobject.PaymentStatus.COMPLETED;
import static com.food.ordering.system.domain.valueobject.PaymentStatus.FAILED;
import static com.food.ordering.system.payment.service.domain.valueobject.TransactionType.CREDIT;
import static com.food.ordering.system.payment.service.domain.valueobject.TransactionType.DEBIT;
import static java.lang.String.format;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.food.ordering.system.domain.valueobject.Money;
import com.food.ordering.system.payment.service.domain.entity.CreditEntry;
import com.food.ordering.system.payment.service.domain.entity.CreditHistory;
import com.food.ordering.system.payment.service.domain.entity.Payment;
import com.food.ordering.system.payment.service.domain.event.PaymentCancelledEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentCompletedEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentFailedEvent;
import com.food.ordering.system.payment.service.domain.valueobject.CreditHistoryId;
import com.food.ordering.system.payment.service.domain.valueobject.TransactionType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaymentDomainServiceImpl implements PaymentDomainService {

    @Override
    public PaymentEvent validateAndInitiatePayment(Payment payment,
                                                   CreditEntry creditEntry,
                                                   List<CreditHistory> creditHistories,
                                                   List<String> failureMessages) {
        payment.validatePayment(failureMessages);
        payment.initializePayment();
        validateCreditEntry(payment, creditEntry, failureMessages);
        subtractCreditEntry(payment, creditEntry);
        updateCreditHistory(payment, creditHistories, DEBIT);
        validateCreditHistory(creditEntry, creditHistories, failureMessages);

        if (!failureMessages.isEmpty()) {
            log.info("Payment initiation failed for order id: {}", payment.getOrderId().getValue());
            payment.updateStatus(FAILED);
            return new PaymentFailedEvent(payment, ZonedDateTime.now(ZoneId.of(UTC)), failureMessages);
        }
        log.info("Payment is initiated for order id: {}", payment.getOrderId().getValue());
        payment.updateStatus(COMPLETED);
        return new PaymentCompletedEvent(payment, ZonedDateTime.now(ZoneId.of(UTC)));
    }

    @Override
    public PaymentEvent validateAndCancelPayment(Payment payment,
                                                 CreditEntry creditEntry,
                                                 List<CreditHistory> creditHistories,
                                                 List<String> failureMessages) {
        payment.validatePayment(failureMessages);
        addCreditEntry(payment,creditEntry);
        updateCreditHistory(payment, creditHistories, CREDIT);

        if(failureMessages.isEmpty()){
            log.info("Payment is cancelled for order id: {}", payment.getOrderId().getValue());
            payment.updateStatus(CANCELLED);
            return new PaymentCancelledEvent(payment,ZonedDateTime.now(ZoneId.of(UTC)));
        }
        log.info("Payment cancellation failed for order id: {}", payment.getOrderId().getValue());
        payment.updateStatus(FAILED);
        return new PaymentFailedEvent(payment, ZonedDateTime.now(ZoneId.of(UTC)), failureMessages);
    }

    private void validateCreditEntry(Payment payment, CreditEntry creditEntry, List<String> failureMessages) {
        if (payment.getPrice().isGreaterThan(creditEntry.getTotalCreditAmount())) {
            log.error("Customer with id: {} doesn't have enough credit for payment!",
                      payment.getCustomerId().getValue());
            failureMessages.add(format("Customer with id: %s doesn't have enough credit for payment!",
                                       payment.getCustomerId().getValue()));
        }
    }

    private void subtractCreditEntry(Payment payment, CreditEntry creditEntry) {
        creditEntry.subtractCreditAmount(payment.getPrice());
    }

    private void updateCreditHistory(Payment payment,
                                     List<CreditHistory> creditHistories,
                                     TransactionType transactionType) {
        creditHistories.add(CreditHistory.builder()
                                .creditHistoryId(new CreditHistoryId(UUID.randomUUID()))
                                .customerId(payment.getCustomerId())
                                .amount(payment.getPrice())
                                .transactionType(transactionType)
                                .build());
    }

    private void validateCreditHistory(CreditEntry creditEntry,
                                       List<CreditHistory> creditHistories,
                                       List<String> failureMessages) {
        Money totalCreditHistory = getTotalAmountByTransactionType(creditHistories, CREDIT);

        Money totalDebitHistory = getTotalAmountByTransactionType(creditHistories, DEBIT);

        if (totalDebitHistory.isGreaterThan(totalCreditHistory)) {
            log.error("Customer with id: {} doesn't have enough credit according to credit history!",
                      creditEntry.getCustomerId().getValue());
            failureMessages.add(format("Customer with id: %s doesn't have enough credit according to credit history!",
                                       creditEntry.getCustomerId().getValue()));
        }

        if (!creditEntry.getTotalCreditAmount().equals(totalCreditHistory.subtract(totalDebitHistory))) {
            log.error("Credit history total is not equal to current credit for customer id: {}",
                      creditEntry.getCustomerId().getValue());
            failureMessages.add(format("Credit history total is not equal to current credit for customer id: %s",
                                       creditEntry.getCustomerId().getValue()));
        }
    }

    private static Money getTotalAmountByTransactionType(List<CreditHistory> creditHistories,
                                                         TransactionType transactionType) {
        return creditHistories.stream()
            .filter(creditHistory -> transactionType == creditHistory.getTransactionType())
            .map(CreditHistory::getAmount)
            .reduce(Money.ZERO, Money::add);
    }

    private void addCreditEntry(Payment payment, CreditEntry creditEntry) {
        creditEntry.addCreditAmount(payment.getPrice());
    }
}
