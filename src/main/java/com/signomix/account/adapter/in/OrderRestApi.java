package com.signomix.account.adapter.in;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.signomix.account.exception.ServiceException;
import com.signomix.account.port.in.AuthPort;
import com.signomix.account.port.in.OrderPort;
import com.signomix.account.port.in.UserPort;
import com.signomix.common.User;
import com.signomix.common.billing.Order;
import com.signomix.common.db.IotDatabaseException;

import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

//
@Path("/api/order")
public class OrderRestApi {

    @Inject
    Logger logger;

    @Inject
    AuthPort authPort;
    @Inject
    UserPort userPort;
    @Inject
    OrderPort orderPort;

    @ConfigProperty(name = "signomix.exception.api.unauthorized")
    String unauthorizedException;
    @ConfigProperty(name = "signomix.exception.user.database")
    String userDatabaseException;

    /**
     * Create a new order.
     * 
     * @param token
     * @param order
     * @return order content as JSON
     */
    @POST
    public Response createOrder(
            @HeaderParam("Authentication") String token, Order order) {
        User authorizingUser = null;
        logger.info("createOrder: " + order.toString());
        try {
            try {
                if (token != null && !token.isEmpty()) {
                    authorizingUser = userPort.getAuthorizing(authPort.getUserId(token));
                }
            } catch (IotDatabaseException e) {
                logger.error("getUser: " + e.getMessage());
                e.printStackTrace();
                throw new ServiceException(unauthorizedException);
            } catch (Exception e) {
                logger.error("getUser: " + e.getMessage());
                e.printStackTrace();
                throw new ServiceException(unauthorizedException);
            }
            try {
                return Response.ok(orderPort.createOrder(authorizingUser, order)).build();
            } catch (IotDatabaseException e) {
                e.printStackTrace();
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

}
