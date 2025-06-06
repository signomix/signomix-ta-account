package com.signomix.account.port.in;

import com.signomix.account.domain.AccountLogic;
import com.signomix.account.domain.UserLogic;
import com.signomix.common.User;
import com.signomix.common.db.IotDatabaseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class AccountPort {

    @Inject
    AccountLogic accountLogic;
    @Inject
    UserLogic userLogic;

    public User getAccount(User user, String uid) {
        // return accountLogic.getAccount(user, uid);
        try {
            return userLogic.getUser(user, uid);
        } catch (IotDatabaseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

    public List<User> getAccounts(User user, int limit, int offset, String searchString) {
        // return accountLogic.getAccounts(user, limit, offset, searchString);
        try {
            return userLogic.getUsers(user, limit, offset, searchString);
        } catch (IotDatabaseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

    public void changePassword(User newUser) throws IotDatabaseException {
        userLogic.modifyPassword(newUser);
    }

    public void registerAccount(User newUser) throws IotDatabaseException {
        userLogic.saveUser(null, newUser, true);
    }

    public void registerAccount(User user, User newUser) throws IotDatabaseException {
        userLogic.saveUser(user, newUser, true);
    }

    public String confirmRegistration(String confirmString) {
        String uid=userLogic.confirmRegistration(confirmString);
        return uid;
    }

    public void requestRemoveAccount(User user, String uid) {
        userLogic.requestRemoveAccount(user);
    }

    public void removeAccount(User user, String uid) {
        userLogic.removeAccount(uid);
    }

    public void changePassword(User user, String uid, String newPassword) throws IotDatabaseException {
        userLogic.modifyUserPassword(user, uid, newPassword);
    }

    public void resetPassword(String uid, String email) {
        userLogic.resetPassword(uid, email);
    }

    public void modifyAccount(User user, String uid, User newUser) throws IotDatabaseException {
        userLogic.saveUser(user, newUser, false);
    }

}
