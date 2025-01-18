package com.signomix.account.adapter.in;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import com.signomix.account.domain.AccountLogic;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MqttClient {

    @Inject
    Logger logger = Logger.getLogger(MqttClient.class);

    @Inject
    AccountLogic accountLogic;

    @Incoming("sms-sent")
    public void processSmsSent(byte[] bytes) {
        logger.info("SMS sent: " + new String(bytes));
        String msg = new String(bytes);
        String[] parts = msg.split(";");
        String userId = null;
        Long messageId = null;
        try {
            userId = parts[0];
            messageId = Long.parseLong(parts[1]);
        } catch (Exception e) {
            logger.error("Error parsing SMS sent message: " + msg);
            return;
        }
        accountLogic.registerSms(userId, 1, messageId);
    }

}
