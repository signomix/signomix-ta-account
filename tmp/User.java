public class User {
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
            // user.sessionToken = token; //TODO: is this necessary?
            user.sessionToken = null;
        } catch (IotDatabaseException e) {
            e.printStackTrace();
            throw new ServiceException(userDatabaseException);
        }
        return Response.ok().entity(user).build();
    }
}
