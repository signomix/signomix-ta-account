package com.signomix.account.domain;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import com.signomix.account.exception.ServiceException;
import com.signomix.common.HashMaker;
import com.signomix.common.Token;
import com.signomix.common.User;
import com.signomix.common.db.IotDatabaseException;
import com.signomix.common.db.UserDao;
import com.signomix.common.db.UserDaoIface;
import com.signomix.common.gui.Dashboard;
import com.signomix.common.iot.Device;
import com.signomix.common.iot.DeviceGroup;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Klasa zawierająca logikę biznesową dotyczącą autoryzacji.
 * 
 * @author Grzegorz
 */
@ApplicationScoped
public class UserLogic {
    @Inject
    Logger logger;

    @Inject
    @DataSource("user")
    AgroalDataSource userDataSource;

    UserDaoIface userDao;

    @ConfigProperty(name = "signomix.exception.api.unauthorized")
    String userNotAuthorizedException;

    @ConfigProperty(name = "signomix.database.type")
    String databaseType;

    @ConfigProperty(name = "signomix.mqtt.field.separator")
    String mqttFieldSeparator;

    private long defaultOrganizationId = 1;

    @Inject
    AuthLogic authLogic;

    @Inject
    @Channel("user-events")
    Emitter<String> eventEmitter;

    void onStart(@Observes StartupEvent ev) {
        if ("h2".equalsIgnoreCase(databaseType)) {
            userDao = new UserDao();
            userDao.setDatasource(userDataSource);
            // iotDao = new IotDatabaseDao();
            // iotDao.setDatasource(deviceDataSource);
            defaultOrganizationId = 0;
        } else if ("postgresql".equalsIgnoreCase(databaseType)) {
            userDao = new com.signomix.common.tsdb.UserDao();
            userDao.setDatasource(userDataSource);
            // iotDao = new com.signomix.common.tsdb.IotDatabaseDao();
            // iotDao.setDatasource(tsDs);
            defaultOrganizationId = 1;
        } else {
            logger.error("Unknown database type: " + databaseType);
        }

    }

    /**
     * Gets user by uid
     * 
     * @param uid
     * @return
     * @throws IotDatabaseException
     */
    public User getUser(User authorizingUser, String uid) throws IotDatabaseException {
        if (authorizingUser == null) {
            throw new ServiceException(userNotAuthorizedException);
        }
        User user = userDao.getUser(uid);
        if (isSystemAdmin(authorizingUser)
                || isOrganizationAdmin(authorizingUser, user.organization)
                || isTenantAdmin(authorizingUser, user.organization)
                || authorizingUser.uid.equals(uid)) {
            return user;
        } else {
            throw new ServiceException(userNotAuthorizedException);
        }
    }

    public String confirmRegistration(String token) {
        return authLogic.getUserId(token);
    }

    public User checkUser(String uid) {
        User user = null;
        try {
            user = userDao.getUser(uid);
            if (user != null) {
                user = new User(); // dummy, for check only
            }
        } catch (IotDatabaseException e) {
        }

        return user;
    }

/*     public void createUser(User authorizingUser, User user) {
        User newUser = new User();
        // user can update only himself or if he is system admin or organization admin
        if (null != authorizingUser) {
            if (!(isOrganizationAdmin(authorizingUser, user.organization)
                    || isSystemAdmin(authorizingUser) || authorizingUser.uid.equals(user.uid))) {
                throw new ServiceException(userNotAuthorizedException);
            }
        }

        if (null == authorizingUser) {
            user.organization = defaultOrganizationId;
        }

        newUser = verifyUser(authorizingUser, user);

        newUser.createdAt = System.currentTimeMillis();
        newUser.password = HashMaker.md5Java(user.password);
        newUser.authStatus = User.IS_CREATED;
        newUser.unregisterRequested = false;
        newUser.confirmed = false;
        Token token = authLogic.createPermanentToken(newUser, user.uid, 24 * 60);
        logger.info("token: " + token);
        newUser.confirmString = token.getToken();
        try {
            userDao.addUser(newUser);
            if (newUser.authStatus == User.IS_CREATED) {
                sendUserEvent("created", authorizingUser, user.uid);
            } else if (newUser.authStatus == User.IS_ACTIVE) {
                sendUserEvent("created_and_activated", authorizingUser, user.uid);
            } else {
                sendUserEvent("event", authorizingUser, user.uid);
            }
        } catch (IotDatabaseException e) {
            // authLogic.removePermanentToken(token); //TODO
            throw new ServiceException(e.getMessage());
        }
    } */

