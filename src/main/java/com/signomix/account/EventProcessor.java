package com.signomix.account;

import javax.enterprise.context.ApplicationScoped;

import com.signomix.event.IotEvent;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class EventProcessor {
    private static final Logger LOG = Logger.getLogger(EventProcessor.class);

    @Incoming("account")
    public void processEvents(JsonObject obj) {
        IotEvent event= obj.mapTo(IotEvent.class);
        LOG.info("RECEIVED: " + event.toString());
    }
}
