package com.signomix.account.port.in;

import java.util.List;

import org.jboss.logging.Logger;

import com.signomix.common.User;
import com.signomix.common.db.IotDatabaseException;
import com.signomix.account.domain.OrganizationLogic;
import com.signomix.account.domain.UserLogic;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserPort {
    @Inject
    Logger logger;
    @Inject
    UserLogic userLogic;
    @Inject
    OrganizationLogic organizationLogic;


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
        //userLogic.createUser(authorizingUser, user);
        userLogic.saveUser(authorizingUser, user, true);
    }

    public void deleteUser(User authorizingUser, String uid) throws IotDatabaseException {
        userLogic.deleteUser(authorizingUser, uid);
    }

    public void updateUser(User authorizingUser, User user) throws IotDatabaseException {
        userLogic.saveUser(authorizingUser, user, false);
    }

    public List<User> getUsers(User authorizingUser, int limit, int offset) throws IotDatabaseException {
        return userLogic.getUsers(authorizingUser, limit, offset);
    }

   /*  public List<User> getUsers(User authorizingUser, Long organization, int limit, int offset) throws IotDatabaseException {
        return userLogic.getUsers(authorizingUser, organization, limit, offset);
    } */

    public List<User> getUsers(User authorizingUser, Long organization, Integer tenant, int limit, int offset, String search) throws IotDatabaseException {
        return userLogic.getUsers(authorizingUser, organization, tenant, limit, offset, search);
    }
}
