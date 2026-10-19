package com.spring.api.hub.lifecycle;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the graceful shutdown support.
 *
 * <p>Everything here is plain bean wiring so the whole package can move into a shared module
 * when the repository is split into services (Phase 1 of the platform migration) without any
 * service having to reimplement it.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShutdownProperties.class)
public class GracefulShutdownConfiguration {
}