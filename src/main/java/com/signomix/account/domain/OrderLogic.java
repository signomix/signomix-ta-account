package com.signomix.account.domain;

import com.signomix.common.User;
import com.signomix.common.billing.Order;
import com.signomix.common.db.BillingDaoIface;
import com.signomix.common.db.OrganizationDaoIface;
import com.signomix.proprietary.AccountTypes;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

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

    private AccountTypes accountTypes = new AccountTypes();

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

    /**
     * Send order events.
     * In this version, only two events are sent: created and proforma.
     * In the future, the events will be separated and depending on the order parameters (e.g. payment method).
     * @param orderId
     */
    private void sendOrderEvent(String orderId) {
        orderEventEmitter.send("created;" + orderId);
        orderEventEmitter.send("proforma;" + orderId);
    }

    // TODO: this should be from configuration
    private Double getPrice(Order order) {
        if (order.yearly) {
            return accountTypes.getYearlyPrice(order.targetType, "pln");
        } else {
            return accountTypes.getMonthlyPrice(order.targetType, "pln");
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