package com.signomix.account.domain;

import java.util.List;

import org.jboss.logging.Logger;

import com.signomix.common.User;
import com.signomix.common.db.BillingDaoIface;
import com.signomix.common.db.IotDatabaseException;
import com.signomix.common.db.UserDaoIface;
import com.signomix.common.tsdb.BillingDao;
import com.signomix.proprietary.ExtensionPoints;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class AccountLogic {

    @Inject
    Logger logger = Logger.getLogger(AccountLogic.class);

    @Inject
    @DataSource("user")
    AgroalDataSource userDataSource;

    @Inject
    @DataSource("billing")
    AgroalDataSource billingDataSource;

    @Inject
    @DataSource("oltp")
    AgroalDataSource iotDataSource;

    UserDaoIface userDao;
    BillingDaoIface billingDao;

    void onStart(@Observes StartupEvent ev) {
        userDao = new com.signomix.common.tsdb.UserDao();
        userDao.setDatasource(userDataSource);
        billingDao = new BillingDao();
        billingDao.setDatasource(billingDataSource);
    }

    public void registerAccount(String login, String email, String password) {
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

    public void changePassword(String login, String newPassword) {
    }

    public User getAccount(User user, String uid) {
        return null;
    }

    public List<User> getAccounts(User user, int imit, int offset, String searchString) {
        return null;
    }

    public void registerAccount(User newUser) {
        if (ExtensionPoints.isControlled()){
            try {
                long points = ExtensionPoints.getStartingPoints();
                billingDao.registerServicePoints(null, points);
            }catch (IotDatabaseException e){
                logger.error(e.getMessage());
            }
        }
    }

    public void registerAccount(User admin, User newUser) {

    }

    public void requestRemoveAccount(User user, String uid) {

    }

    public void removeAccount(User user, String uid) {

    }

    public void changePassword(User user, String uid, String newPassword) {

    }

    public void resetPassword(String uid, String email) {

    }

    /* public void modifyAccount(User user, String uid, User newUser) {
        User actualUser = userDao.getUser(user.uid);
        if (actualUser == null) {
            throw new ServiceException("User not found");
        }
        user.number = actualUser.number;
        user.password = actualUser.password;
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
    }
    */
    public void registerSms(String uidString, int smsCount) {
        long pointsForSMS=0;
        try {
            pointsForSMS = ExtensionPoints.getPointsForSMS();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        try {
            billingDao.registerServicePoints(uidString, smsCount*pointsForSMS);
        } catch (IotDatabaseException e) {
            logger.error(e.getMessage());
        }
    }


}
