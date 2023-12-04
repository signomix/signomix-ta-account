package com.signomix.account.domain;

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
    
}
