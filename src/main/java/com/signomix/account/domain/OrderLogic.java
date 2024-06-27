package com.signomix.account.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    // TODO: this should be from configuration
    private static final double VAT_RATE = 0.23; // Stawka VAT 23%
    private static final String VAT_RATE_STR = "23%";

    void onStart(@Observes StartupEvent ev) {
        billingDao = new com.signomix.common.tsdb.BillingDao();
        billingDao.setDatasource(tsDs);
    }

    public String createOrder(User user, Order order) throws Exception {
        if (user == null || order == null)
            throw new IllegalArgumentException("user or order is null");
        if (order.uid == null || !user.uid.equals(order.uid))
            throw new IllegalArgumentException("user and order uid mismatch");

        // values
        order.price = round(getPrice(order), 2);
        if (order.price == null)
            throw new Exception("Invalid order price value");
        order.serviceName = getServiceName(order);
        order.tax = VAT_RATE_STR;
        order.vatValue = round(order.price * VAT_RATE, 2);
        order.total = order.price + order.vatValue;
        Order created = billingDao.createOrder(order);
        if (created == null) {
            throw new Exception("Order creation failed");
        } else {
            logger.info("Order created: " + created.id);
        }
        sendOrderEvent(created.id);
        return created.id;
    }

    private Double round(Double value, int places) throws Exception {
        if (places < 0)
            throw new Exception("Invalid rounding argument");
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private void sendOrderEvent(String orderId) {
        orderEventEmitter.send("created;" + orderId);
    }

    // TODO: this should be from configuration
    private Double getPrice(Order order) {
        if (order.yearly) {
            switch (order.targetType) {
                case 0:
                    return 550.0;
                case 4:
                    return 0.0;
                case 5:
                    return 2200.0;
                default:
                    return null;
            }
        } else {
            switch (order.targetType) {
                case 0:
                    return 50.0;
                case 4:
                    return 0.0;
                case 5:
                    return 200.0;
                default:
                    return null;
            }
        }
    }

    private String getServiceName(Order order) {
        if (order.yearly) {
            switch (order.targetType) {
                case 0:
                    return "Konto Standard w serwisie Signomix. Subskrypcja roczna.";
                case 4:
                    return "Konto Bezpłatne w serwisie Signomix. Subskrypcja roczna.";
                case 5:
                    return "Konto Premium w serwisie Signomix. Subskrypcja roczna.";
                default:
                    return "";
            }
        } else {
            switch (order.targetType) {
                case 0:
                    return "Konto Standard w serwisie Signomix. Subskrypcja miesięczna.";
                case 4:
                    return "Konto Bezpłatne w serwisie Signomix. Subskrypcja miesięczna.";
                case 5:
                    return "Konto Premium w serwisie Signomix. Subskrypcja miesięczna.";
                default:
                    return "";
            }
        }
    }

    private void validate(Order order) {
        if (order == null)
            throw new IllegalArgumentException("order is null");
        if (order.uid == null || order.uid.isEmpty())
            throw new IllegalArgumentException("order uid is null or empty");
    }

}