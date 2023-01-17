package com.food.ordering.system.saga.order;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SagaConstants {
    public static final String ORDER_SAGA_NAME = "OrderProcessingSaga";
}
