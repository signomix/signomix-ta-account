package com.signomix.account.domain;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.signomix.common.db.BillingDaoIface;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderLogic {

    @Inject
    Logger logger;

    BillingDaoIface billingDao;

    @ConfigProperty(name = "signomix.exception.api.unauthorized")
    String userNotAuthorizedException;
    
}