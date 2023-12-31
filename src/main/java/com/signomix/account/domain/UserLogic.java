package com.signomix.account.domain;

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
        //user.password = "***";
        if (isSystemAdmin(authorizingUser)
                || isOrganizationAdmin(authorizingUser, user.organization)
                || authorizingUser.uid.equals(uid)) {
            return user;
        } else {
            throw new ServiceException(userNotAuthorizedException);
        }
    }

    public String confirmRegistration(String token){
        return authLogic.getUserId(token);
    }

    public User checkUser(String uid){
        User user = null;
        try {
            user=userDao.getUser(uid);
            if(user!=null){
                user=new User(); // dummy, for check only       
            }  
        } catch (IotDatabaseException e) {
        }

        return user;
    }

    public void createUser(User authorizingUser, User user) {
        User newUser = new User();
        // user can update only himself or if he is system admin or organization admin
        if (null != authorizingUser) {
            if (!(isOrganizationAdmin(authorizingUser, user.organization)
                    || isSystemAdmin(authorizingUser) || authorizingUser.uid.equals(user.uid))) {
                throw new ServiceException(userNotAuthorizedException);
            }
        }

        if(null==authorizingUser){
            user.organization = defaultOrganizationId;
        }

        newUser = verifyUser(authorizingUser, user);

        newUser.createdAt = System.currentTimeMillis();
        newUser.password = HashMaker.md5Java(user.password);
        newUser.authStatus = User.IS_CREATED;
        newUser.unregisterRequested = false;
        newUser.confirmed = false;
        Token token = authLogic.createPermanentToken(newUser, user.uid, 24*60);
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
            //authLogic.removePermanentToken(token); //TODO
            throw new ServiceException(e.getMessage());
        }
    }

    private void sendUserEvent(String userEventType, User authorizingUser, String userUid) {
        String authorizingUserUid = authorizingUser == null ? "" : authorizingUser.uid;
        eventEmitter.send(userEventType + mqttFieldSeparator + authorizingUserUid + mqttFieldSeparator + userUid);
    }

    public void resetPassword(String uid, String email){
        User user = null;
        try {
            user=userDao.getUser(uid);
        } catch (IotDatabaseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if(user==null){
            throw new ServiceException("User not found");
        }
        if(!user.email.equals(email)){
            throw new ServiceException("Email not found");
        }
        Token token = authLogic.createPermanentToken(user, uid, 24*60);
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

    public void updateUser(User authorizingUser, User user) throws IotDatabaseException {
        User actualUser = userDao.getUser(user.uid);
        if (actualUser == null) {
            throw new ServiceException("User not found");
        }
        user.number = actualUser.number;
        // user.password = HashMaker.md5Java(actualUser.password);
        user.sessionToken = actualUser.sessionToken;
        user.createdAt = actualUser.createdAt;
        // user can update only himself or if he is system admin or organization admin
        if (!(isOrganizationAdmin(authorizingUser, actualUser.organization)
                || isSystemAdmin(authorizingUser) || authorizingUser.uid.equals(user.uid))) {
            throw new ServiceException(userNotAuthorizedException);
        }
        if (!isSystemAdmin(authorizingUser)) {
            user.organization = actualUser.organization;
            user.uid = actualUser.uid;
            user.type = actualUser.type;
            user.credits = actualUser.credits;
            user.services = actualUser.services;
            user.unregisterRequested = actualUser.unregisterRequested;
        } else if (!isOrganizationAdmin(authorizingUser, actualUser.organization)) {
            user.authStatus = actualUser.authStatus;
            user.confirmString = actualUser.confirmString;
            user.confirmed = actualUser.confirmed;
            user.role = actualUser.role;
        }
        userDao.updateUser(user);
        logger.info("sending updated event");
        sendUserEvent("updated", authorizingUser, user.uid);
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

    /**
     * Register user's request for account removal. 
     * @param login
     */
    public void requestRemoveAccount(User user) {
    }

    /**
     * Removes account and all related data
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
            if(organizationAdmin){
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
        if(null==changedUser.unregisterRequested){
            changedUser.unregisterRequested=false;
        }
        if(changedUser.authStatus==null){
            changedUser.authStatus=User.IS_CREATED;
        }
        return changedUser;
    }
}
