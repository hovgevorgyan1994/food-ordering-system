package com.food.ordering.system.order.service.domain;

import static com.food.ordering.system.saga.SagaStatus.COMPENSATED;
import static com.food.ordering.system.saga.SagaStatus.COMPENSATING;
import static com.food.ordering.system.saga.SagaStatus.PROCESSING;
import static com.food.ordering.system.saga.SagaStatus.STARTED;
import static com.food.ordering.system.saga.SagaStatus.SUCCEEDED;
import static java.lang.String.format;
import static java.util.UUID.fromString;

import java.util.Optional;

import com.food.ordering.system.domain.valueobject.OrderId;
import com.food.ordering.system.domain.valueobject.OrderStatus;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.exception.OrderNotFoundException;
import com.food.ordering.system.order.service.domain.ports.output.repository.OrderRepository;
import com.food.ordering.system.saga.SagaStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaHelper {

    private final OrderRepository orderRepository;

    Order findOrder(String orderId) {
        Optional<Order> optionalOrder = orderRepository.findById(new OrderId(fromString(orderId)));
        if (optionalOrder.isEmpty()) {
            log.error("Could not find order with id: {}", orderId);
            throw new OrderNotFoundException(format("Could not find order with id: %s", orderId));
        }
        return optionalOrder.get();
    }

    void saveOrder(Order order) {
        orderRepository.save(order);
    }

    SagaStatus orderStatusToSagaStatus(OrderStatus orderStatus) {
        return switch (orderStatus) {
            case PAID -> PROCESSING;
            case APPROVED -> SUCCEEDED;
            case CANCELLING -> COMPENSATING;
            case CANCELLED -> COMPENSATED;
            default -> STARTED;
        };
    }
}
