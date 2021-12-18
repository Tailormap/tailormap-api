/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.health;

import nl.b3p.tailormap.api.controller.VersionController;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * HealthIndicator for Tailormap. Can be used to retrieve Tailormap specific health statistics from
 * the actuator endpoint.
 *
 * @since 0.1
 * @author mprins
 */
@Component
@ConditionalOnEnabledHealthIndicator("tailormap")
public class TailormapHealthIndicator implements HealthIndicator {
    private final Log logger = LogFactory.getLog(getClass());
    @Autowired private VersionController versionController;

    @Value("${management.health.tailormap.enabled}")
    private boolean healthEnabled = false;

    @Override
    public Health health() {
        Health.Builder health = Health.unknown();

        logger.info("tailormap health indicator " + (healthEnabled ? "enabled" : "disabled"));

        if (healthEnabled) {
            try {
                // TODO check some other statistics
                health.up().withDetails(versionController.getVersion());
            } catch (Exception e) {
                logger.fatal("Error checking Tailormap health. " + e.getMessage());
                logger.debug("Error checking Tailormap health", e);
                health.outOfService().withDetail("message", "Error checking database");
            }
        }

        return health.build();
    }
}
