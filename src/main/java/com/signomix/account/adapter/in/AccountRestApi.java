package com.signomix.account.adapter.in;

import com.signomix.account.port.in.AccountPort;
import com.signomix.account.port.in.AuthPort;
import com.signomix.account.port.in.UserPort;
import com.signomix.common.User;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

@Path("/api/account")
public class AccountRestApi {

    @Inject
    Logger logger;

    @Inject
    AuthPort authPort;

    @Inject
    AccountPort accountPort;

    @Inject
    UserPort userPort;

    @Context
    UriInfo uriInfo;

    /**
     * Get account data.
     *
     * @param token
     * @param uid
     * @return
     */
    @GET
    @Path("/user/{uid}")
    public Response getUser(
        @HeaderParam("Authentication") String token,
        @HeaderParam("X-signomix-takeover") String takeover,
        @PathParam("uid") String uid
    ) {
        User authorizingUser = authPort.getUser(token);
        if (authorizingUser == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        User user = accountPort.getAccount(authorizingUser, uid);
        if (takeover != null && takeover.equalsIgnoreCase("true")) {
            if (user.role == null) {
                user.role = "takeover";
            } else if (user.role.endsWith(",")) {
                user.role = user.role + "takeover,";
            } else {
                user.role = user.role + ",takeover,";
            }
        }
        return Response.ok().entity(user).build();
    }

    /**
     * Get list of accounts.
     *
     * @param token
     * @param limit
     * @param offset
     * @param searchString
     * @return
     */
    @GET
    public Response getUsers(
        @HeaderParam("Authentication") String token,
        @QueryParam("limit") int limit,
        @QueryParam("offset") int offset,
        @QueryParam("search") String searchString
    ) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        List<User> users = accountPort.getAccounts(
            user,
            limit,
            offset,
            searchString
        );
        return Response.ok().entity(users).build();
    }

    /**
     * Register new account for user. Registering user must be organization admin or
     * service admin.
     *
     * @param token
     * @param newUser
     * @return
     */
    @POST
    @Path("/user")
    public Response registerAccountFor(
        @HeaderParam("Authentication") String token,
        User newUser
    ) {
        logger.info("Registering account for user");
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            accountPort.registerAccount(user, (User) newUser);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    /**
     * Self registration of new account.
     *
     * @param token
     * @param newUser
     * @return
     */
    @POST
    @Path("/register")
    public Response registerAccount(User newUser) {
        User user = userPort.checkUser(newUser.uid);
        if (user != null) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        try {
            accountPort.registerAccount(newUser);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    /*     @POST
    @Path("/password")
    public Response saveNewPassword(@HeaderParam("Authentication") String token, User newUser) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            accountPort.changePassword(newUser);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    } */

    /**
     * Upddate user data.
     *
     * @param token
     * @param uid
     * @return
     */
    @PUT
    @Path("/user/{uid}")
    public Response updateAccount(
        @HeaderParam("Authentication") String token,
        @PathParam("uid") String uid,
        User updatedUser
    ) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            accountPort.modifyAccount(user, uid, updatedUser);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    /**
     * Change user data.
     *
     * @param token
     * @param uid
     * @return
     */
    @DELETE
    @Path("/user/{uid}")
    public Response removeAccount(
        @HeaderParam("Authentication") String token,
        @PathParam("uid") String uid
    ) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            accountPort.removeAccount(user, uid);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    /**
     * Request account removal.
     *
     * @param token
     * @param uid
     * @return
     */
    @PUT
    @Path("/user/{uid}/remove")
    public Response requestRemove(
        @HeaderParam("Authentication") String token,
        @PathParam("uid") String uid
    ) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            accountPort.requestRemoveAccount(user, uid);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    /**
     * Change user password.
     *
     * @param token
     * @param uid
     * @param newPassword
     * @return
     */
    @PUT
    @Path("/user/{uid}/password")
    public Response changePassword(
        @HeaderParam("Authentication") String token,
        @PathParam("uid") String uid,
        User modifiedUser
    ) {
        User user = authPort.getUser(token);
        if (
            user == null
            /*  || !user.uid.equals(uid) || !user.uid.equals(modifiedUser.uid) */
        ) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            accountPort.changePassword(user, uid, modifiedUser.password);
        } catch (Exception e) {
            logger.error(e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    /**
     * Reset user password.
     *
     * @param token
     * @param uid
     * @param email
     * @return
     */
    @POST
    @Path("/user/{uid}/resetpassword")
    public Response resetPassword(
        @PathParam("uid") String uid,
        @QueryParam("email") String email
    ) {
        logger.info("Resetting password for user");
        try {
            accountPort.resetPassword(uid, email);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    /**
     * Confirm user registration.
     *
     * @param token
     * @param uid
     * @param confirmString
     * @return
     */
    @GET
    @Path("/confirm")
    public Response confirmRegistration(
        @QueryParam("key") String confirmString,
        @QueryParam("r") String redirectUrl
    ) {
        String regiseredUser = null;
        try {
            regiseredUser = accountPort.confirmRegistration(confirmString);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (regiseredUser == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        } else {
            try {
                logger.info("Redirecting to: " + redirectUrl);
                URI uri = URI.create(redirectUrl);
                return Response.status(Response.Status.SEE_OTHER)
                    .location(uri)
                    .build();
            } catch (Exception e) {
                e.printStackTrace();
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }
    }
}
