package com.signomix.account.domain;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import com.signomix.account.exception.ServiceException;
import com.signomix.common.HashMaker;
import com.signomix.common.Token;
import com.signomix.common.TokenType;
import com.signomix.common.User;
import com.signomix.common.db.IotDatabaseException;
import com.signomix.common.db.IotDatabaseIface;
import com.signomix.common.db.OrganizationDao;
import com.signomix.common.db.OrganizationDaoIface;
import com.signomix.common.db.UserDao;
import com.signomix.common.db.UserDaoIface;
import com.signomix.common.gui.Dashboard;
import com.signomix.common.iot.Device;
import com.signomix.common.iot.DeviceGroup;
import com.signomix.proprietary.ExtensionPoints;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import io.questdb.client.Sender;
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

    @Inject
    @DataSource("oltp")
    AgroalDataSource iotDataSource;

    UserDaoIface userDao;
    OrganizationDaoIface organizationDao;
    IotDatabaseIface iotDao;

    @ConfigProperty(name = "signomix.exception.api.unauthorized")
    String userNotAuthorizedException;
    @ConfigProperty(name = "signomix.exception.api.required_password")
    String passwordNotSetException;

    @ConfigProperty(name = "signomix.database.type")
    String databaseType;

    @ConfigProperty(name = "signomix.mqtt.field.separator")
    String mqttFieldSeparator;

    @ConfigProperty(name = "questdb.client.config")
    String questDbConfig;

    private long defaultOrganizationId = 1;

    @Inject
    AuthLogic authLogic;

    @Inject
    AccountLogic accountLogic;

    @Inject
    @Channel("user-events")
    Emitter<String> eventEmitter;

    void onStart(@Observes StartupEvent ev) {
        if ("h2".equalsIgnoreCase(databaseType)) {
            userDao = new UserDao();
            userDao.setDatasource(userDataSource);
            organizationDao = new OrganizationDao();
            // iotDao = new IotDatabaseDao();
            // iotDao.setDatasource(deviceDataSource);
            defaultOrganizationId = 0;
        } else if ("postgresql".equalsIgnoreCase(databaseType)) {
            userDao = new com.signomix.common.tsdb.UserDao();
            userDao.setDatasource(userDataSource);
            organizationDao = new com.signomix.common.tsdb.OrganizationDao();
            organizationDao.setDatasource(userDataSource);
            iotDao = new com.signomix.common.tsdb.IotDatabaseDao();
            iotDao.setDatasource(iotDataSource);
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
        //logger.info("getUser: " + uid+ " authorized by: "+authorizingUser.uid);
        User user = userDao.getUser(uid);
        if (isSystemAdmin(authorizingUser)
                || isTenantAdmin(authorizingUser, user.organization, user.getPathRoot())
                || isManagingAdmin(authorizingUser, user.organization)
                || authorizingUser.uid.equals(uid)) {
            /*
            // not needed anymore because of the new getUser method in UserDao 
            Integer numberOfDevices = iotDao.getUserDevicesCount(uid);
            user.devicesCounter = numberOfDevices; */
            return user;
        } else {
            throw new ServiceException(userNotAuthorizedException);
        }
    }

    public String confirmRegistration(String token) {
        User user = authLogic.getUser(authLogic.getUserId(token));
        try {
            user.authStatus = User.IS_ACTIVE;
            user.confirmed = true;
            userDao.updateUser(user);
            sendUserEvent("confirmed", null, user.uid);
            accountLogic.registerAccount(user);
        } catch (IotDatabaseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
        return user.uid;
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

    /*
     * public void createUser(User authorizingUser, User user) {
     * User newUser = new User();
     * // user can update only himself or if he is system admin or organization
     * admin
     * if (null != authorizingUser) {
     * if (!(isOrganizationAdmin(authorizingUser, user.organization)
     * || isSystemAdmin(authorizingUser) || authorizingUser.uid.equals(user.uid))) {
     * throw new ServiceException(userNotAuthorizedException);
     * }
     * }
     * 
     * if (null == authorizingUser) {
     * user.organization = defaultOrganizationId;
     * }
     * 
     * newUser = verifyUser(authorizingUser, user);
     * 
     * newUser.createdAt = System.currentTimeMillis();
     * newUser.password = HashMaker.md5Java(user.password);
     * newUser.authStatus = User.IS_CREATED;
     * newUser.unregisterRequested = false;
     * newUser.confirmed = false;
     * Token token = authLogic.createPermanentToken(newUser, user.uid, 24 * 60);
     * logger.info("token: " + token);
     * newUser.confirmString = token.getToken();
     * try {
     * userDao.addUser(newUser);
     * if (newUser.authStatus == User.IS_CREATED) {
     * sendUserEvent("created", authorizingUser, user.uid);
     * } else if (newUser.authStatus == User.IS_ACTIVE) {
     * sendUserEvent("created_and_activated", authorizingUser, user.uid);
     * } else {
     * sendUserEvent("event", authorizingUser, user.uid);
     * }
     * } catch (IotDatabaseException e) {
     * // authLogic.removePermanentToken(token); //TODO
     * throw new ServiceException(e.getMessage());
     * }
     * }
     */

    private void sendUserEvent(String userEventType, User authorizingUser, String userUid) {
        //logger.info("sendUserEvent: " + userEventType + " " + authorizingUser + " " + userUid);
        String authorizingUserUid = authorizingUser == null ? "" : authorizingUser.uid;
        eventEmitter.send(userEventType + mqttFieldSeparator + authorizingUserUid + mqttFieldSeparator + userUid);
        //logger.info("sendingEvent - after send");
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
        Token token = authLogic.createPermanentToken(user, uid, 1 * 60, TokenType.RESET_PASSWORD, "");
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

    private void checkRightsToModify(User authorizingUser, User user) {
        // Admin hierarchy:
        // System admin (1) > Managing admin (8) > admin /tenant admin (9)) > User
        if (authorizingUser.uid.equalsIgnoreCase(user.uid)) {
            return;
        }
        if (isSystemAdmin(authorizingUser)) {
            return;
        } else if (isSystemAdmin(user)) {
            throw new ServiceException(userNotAuthorizedException);
        }

        // Tenant admin can modify all users from
        if (isTenantAdmin(authorizingUser, user.organization, user.getPathRoot())) {
            return;
        }

        // Managing admin can modify all users from his organization
        if (isManagingAdmin(authorizingUser, user.organization)) {
            return;
        }
        throw new ServiceException(userNotAuthorizedException);
    }

    public void saveUser(User authorizingUser, User user, boolean newUser) throws IotDatabaseException {
        // try {
        if (!newUser) {
            logger.info("updateUser: " + user.uid);
        } else {
            logger.info("createUser: " + user.uid + " " + user.path);
        }
        User actualUser = null;
        if (!newUser) {
            actualUser = userDao.getUser(user.uid);
            if (actualUser == null) {
                throw new ServiceException("User not found");
            }
        }
        if (user.organization == null) {
            user.organization = defaultOrganizationId;
        }
        // User can be updated or created only by himself
        // or by system admin or organization admin or organization tenant admin
        // Otherwise request is rejected.
        // checkRightsToModify(authorizingUser, actualUser);
        if (authorizingUser != null) {
            if (!newUser) {
                if (!(authorizingUser.uid.equalsIgnoreCase(user.uid)
                        || isSystemAdmin(authorizingUser)
                        || isTenantAdmin(authorizingUser, actualUser.organization, actualUser.getPathRoot())
                        || isManagingAdmin(authorizingUser, actualUser.organization))) {
                    throw new ServiceException(userNotAuthorizedException);
                }
            } else {
                if (!(isSystemAdmin(authorizingUser)
                        || isTenantAdmin(authorizingUser, user.organization, user.getPathRoot())
                        || isManagingAdmin(authorizingUser, user.organization))) {
                    throw new ServiceException(userNotAuthorizedException);
                }
            }
        }

        // user modification cannot be done anonymously
        if (authorizingUser == null && !newUser) {
            throw new ServiceException(userNotAuthorizedException);
        }

        User updatedUser = null;
        if (newUser) {
            updatedUser = User.clone(user);
            updatedUser.number = null;
            updatedUser.organization = defaultOrganizationId;
            updatedUser.authStatus = User.IS_CREATED;
            if (user.password != null) {
                updatedUser.password = HashMaker.md5Java(user.password);
            } else {
                // password is required for new user
                throw new ServiceException(passwordNotSetException);
            }
        } else {
            updatedUser = User.clone(actualUser);
            if (user.password != null) {
                updatedUser.password = HashMaker.md5Java(user.password);
            }
        }

        updatedUser.type = getAcceptedType(authorizingUser, user.type);

        if (isSystemAdmin(authorizingUser)) {
            // logger.info("SYSTEM ADMIN");
            // restricted fields:
            // organization
            // tenant
            // path
            // credits
            // services
            // role
            // confirmed
            // authStatus
            if (user.organization != null)
                updatedUser.organization = user.organization;
            if (user.tenant != null)
                updatedUser.tenant = user.tenant;
            if (user.path != null)
                updatedUser.path = user.path;
            if (user.credits != null)
                updatedUser.credits = user.credits;
            if (user.services != null)
                updatedUser.services = user.services;
            if (user.role != null)
                updatedUser.role = user.role;
            if (user.confirmed != null)
                updatedUser.confirmed = user.confirmed;
            if (user.authStatus != null)
                updatedUser.authStatus = user.authStatus;
        } else if (isManagingAdmin(authorizingUser, user.organization)) {
            // logger.info("MANAGING ADMIN");
            // restricted fields:
            // tenant
            // path
            // role
            // confirmed
            // authStatus
            updatedUser.organization = authorizingUser.organization;
            if (user.tenant != null)
                updatedUser.tenant = user.tenant;
            if (user.path != null)
                updatedUser.path = user.path;
            if (user.role != null)
                updatedUser.role = user.role;
            if (user.confirmed != null)
                updatedUser.confirmed = user.confirmed;
            if (user.authStatus != null)
                updatedUser.authStatus = user.authStatus;
        } else if (isTenantAdmin(authorizingUser, user.organization, user.getPathRoot())) {
            // logger.info("TENANT ADMIN");
            // restricted fields:
            // path
            // role
            // confirmed
            // authStatus
            updatedUser.organization = authorizingUser.organization;
            updatedUser.tenant = authorizingUser.tenant;
            if (user.path == null) {
                updatedUser.path = authorizingUser.getPathRoot();
            } else {
                if (user.path.startsWith(authorizingUser.getPathRoot())) {
                    updatedUser.path = user.path;
                } else {
                    updatedUser.path = authorizingUser.getPathRoot();
                }
            }
            if (user.role != null)
                updatedUser.role = user.role;
            if (user.confirmed != null)
                updatedUser.confirmed = user.confirmed;
            if (user.authStatus != null)
                updatedUser.authStatus = user.authStatus;
        } else {
            updatedUser.confirmed = false;
            // not needed
            /*
             * if (authorizingUser != null) {
             * updatedUser.organization = authorizingUser.organization;
             * }
             */
        }

        // Any authorized user can modify the following fields:

        // if (isSystemAdmin(authorizingUser)
        // || isTenantAdmin(authorizingUser, user.organization, user.getPathRoot())
        // || isManagingAdmin(authorizingUser, user.organization)) {
        if (user.unregisterRequested != null)
            updatedUser.unregisterRequested = user.unregisterRequested;
        if (user.confirmString != null)
            updatedUser.confirmString = user.confirmString;
        /*
         * if (user.confirmed != null)
         * updatedUser.confirmed = user.confirmed;
         * if (user.authStatus != null)
         * updatedUser.authStatus = user.authStatus;
         */
        /*
         * if (user.role != null)
         * updatedUser.role = user.role;
         */
        /*
         * if (user.path != null)
         * updatedUser.path = user.path;
         */
        if (user.preferredLanguage != null)
            updatedUser.preferredLanguage = user.preferredLanguage;
        if (user.autologin != null)
            updatedUser.autologin = user.autologin;
        if (user.name != null)
            updatedUser.name = user.name;
        if (user.surname != null)
            updatedUser.surname = user.surname;
        if (user.email != null)
            updatedUser.email = user.email;
        // if (user.phonePrefix != null)
        //     updatedUser.phonePrefix = user.phonePrefix;
        updatedUser.phonePrefix = "+48"; // only polish phone numbers are allowed
        if (user.phone != null)
            updatedUser.phone = user.phone;
        if (user.generalNotificationChannel != null)
            updatedUser.generalNotificationChannel = user.generalNotificationChannel;
        if (user.infoNotificationChannel != null)
            updatedUser.infoNotificationChannel = user.infoNotificationChannel;
        if (user.warningNotificationChannel != null)
            updatedUser.warningNotificationChannel = user.warningNotificationChannel;
        if (user.alertNotificationChannel != null)
            updatedUser.alertNotificationChannel = user.alertNotificationChannel;
        // }

        logger.info("user G-CHANNEL: " + user.generalNotificationChannel);
        logger.info("updatedUser G-CHANNEL: " + updatedUser.generalNotificationChannel);

        // set services and credits
        if(isPaidUserType(updatedUser.type)){
            if(newUser){
                updatedUser.services = User.SERVICE_SMS | User.SERVICE_SUPPORT;
                updatedUser.credits = ExtensionPoints.getStartingPoints();
            }else{
                boolean actuallyPaid = isPaidUserType(actualUser.type);
                // if user was not of paid type and now is - give him starting points
                if(!actuallyPaid){
                    updatedUser.services = User.SERVICE_SMS | User.SERVICE_SUPPORT;
                    updatedUser.credits = ExtensionPoints.getStartingPoints();
                }
            }
        }else{
            if(!newUser){
                boolean actuallyPaid = isPaidUserType(actualUser.type);
                // if user was of paid type and now is not - remove his points
                if(actuallyPaid){
                    updatedUser.services = 0;
                    updatedUser.credits = 0L;
                }
            }
        }

        // finally: save user
        if (newUser) {
            updatedUser.createdAt = System.currentTimeMillis();
            updatedUser.unregisterRequested = false;
            // updatedUser.confirmed = false;
            /*
             * if (updatedUser.services == null) {
             * updatedUser.services = 0;
             * }
             * if (updatedUser.credits == null) {
             * updatedUser.credits = 0L;
             * }
             * if (updatedUser.autologin == null) {
             * updatedUser.autologin = false;
             * }
             */
            Token token = authLogic.createPermanentToken(updatedUser, user.uid, 24 * 60, TokenType.CONFIRM, "");
            updatedUser.confirmString = token.getToken();
            Integer userNumber = userDao.addUser(updatedUser);
            if (updatedUser.tenant != null && updatedUser.tenant > 0) {
                if (userNumber > 0) {
                    // logger.info("addTenantUser with path: " + updatedUser.path);
                    userDao.addTenantUser(updatedUser.organization, updatedUser.tenant, userNumber.longValue(),
                            updatedUser.path);
                } else {
                    // it should not happen
                    throw new ServiceException("User not added");
                }
            }
            if (updatedUser.authStatus == User.IS_CREATED) {
                sendUserEvent("created", authorizingUser, user.uid);
            } else if (updatedUser.authStatus == User.IS_ACTIVE) {
                sendUserEvent("created_and_activated", authorizingUser, user.uid);
                accountLogic.registerAccount(updatedUser);
            } else {
                sendUserEvent("event", authorizingUser, user.uid);
            }
            // register in log db
            updatedUser.number = Long.valueOf(userNumber);
            saveUserRegistration(updatedUser);
        } else {
            userDao.updateUser(updatedUser);
            if (updatedUser.tenant != null && updatedUser.tenant > 0) {
                userDao.updateTenantUser(updatedUser);
            }
            sendUserEvent("updated", authorizingUser, updatedUser.uid);
        }

    }

    private boolean isPaidUserType(int userType) {
        for (int i : User.PAID_TYPES) {
            if (i == userType) {
                return true;
            }
        }
        return false;
    }

    public void deleteUser(User authorizingUser, String uid) throws IotDatabaseException {
        User actualUser = userDao.getUser(uid);
        if (actualUser == null) {
            throw new ServiceException("User not found");
        }
        // user can delete only himself or if he is system admin or organization admin
        if (!(isTenantAdmin(authorizingUser, actualUser.organization, actualUser.getPathRoot())
                || isSystemAdmin(authorizingUser) || authorizingUser.uid.equals(uid))) {
            throw new ServiceException(userNotAuthorizedException);
        }
        userDao.deleteUser(actualUser.number);
        sendUserEvent("removed", authorizingUser, uid);
    }

    public User getAuthorizingUser(String uid) throws IotDatabaseException {
        return userDao.getUser(uid);
    }

    public List<User> getUsers(User authorizingUser, Integer limit, Integer offset, String search) throws IotDatabaseException {
        if (authorizingUser == null) {
            throw new ServiceException(userNotAuthorizedException);
        }
        String[] searchParams={"",""};
        if(search!=null && search.indexOf(":")>0){
            searchParams=search.split(":");
        }
        if (isSystemAdmin(authorizingUser)) {
            return userDao.getUsers(limit, offset, searchParams[0], searchParams[1]);
        } else if (isTenantAdmin(authorizingUser, authorizingUser.organization, authorizingUser.getPathRoot())) {
            return userDao.getOrganizationUsers(authorizingUser.organization, limit, offset, searchParams[0], searchParams[1]);
        } else {
            throw new ServiceException(userNotAuthorizedException);
        }
    }

    @Deprecated
    public List<User> getUsers(User authorizingUser, Long organizationId, Integer limit, Integer offset, String search)
            throws IotDatabaseException {
        if (authorizingUser == null) {
            throw new ServiceException(userNotAuthorizedException);
        }
        String[] searchParams={"",""};
        if(search!=null && search.indexOf(":")>0){
            searchParams=search.split(":");
        }
        if (isSystemAdmin(authorizingUser)) {
            return userDao.getUsers(limit, offset, searchParams[0], searchParams[1]);
        } else if (isTenantAdmin(authorizingUser, authorizingUser.organization, authorizingUser.getPathRoot())) {
            return userDao.getOrganizationUsers(authorizingUser.organization, limit, offset, searchParams[0], searchParams[1]);
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
        String[] searchParams={"",""};
        if(search!=null && search.indexOf(":")>0){
            searchParams=search.split(":");
        }
        logger.info("isSystemAdmin(authorizingUser): " + isSystemAdmin(authorizingUser));
        logger.info("isTenantAdmin(authorizingUser, organizationId): "
                + isTenantAdmin(authorizingUser, organizationId));
        logger.info(
                "isManagingAdmin(authorizingUser, organizationId): "
                        + isManagingAdmin(authorizingUser, organizationId));

        if (organizationId == null && isSystemAdmin(authorizingUser)) {
            logger.info("getOrganizationUsers1");
            users = (ArrayList) userDao.getUsers(limit, offset, searchParams[0], searchParams[1]);
        }

        if (organizationId != null && tenantId == null || tenantId == 0) {
            if (isTenantAdmin(authorizingUser, organizationId) || isSystemAdmin(authorizingUser)
                    || isManagingAdmin(authorizingUser, organizationId)) {
                logger.info("getOrganizationUsers2");
                users = (ArrayList) userDao.getOrganizationUsers(organizationId, limit, offset, searchParams[0], searchParams[1]);
            } else {
                logger.info("getOrganizationUsers2a");
            }
        }

        if (organizationId != null && tenantId != null && tenantId > 0
                && (isSystemAdmin(authorizingUser) || isTenantAdmin(authorizingUser, organizationId)
                        || isManagingAdmin(authorizingUser, organizationId))) {
            logger.info("getOrganizationUsers3");
            users = (ArrayList) userDao.getTenantUsers(tenantId, limit, offset, searchParams[0], searchParams[1]);
        }
        return users;
    }

    public void modifyUserPassword(User authorizingUser, String uid, String password) throws IotDatabaseException {
        User actualUser = userDao.getUser(uid);
        if (actualUser == null) {
            throw new ServiceException("User not found");
        }
        // user can update only himself or if he is system admin or organization admin
        if (!(isTenantAdmin(authorizingUser, actualUser.organization)
                || isSystemAdmin(authorizingUser)
                || isManagingAdmin(authorizingUser, actualUser.organization)
                || authorizingUser.uid.equals(uid))) {
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
     * Checks if user is tenant admin
     * 
     * @param user           user
     * @param organizationId organization id
     * @param tenantRoot     tenant root path (e.g. /tenant1)
     * @return boolean
     */
    public boolean isTenantAdmin(User user, long organizationId, String tenantRoot) {
        if (tenantRoot == null || tenantRoot.isEmpty()) {
            return false;
        }
        return user != null && user.organization == organizationId && user.type == User.ADMIN
                && user.path.startsWith(tenantRoot);
    }

    /**
     * Checks if user is tenant admin
     * 
     * @param user
     * @param organizationId
     * @return
     */
    public boolean isTenantAdmin(User user, long organizationId) {
        logger.info("isTenantAdmin: " + user.organization + " " + organizationId + " " + user.tenant + " "
                + user.getPathRoot());
        return user.organization == organizationId && user.tenant != null && !user.getPathRoot().isEmpty();
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

    public boolean isTenantMember(User user, long organizationId, String tenantRoot) {
        return user != null && user.organization == organizationId && user.path.startsWith(tenantRoot);
    }

    public boolean isTenantMember(User user, long organizationId, int tenantId) {
        return user != null && user.organization == organizationId && user.tenant == tenantId;
    }

    public boolean isManagingAdmin(User user, long organizationId) {
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
        boolean organizationAdmin = isTenantAdmin(userDoingChange, user.organization);

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

    private int getAcceptedType(User authorizunUser, Integer requestedType) {
        if (authorizunUser == null || requestedType == null) {
            return User.FREE;
        }
        if (isSystemAdmin(authorizunUser)) {
            return requestedType;
        } else if (isTenantAdmin(authorizunUser, authorizunUser.organization)) {
            switch (requestedType) {
                case User.ADMIN:
                case User.MANAGING_ADMIN:
                case User.FREE:
                case User.USER:
                    return requestedType;
                default:
                    return User.FREE;
            }
        } else if (isManagingAdmin(authorizunUser, authorizunUser.organization)) {
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

    /**
     * Save user registration event in log database
     * 
     * @param user
     */
    private void saveUserRegistration(User user) {
        try (
            Sender sender = Sender.fromConfig(questDbConfig)) {
            sender.table("users")
                    .symbol("login", user.uid)
                    .longColumn("user_number", user.number)
                    .at(System.currentTimeMillis(), ChronoUnit.MILLIS);
        } catch (Exception e) {
            logger.error("saveLoginEvent: " + e.getMessage());
        }
    }
}
