package com.signomix.account.adapter.in;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MqttClient {

    @Inject
    Logger logger = Logger.getLogger(MqttClient.class);

    @Incoming("sms-sent")
    public void processSmsSent(byte[] bytes) {
        logger.info("SMS sent: " + new String(bytes));
        String msg = new String(bytes);
        String[] parts = msg.split(";");
        String userId = parts[0];
    }

}
