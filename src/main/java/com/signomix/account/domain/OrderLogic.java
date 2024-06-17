package com.signomix.account.domain;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import com.signomix.common.User;
import com.signomix.common.billing.Order;
import com.signomix.common.db.BillingDaoIface;
import com.signomix.common.db.OrganizationDaoIface;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderLogic {

    @Inject
    Logger logger;

    BillingDaoIface billingDao;

    @ConfigProperty(name = "signomix.exception.api.unauthorized")
    String userNotAuthorizedException;

    @Inject
    @DataSource("oltp")
    AgroalDataSource tsDs;

    OrganizationDaoIface organiaztionDao;

     @Inject
    @Channel("order-events")
    Emitter<String> orderEventEmitter;

    void onStart(@Observes StartupEvent ev) {
        billingDao = new com.signomix.common.tsdb.BillingDao();
        billingDao.setDatasource(tsDs);
    }

    public String createOrder(User user, Order order) throws Exception {
        if(user==null || order==null) throw new IllegalArgumentException("user or order is null");
        if(order.uid==null || !user.uid.equals(order.uid)) throw new IllegalArgumentException("user and order uid mismatch");
        Order created=billingDao.createOrder(order);
        if(created==null) throw new Exception("Order creation failed");
        sendOrderEvent(created.id);
        return created.id;
    }

    private void sendOrderEvent(String orderId) {
        orderEventEmitter.send("created;"+orderId);
    }
    
}