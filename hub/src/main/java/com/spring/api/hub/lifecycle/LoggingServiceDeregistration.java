package com.spring.api.hub.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link ServiceDeregistration} used while the platform runs without a discovery
 * client. Readiness-based deregistration already covers traffic, so this only records the event.
 *
 * <p>A discovery client (Eureka, Consul) needs no special wiring: publishing another
 * {@link ServiceDeregistration} bean is enough, because the coordinator invokes every
 * implementation it finds.
 */
@Component
public class LoggingServiceDeregistration implements ServiceDeregistration {

    private static final Logger log = LoggerFactory.getLogger(LoggingServiceDeregistration.class);

    @Override
    public void deregister(String reason) {
        log.info("No service registry configured, relying on readiness deregistration only (reason: {})", reason);
    }
}