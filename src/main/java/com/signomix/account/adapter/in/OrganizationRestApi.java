package com.signomix.account.adapter.in;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.Query;

import com.signomix.account.exception.ServiceException;
import com.signomix.account.port.in.AuthPort;
import com.signomix.account.port.in.OrganizationPort;
import com.signomix.account.port.in.UserPort;
import com.signomix.common.Organization;
import com.signomix.common.User;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/api/organization")
public class OrganizationRestApi {

    @Inject
    Logger logger;

    @Inject
    AuthPort authPort;

    @Inject
    OrganizationPort accountPort;

    @Inject
    OrganizationPort organizationPort;

    @Inject
    UserPort userPort;

    @Context
    UriInfo uriInfo;

    @ConfigProperty(name = "signomix.exception.api.unauthorized")
    String unauthorizedException;
    @ConfigProperty(name = "signomix.exception.user.database")
    String userDatabaseException;

    /**
     * Get organization data.
     * 
     * @param token
     * @param uid
     * @return
     */
    @GET
    @Path("/{id}")
    public Response getOrganization(@HeaderParam("Authentication") String token, @PathParam("id") Integer id) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        Organization organization = organizationPort.getOrganization(user, id);
        return Response.ok().entity(organization).build();
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
    public Response addOrganization(@HeaderParam("Authentication") String token, Organization organization) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            organizationPort.addOrganization(user, organization);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    /**
     * Upddate user data.
     * 
     * @param token
     * @param uid
     * @return
     */
    @PUT
    @Path("/{id}")
    public Response updateOrganization(@HeaderParam("Authentication") String token, @PathParam("id") Integer id,
            Organization organization) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if(organization.id != id){
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            organizationPort.updateOrganization(user, organization);
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
    @Path("/{id}")
    public Response deleteOrganization(@HeaderParam("Authentication") String token, @PathParam("id") Integer id) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            organizationPort.deleteOrganization(user, id);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    @GET
    @Path("/users")
    public Response getUsers(
            @HeaderParam("Authentication") String token,
            @QueryParam("organization") Long organizationId,
            @QueryParam("tenant") Integer tenantId,
            @QueryParam("offset") int offset, @QueryParam("limit") int limit,
            @QueryParam("search") String search) {

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
            //users = userPort.getUsers(authorizingUser, organizationId, limit, offset);
            users = userPort.getUsers(authorizingUser, organizationId, tenantId, limit, offset, search);
        } catch (IotDatabaseException e) {
            e.printStackTrace();
            throw new ServiceException(userDatabaseException);
        }
        return Response.ok().entity(users).build();
    }


}
