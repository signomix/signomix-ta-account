package com.signomix.account.adapter.in;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.signomix.account.exception.ServiceException;
import com.signomix.account.port.in.AuthPort;
import com.signomix.account.port.in.UserPort;
import com.signomix.common.Token;
import com.signomix.common.User;
import com.signomix.common.annotation.InboundAdapter;
import com.signomix.common.db.IotDatabaseException;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@InboundAdapter
@Path("/api/user")
public class UserRestAdapter {

    @Inject
    Logger logger;

    @Inject
    AuthPort authPort;
    @Inject
    UserPort userPort;

    @ConfigProperty(name = "signomix.exception.api.unauthorized")
    String unauthorizedException;
    @ConfigProperty(name = "signomix.exception.user.database")
    String userDatabaseException;

    @GET
    public Response getUsers(
            @HeaderParam("Authentication") String token,
            @QueryParam("offset") int offset, @QueryParam("limit") int limit) {

        List<User> users = null;
        User authorizingUser;
        try {
            authorizingUser = userPort.getAuthorizing(authPort.getUserId(token));
        } catch (IotDatabaseException e) {
            logger.error("getUser: " + e.getMessage());
            e.printStackTrace();
            throw new ServiceException(unauthorizedException);
        } catch (Exception e) {
            logger.error("getUser: " + e.getMessage());
            e.printStackTrace();
            throw new ServiceException(unauthorizedException);
        }
        if (authorizingUser == null) {
            throw new ServiceException(unauthorizedException);
        } else {
            logger.info("getUser uid from token: " + authorizingUser.uid);
        }
        try {
            users = userPort.getUsers(authorizingUser, limit, offset);
        } catch (IotDatabaseException e) {
            e.printStackTrace();
            throw new ServiceException(userDatabaseException);
        }
        return Response.ok().entity(users).build();
    }

    @GET
    @Path("/{uid}")
    public Response getUser(
            @HeaderParam("Authentication") String token,
            @PathParam("uid") String uid) {
        logger.info("Handling getUser request for uid token: " + uid + " " + token);

        User user;
        User authorizingUser;
        try {
            authorizingUser = userPort.getAuthorizing(authPort.getUserId(token));
        } catch (IotDatabaseException e) {
            logger.error("getUser: " + e.getMessage());
            e.printStackTrace();
            throw new ServiceException(unauthorizedException);
        } catch (Exception e) {
            logger.error("getUser: " + e.getMessage());
            e.printStackTrace();
            throw new ServiceException(unauthorizedException);
        }
        if (authorizingUser == null) {
            throw new ServiceException(unauthorizedException);
        } else {
            logger.info("getUser uid from token: " + authorizingUser.uid);
        }
        try {
            user = userPort.getUser(authorizingUser, uid);
            user.password = null;
            user.sessionToken = token;
        } catch (IotDatabaseException e) {
            e.printStackTrace();
            throw new ServiceException(userDatabaseException);
        }
        return Response.ok().entity(user).build();
    }

    @PUT
    @Path("/{uid}")
    public Response updateUser(
            @HeaderParam("Authentication") String token,
            @PathParam("uid") String uid, User user) {
        logger.info("Handling getUser request for uid token: " + uid + " " + token);

        User authorizingUser;
        try {
            authorizingUser = userPort.getAuthorizing(authPort.getUserId(token));
        } catch (IotDatabaseException e) {
            logger.error("getUser: " + e.getMessage());
            e.printStackTrace();
            throw new ServiceException(unauthorizedException);
        } catch (Exception e) {
            logger.error("getUser: " + e.getMessage());
            e.printStackTrace();
            throw new ServiceException(unauthorizedException);
        }
        if (authorizingUser == null || !authorizingUser.uid.equals(uid) || !authorizingUser.uid.equals(user.uid)) {
            throw new ServiceException(unauthorizedException);
        }
        try {
            userPort.updateUser(authorizingUser, user);
        } catch (IotDatabaseException e) {
            e.printStackTrace();
            throw new ServiceException(userDatabaseException);
        }
        return Response.ok().build();
    }

    @POST
    public Response createUser(
            @HeaderParam("Authentication") String token, User user) {
        User authorizingUser = null;
        try {
            try {
                // Token tokenObj = authPort.getToken(token);
                // authorizingUser = userPort.getAuthorizing(authPort.getUserId(token));
                // } catch (IotDatabaseException e) {
                // logger.error("getUser: "+e.getMessage());
                // e.printStackTrace();
                // throw new ServiceException(unauthorizedException);
            } catch (Exception e) {
                logger.error("getUser: " + e.getMessage());
                e.printStackTrace();
                throw new ServiceException(unauthorizedException);
            }
            try {
                userPort.createUser(authorizingUser, user);
            } catch (IotDatabaseException e) {
                e.printStackTrace();
                throw new ServiceException(userDatabaseException);
            }
            return Response.ok().build();
        } catch (Exception ex) {
            ex.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    @DELETE
    @Path("/{uid}")
    public Response deleteUser(
            @HeaderParam("Authentication") String token,
            @PathParam("uid") String uid) {
        logger.info("Handling deleteUser request for uid token: " + uid + " " + token);

        User authorizingUser;
        try {
            authorizingUser = userPort.getAuthorizing(authPort.getUserId(token));
        } catch (IotDatabaseException e) {
            logger.error("getUser: " + e.getMessage());
            e.printStackTrace();
            throw new ServiceException(unauthorizedException);
        } catch (Exception e) {
            logger.error("getUser: " + e.getMessage());
            e.printStackTrace();
            throw new ServiceException(unauthorizedException);
        }
        if (authorizingUser == null || !authorizingUser.uid.equals(uid)) {
            throw new ServiceException(unauthorizedException);
        }
        try {
            userPort.deleteUser(authorizingUser, uid);
        } catch (IotDatabaseException e) {
            e.printStackTrace();
            throw new ServiceException(userDatabaseException);
        }
        return Response.ok().build();
    }

    @GET
    @Path("/confirm")
    public Response confirmUser(
            @QueryParam("token") String token) {
        logger.info("Handling confirmUser request with token: " + token);
        /*
         * try {
         * userPort.confirmUser(token);
         * } catch (IotDatabaseException e) {
         * e.printStackTrace();
         * throw new ServiceException(userDatabaseException);
         * }
         */
        return Response.ok().build();
    }

    @GET
    @Path("/resetpassword")
    public Response resetPassword(
            @QueryParam("token") String token) {
        logger.info("Handling confirmUser request with token: " + token);
        /*
         * try {
         * userPort.resetPassword(token);
         * } catch (IotDatabaseException e) {
         * e.printStackTrace();
         * throw new ServiceException(userDatabaseException);
         * }
         */
        return Response.ok().build();
    }

}
