package com.signomix.account.port.in;

import java.util.List;

import com.signomix.common.User;
import com.signomix.common.db.IotDatabaseException;
import com.signomix.account.domain.UserLogic;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserPort {
    @Inject
    UserLogic userLogic;


    public User getAuthorizing(String uid) throws IotDatabaseException {
        return userLogic.getAuthorizingUser(uid);
    }
    
    public User getUser(User user, String uid) throws IotDatabaseException {
        return userLogic.getUser(user, uid);
    }

    public User checkUser(String login){
        return userLogic.checkUser(login);
    }

    public void createUser(User authorizingUser, User user) throws IotDatabaseException {
        userLogic.createUser(authorizingUser, user);
    }

    public void deleteUser(User authorizingUser, String uid) throws IotDatabaseException {
        userLogic.deleteUser(authorizingUser, uid);
    }

    public void updateUser(User authorizingUser, User user) throws IotDatabaseException {
        userLogic.updateUser(authorizingUser, user);
    }

    public List<User> getUsers(User authorizingUser, int limit, int offset) throws IotDatabaseException {
        return userLogic.getUsers(authorizingUser, limit, offset);
    }
}
