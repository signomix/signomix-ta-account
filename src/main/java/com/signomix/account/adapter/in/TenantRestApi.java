package com.signomix.account.adapter.in;

import org.jboss.logging.Logger;

import com.signomix.account.port.in.AuthPort;
import com.signomix.account.port.in.TenantPort;
import com.signomix.account.port.in.UserPort;
import com.signomix.common.Tenant;
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

@Path("/api/tenant")
public class TenantRestApi {

    @Inject
    Logger logger;

    @Inject
    AuthPort authPort;

    @Inject
    TenantPort tenantPort;

    @Inject
    UserPort userPort;

    @Context
    UriInfo uriInfo;


    /**
     * Get organization tenants.
     * @param token
     * @param id
     * @return
     */
    @GET
    public Response getTenants(@HeaderParam("Authentication") String token, 
    @QueryParam("organization") Long organizationId, 
    @QueryParam("limit") int limit, 
    @QueryParam("offset") int offset) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            return Response.ok().entity(tenantPort.getTenants(user, organizationId, limit, offset)).build();
        } catch (IotDatabaseException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get tenant by id.
     * 
     * @param token
     * @param id
     * @return
     */
    @GET
    @Path("/{id}")
    public Response getTenantById(@HeaderParam("Authentication") String token, @PathParam("id") Integer id) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        Tenant tenant = null;
        try{
            tenant = tenantPort.getTenantById(user, id);
        }catch(Exception e){
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().entity(tenant).build();
    }

    /**
     * Get tenant by root.
     * 
     * @param token
     * @param organization
     * @param root
     * @return
     */
    @GET
    public Response getTenantByRoot(@HeaderParam("Authentication") String token, 
    @QueryParam("organization") Long organizationId, @QueryParam("root") String root) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        Tenant tenant = null;
        try{
            tenant = tenantPort.getTenantByRoot(user, organizationId, root);
        }catch(Exception e){
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().entity(tenant).build();
    }

    /**
     * Register new tenant.
     * 
     * @param token
     * @param tenant
     * @return
     */
    @POST
    public Response addTenant(@HeaderParam("Authentication") String token, Tenant tenant) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            tenantPort.addTenant(user, tenant);
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
    public Response updateTenant(@HeaderParam("Authentication") String token, @PathParam("id") Integer id,
            Tenant tenant) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if(tenant.id != id){
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            tenantPort.updateTenant(user, tenant);
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
    public Response removeTenant(@HeaderParam("Authentication") String token, @PathParam("id") Integer id) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            tenantPort.deleteTenant(user, id);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }


}
