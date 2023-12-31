package com.signomix.account.domain;

import java.util.List;

import com.signomix.common.User;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AccountLogic {

    public void registerAccount(String login, String email, String password) {
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

    public void changePassword(String login, String newPassword) {
    }

    public User getAccount(User user, String uid){
        return null;
    }

    public List<User> getAccounts(User user, int imit, int offset, String searchString){
        return null;
    }

    public void registerAccount(User newUser){

    }

    public void registerAccount(User admin, User newUser){

    }
 
    public void  requestRemoveAccount(User user, String uid){

    }

    public void removeAccount(User user, String uid){

    }

    public void changePassword(User user, String uid, String newPassword){

    }            
    
    public void resetPassword(String uid, String email){

    }

    public void modifyAccount(User user, String uid, User newUser){

    }






























































































































































    
}
