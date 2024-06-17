package com.signomix.account.port.in;

import com.signomix.account.domain.OrderLogic;
import com.signomix.common.User;
import com.signomix.common.billing.Order;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class OrderPort {

    @Inject
    OrderLogic orderLogic;
    
    public String createOrder(User user, Order order) throws Exception {
        return orderLogic.createOrder(user, order);
    }
}
