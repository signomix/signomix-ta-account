public class Account {
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
    @PathParam("uid") String uid) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        user = accountPort.getAccount(user, user.uid);
        return Response.ok().entity(user).build();
    }
