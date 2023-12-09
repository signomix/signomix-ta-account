package com.signomix.account.adapter.in;

import java.util.List;

import org.jboss.logging.Logger;

import com.signomix.account.port.in.AccountPort;
import com.signomix.account.port.in.AuthPort;
import com.signomix.common.User;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/api/account")
public class AccountRestApi {

    @Inject
    Logger logger;

    @Inject
    AuthPort authPort;

    @Inject
    AccountPort accountPort;

    @GET
    //@NonBlocking
    public Response getUser(@HeaderParam("Authentication") String token, @QueryParam("limit") int limit,
            @QueryParam("offset") int offset) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        user = accountPort.getUser(user, user.uid);
        return Response.ok().entity(user).build();
    }

    @GET
    public Response getUsers(@HeaderParam("Authentication") String token, @QueryParam("limit") int limit,
            @QueryParam("offset") int offset, @QueryParam("search") String searchString) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        List<User> users = accountPort.getUsers(user, limit, offset, searchString);
        return Response.ok().entity(users).build();
    }

    @POST
    public Response registerAccount(@HeaderParam("Authentication") String token, User newUser) {
        User user = authPort.getUser(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            //accountPort.registerAccount(user, newUser);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

/*     private User buildNewUser(String login, String email, String password,
            String name, String surname, String preferredLanguage, String role, String type,
            String generalNotifications, String infoNotifications, String warningNotifications,
            String alertNotifications,
            boolean withConfirmation, boolean isAdmin, Integer status) {
        return null;
        try {
            String defaultRole = ""; // TODO: from config?
            String defaultType = ""; // TODO: from config?
            User newUser = new User();
            newUser.uid = login;
            newUser.email = email;
            newUser.name = name;
            newUser.surname = surname;
            newUser.preferredLanguage = preferredLanguage;
            newUser.password = HashMaker.md5Java(password);
            if (null != role && isAdmin) {
                newUser.role = role.toUpperCase();
            } else {
                newUser.role = defaultRole;
            }

            if (null != type && isAdmin) {
                switch (type.toUpperCase()) {
                    case "APPLICATION":
                        newUser.type = User.APPLICATION;
                        break;
                    case "OWNER":
                        newUser.type = User.OWNER;
                        break;
                    default:
                        newUser.type = User.FREE;
                        break;
                }
            } else {
                newUser.setType(defaultType);
            }
            newUser.generalNotificationChannel = generalNotifications;
            newUser.infoNotificationChannel = infoNotifications;
            newUser.warningNotificationChannel = warningNotifications;
            newUser.alertNotificationChannel = alertNotifications;
            Long organization = null;
            // validate
            boolean valid = true;
            if (!(newUser.uid != null && !newUser.uid.isEmpty())) {
                valid = false;
            }
            if (!(newUser.email != null && !newUser.email.isEmpty())) {
                valid = false;
            }
            if (!(newUser.password != null && !newUser.password.isEmpty())) {
                valid = false;
            }
            if (organization != null) {
                newUser.organization = organization;
            }
            if (null != status) {
                newUser.authStatus = status;
                if (newUser.getStatus() == User.IS_ACTIVE && admin) {
                    newUser.setConfirmed(true);
                    // Kernel.getInstance().dispatchEvent(new
                    // UserEvent(UserEvent.USER_REG_CONFIRMED, newUser.getNumber()));
                } else if (newUser.getStatus() == User.IS_UNREGISTERING) {
                    newUser.setUnregisterRequested(true);
                    // Kernel.getInstance().dispatchEvent(new UserEvent(UserEvent.USER_DEL_SHEDULED,
                    // newUser.getUid()));
                } else if (newUser.getStatus() == User.IS_CREATED) {
                    // TODO: only OWNER user type is authorized to do this
                    Token token = authAdapter.createPermanentToken(newUser.getUid(), "", true, "");
                    newUser.confirmString = token.getToken();
                }
            } else {
                Token token = authAdapter.createPermanentToken(newUser.getUid(), "", true, "");
                newUser.confirmString = token.getToken();
            }
            if (!valid) {
                throw new Exception("invalid user data");
            }
            // newUser = verifyNotificationsConfig(newUser, telegramNotifier);
            newUser = userAdapter.register(newUser);
            if (withConfirmation) {
                result.setCode(HttpAdapter.SC_ACCEPTED);
                // fire event to send "need confirmation" email
                UserEvent ev = new UserEvent(UserEvent.USER_REGISTERED, newUser.getUid());
                ev.setTimePoint("+5s");
                Kernel.getInstance().dispatchEvent(ev);
            } else {
                userAdapter.confirmRegistration(newUser.getUid());
                result.setCode(HttpAdapter.SC_CREATED);
                // fire event to send "welcome" email
                Kernel.getInstance().dispatchEvent(new UserEvent(UserEvent.USER_REG_CONFIRMED, newUser.getNumber()));
            }
            result.setData(newUser.getUid());
        } catch (UserException e) {
            if (e.getCode() == UserException.USER_ALREADY_EXISTS) {
                result.setCode(HttpAdapter.SC_CONFLICT);
            } else {
                result.setCode(HttpAdapter.SC_BAD_REQUEST);
            }
            result.setMessage(e.getMessage());
        } catch (NullPointerException e) {
            e.printStackTrace();
            result.setCode(HttpAdapter.SC_BAD_REQUEST);
            result.setMessage(e.getMessage());
        } catch (AuthException ex) {
            result.setCode(HttpAdapter.SC_BAD_REQUEST);
            result.setMessage("unable to create token");
        }

    } */

}