    private void sendUserEvent(String userEventType, User authorizingUser, String userUid) {
        String authorizingUserUid = authorizingUser == null ? "" : authorizingUser.uid;
        eventEmitter.send(userEventType + mqttFieldSeparator + authorizingUserUid + mqttFieldSeparator + userUid);
    }

    public void resetPassword(String uid, String email) {
        User user = null;
        try {
            user = userDao.getUser(uid);
        } catch (IotDatabaseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (user == null) {
            throw new ServiceException("User not found");
        }
        if (!user.email.equals(email)) {
            throw new ServiceException("Email not found");
        }
        Token token = authLogic.createPermanentToken(user, uid, 1 * 60);
        logger.info("token: " + token);
        user.confirmString = token.getToken();
        try {
            userDao.updateUser(user);
            sendUserEvent("password_reset", null, uid);
        } catch (IotDatabaseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new ServiceException("not updated");
        }

    }

    public void saveUser(User authorizingUser, User user, boolean newUser) throws IotDatabaseException {
        try {
            if (!newUser) {
                logger.info("updateUser: " + user.uid);
            } else {
                logger.info("createUser: " + user.uid);
            }
            User actualUser = null;
            if (!newUser) {
                actualUser = userDao.getUser(user.uid);
                if (actualUser == null) {
                    throw new ServiceException("User not found");
                }
            }
            // user can be updated or created only by himself
            // or by system admin or organization admin or organization tenant admin
            if (authorizingUser != null) {
                if (!(authorizingUser.uid.equalsIgnoreCase(user.uid)
                        || isSystemAdmin(authorizingUser)
                        || isOrganizationAdmin(authorizingUser, actualUser.organization)
                        || isTenantAdmin(authorizingUser, actualUser.organization))) {
                    throw new ServiceException(userNotAuthorizedException);
                }
            }
            // user modification cannot be done anonymously
            if (authorizingUser == null && !newUser) {
                throw new ServiceException(userNotAuthorizedException);
            }

            User updatedUser = null;
            if (newUser) {
                updatedUser = User.clone(user);
                updatedUser = User.clone(user);
                updatedUser.number = null;
                updatedUser.organization = defaultOrganizationId;
                updatedUser.authStatus = User.IS_CREATED;
            } else {
                updatedUser = User.clone(actualUser);
            }

            updatedUser.type = getAcceptedType(authorizingUser, user.type);

            if (user.password != null) {
                updatedUser.password = HashMaker.md5Java(user.password);
            }

            if (isSystemAdmin(authorizingUser)) {
                if (user.organization != null)
                    updatedUser.organization = user.organization;
                if (user.credits != null)
                    updatedUser.credits = user.credits;
                if (user.services != null)
                    updatedUser.services = user.services;
            } else {
                if (authorizingUser != null) {
                    updatedUser.organization = authorizingUser.organization;
                }
            }
            if (isSystemAdmin(authorizingUser)
                    || isOrganizationAdmin(authorizingUser, user.organization)
                    || isTenantAdmin(authorizingUser, user.organization)) {
                if (user.unregisterRequested != null)
                    updatedUser.unregisterRequested = user.unregisterRequested;
                if (user.authStatus != null)
                    updatedUser.authStatus = user.authStatus;
                if (user.confirmString != null)
                    updatedUser.confirmString = user.confirmString;
                if (user.confirmed != null)
                    updatedUser.confirmed = user.confirmed;
                if (user.role != null)
                    updatedUser.role = user.role;
                if (user.path != null)
                    updatedUser.path = user.path;
            }

            if (newUser) {
                updatedUser.createdAt = System.currentTimeMillis();
                updatedUser.password = HashMaker.md5Java(user.password);
                updatedUser.unregisterRequested = false;
                updatedUser.confirmed = false;
                Token token = authLogic.createPermanentToken(updatedUser, user.uid, 24 * 60);
                updatedUser.confirmString = token.getToken();
                userDao.addUser(updatedUser);
                if (updatedUser.authStatus == User.IS_CREATED) {
                    sendUserEvent("created", authorizingUser, user.uid);
                } else if (updatedUser.authStatus == User.IS_ACTIVE) {
                    sendUserEvent("created_and_activated", authorizingUser, user.uid);
                } else {
                    sendUserEvent("event", authorizingUser, user.uid);
                }
            } else {
                userDao.updateUser(updatedUser);
                sendUserEvent("updated", authorizingUser, updatedUser.uid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteUser(User authorizingUser, String uid) throws IotDatabaseException {
        User actualUser = userDao.getUser(uid);
        if (actualUser == null) {
            throw new ServiceException("User not found");
        }
        // user can delete only himself or if he is system admin or organization admin
        if (!(isOrganizationAdmin(authorizingUser, actualUser.organization)
                || isSystemAdmin(authorizingUser) || authorizingUser.uid.equals(uid))) {
            throw new ServiceException(userNotAuthorizedException);
        }
        userDao.deleteUser(actualUser.number);
        sendUserEvent("removed", authorizingUser, uid);
    }

    public User getAuthorizingUser(String uid) throws IotDatabaseException {
        return userDao.getUser(uid);
    }

    public List<User> getUsers(User authorizingUser, Integer limit, Integer offset) throws IotDatabaseException {
        if (authorizingUser == null) {
            throw new ServiceException(userNotAuthorizedException);
        }
        if (isSystemAdmin(authorizingUser)) {
            return userDao.getUsers(limit, offset);
        } else if (isOrganizationAdmin(authorizingUser, authorizingUser.organization)) {
            return userDao.getOrganizationUsers(authorizingUser.organization, limit, offset);
        } else {
            throw new ServiceException(userNotAuthorizedException);
        }
    }

    @Deprecated
    public List<User> getUsers(User authorizingUser, Long organizationId, Integer limit, Integer offset)
            throws IotDatabaseException {
        if (authorizingUser == null) {
            throw new ServiceException(userNotAuthorizedException);
        }
        if (isSystemAdmin(authorizingUser)) {
            return userDao.getUsers(limit, offset);
        } else if (isOrganizationAdmin(authorizingUser, authorizingUser.organization)) {
            return userDao.getOrganizationUsers(authorizingUser.organization, limit, offset);
        } else {
            throw new ServiceException(userNotAuthorizedException);
        }
    }

    public List<User> getUsers(User authorizingUser, Long organizationId, Integer tenantId, Integer limit,
            Integer offset, String search) throws IotDatabaseException {
        logger.info("getUsers " + organizationId + " " + tenantId + " " + limit + " " + offset + " " + search);
        ArrayList<User> users = new ArrayList<>();
        if (authorizingUser == null) {
            throw new ServiceException(userNotAuthorizedException);
        }
        String searchArray[];
        if (search == null) {
            search = "";
        }
        searchArray = search.split(":");
        switch (searchArray[0]) {
            case "name":
                search = searchArray[1];
                break;
            case "login":
                search = searchArray[1];
                break;
            default:
                search = "";
                break;
        }
        logger.info("isSystemAdmin(authorizingUser): " + isSystemAdmin(authorizingUser));
        logger.info("isOrganizationAdmin(authorizingUser, organizationId): "
                + isOrganizationAdmin(authorizingUser, organizationId));
        logger.info(
                "isTenantAdmin(authorizingUser, organizationId): " + isTenantAdmin(authorizingUser, organizationId));

        if (organizationId == null && isSystemAdmin(authorizingUser)) {
            logger.info("getOrganizationUsers1");
            users = (ArrayList) userDao.getUsers(limit, offset);
        }

        if (organizationId != null && tenantId == null) {
            if (isOrganizationAdmin(authorizingUser, organizationId) || isSystemAdmin(authorizingUser)
                    || isTenantAdmin(authorizingUser, organizationId)) {
                logger.info("getOrganizationUsers2");
                users = (ArrayList) userDao.getOrganizationUsers(organizationId, limit, offset);
            } else {
                logger.info("getOrganizationUsers2a");
            }
        }

        if (organizationId != null && tenantId != null
                && (isSystemAdmin(authorizingUser) || isOrganizationAdmin(authorizingUser, organizationId)
                        || isTenantAdmin(authorizingUser, organizationId))) {
            logger.info("getOrganizationUsers3");
            users = (ArrayList) userDao.getTenantUsers(tenantId, limit, offset);
        }
        return users;
    }

    public void modifyUserPassword(User authorizingUser, String uid, String password) throws IotDatabaseException {
        User actualUser = userDao.getUser(uid);
        if (actualUser == null) {
            throw new ServiceException("User not found");
        }
        // user can update only himself or if he is system admin or organization admin
        if (!(isOrganizationAdmin(authorizingUser, actualUser.organization)
                || isSystemAdmin(authorizingUser) || authorizingUser.uid.equals(uid))) {
            throw new ServiceException(userNotAuthorizedException);
        }
        userDao.modifyUserPassword(actualUser.number, HashMaker.md5Java(password));
        sendUserEvent("password_changed", authorizingUser, uid);
    }

    public void modifyPassword(User user) throws IotDatabaseException {
        User actualUser = userDao.getUser(user.uid);
        if (actualUser == null) {
            throw new ServiceException("User not found");
        }
        userDao.modifyUserPassword(actualUser.number, HashMaker.md5Java(user.password));
        sendUserEvent("password_changed", actualUser, actualUser.uid);
    }

    /**
     * Register user's request for account removal.
     * 
     * @param login
     */
    public void requestRemoveAccount(User user) {
    }

    /**
     * Removes account and all related data
     * 
     * @param login
     */
    public void removeAccount(String login) {
    }

    /**
     * Checks if user is system admin
     * 
     * @param user
     * @return
     */
    public boolean isSystemAdmin(User user) {
        return user != null && user.type == User.OWNER;
    }

    /**
     * Checks if user is organization admin
     * 
     * @param user
     * @param organizationId
     * @return
     */
    public boolean isOrganizationAdmin(User user, long organizationId) {
        return user != null && user.organization == organizationId && user.type == User.ADMIN;
    }

    /**
     * Checks if user is organization user
     * 
     * @param user
     * @param organizationId
     * @return
     */
    public boolean isOrganizationMember(User user, long organizationId) {
        logger.info("user " + user);
        logger.info("user.organization: " + user.organization);
        logger.info("organizationId: " + organizationId);
        return user != null && user.organization == organizationId;
    }

    public boolean isTenantAdmin(User user, long organizationId) {
        return user != null && user.organization == organizationId && user.type == User.MANAGING_ADMIN;
    }

    /**
     * Checks user access to object (Device, Dashboard)
     * 
     * @param user
     * @param writeAccess
     * @param defaultOrganizationId
     * @param accessedObject
     * @return
     */
    public boolean hasObjectAccess(
            User user,
            boolean writeAccess,
            long defaultOrganizationId,
            Object accessedObject) {
        // Platfor administrator has access to all objects
        if (user.type == User.OWNER) {
            return true;
        }
        String owner = null;
        String team = null;
        String admins = null;
        long organizationId = 0;
        if (accessedObject instanceof Dashboard) {
            Dashboard dashboard = (Dashboard) accessedObject;
            team = dashboard.getTeam();
            admins = dashboard.getAdministrators();
            owner = dashboard.getUserID();
            organizationId = dashboard.getOrganizationId();
        } else if (accessedObject instanceof Device) {
            Device device = (Device) accessedObject;
            team = device.getTeam();
            admins = device.getAdministrators();
            owner = device.getUserID();
            organizationId = device.getOrganizationId();
        } else if (accessedObject instanceof DeviceGroup) {
            DeviceGroup group = (DeviceGroup) accessedObject;
            team = group.getTeam();
            admins = group.getAdministrators();
            owner = group.getUserID();
            organizationId = group.getOrganization();
        } else {
            logger.error("Unknown object type: " + accessedObject.getClass().getName());
            return false;
        }

        // object owner has read/write access
        if (owner.equals(user.uid))
            return true;
        // access depands on organization
        if (user.organization == defaultOrganizationId) {
            if (admins.contains("," + user.uid + ","))
                return true;
            if (!writeAccess) {
                if (team.contains("," + user.uid + ","))
                    return true;
            }
        } else {
            if (!writeAccess) {
                if (user.organization == organizationId)
                    return true;
            } else {
                if (user.organization == organizationId && user.type == User.ADMIN) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    private User verifyUser(User userDoingChange, User user) {
        if (user == null) {
            return null;
        }
        User changedUser = new User();
        boolean systemAdmin = isSystemAdmin(userDoingChange);
        boolean organizationAdmin = isOrganizationAdmin(userDoingChange, user.organization);

        // user fields that can be changed by user itself
        changedUser.uid = user.uid;
        changedUser.name = user.name;
        changedUser.surname = user.surname;
        changedUser.email = user.email;
        changedUser.phonePrefix = user.phonePrefix;
        changedUser.number = user.number;
        changedUser.preferredLanguage = user.preferredLanguage;
        changedUser.autologin = user.autologin;
        changedUser.confirmString = user.confirmString;
        changedUser.confirmed = user.confirmed;
        changedUser.organization = user.organization;

        // user fields that can be changed by organization admin or system admin
        if (organizationAdmin || systemAdmin) {
            if (user.type == User.PRIMARY
                    || user.type == User.FREE
                    || user.type == User.ADMIN
                    || user.type == User.USER) {
                changedUser.type = user.type;
            } else {
                changedUser.type = User.FREE;
            }
            changedUser.authStatus = user.authStatus;
            changedUser.confirmed = user.confirmed;
            changedUser.role = user.role;
            changedUser.unregisterRequested = user.unregisterRequested;
            changedUser.confirmString = user.confirmString;
            if (organizationAdmin) {
                changedUser.organization = userDoingChange.organization;
            }
        }
        // user fields that can be changed by system admin
        if (systemAdmin) {
            changedUser.type = user.type;
            changedUser.organization = user.organization;
            changedUser.credits = user.credits;
            changedUser.services = user.services;
        }
        if (null == changedUser.type) {
            changedUser.type = User.FREE;
        }
        if (null == changedUser.unregisterRequested) {
            changedUser.unregisterRequested = false;
        }
        if (changedUser.authStatus == null) {
            changedUser.authStatus = User.IS_CREATED;
        }
        return changedUser;
    }

    private int getAcceptedType(User authorizunUser, int requestedType) {
        if (authorizunUser == null) {
            return User.FREE;
        }
        if (isSystemAdmin(authorizunUser)) {
            return requestedType;
        } else if (isOrganizationAdmin(authorizunUser, authorizunUser.organization)) {
            switch (requestedType) {
                case User.ADMIN:
                case User.MANAGING_ADMIN:
                case User.FREE:
                case User.USER:
                    return requestedType;
                default:
                    return User.FREE;
            }
        } else if (isTenantAdmin(authorizunUser, authorizunUser.organization)) {
            switch (requestedType) {
                case User.MANAGING_ADMIN:
                case User.FREE:
                case User.USER:
                    return requestedType;
                default:
                    return User.FREE;
            }
        } else {
            return User.FREE;
        }
    }
}
