package com.signomix.account.port.in;

import com.signomix.common.User;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AccountPort {

    public User getUser(User user, String uid) {
        return null;
    }

    public List<User> getUsers(User user, int limit, int offset, String searchString) {
        return null;
    }
    
}
