/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import nl.tailormap.viewer.config.app.Application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/** Testcases for {@link ApplicationRepository}. */
@ActiveProfiles("test")
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase
@EnableJpaRepositories(basePackages = {"nl.b3p.tailormap.api.repository"})
@EntityScan(
        basePackages = {
            "nl.tailormap.viewer.config",
            "nl.tailormap.viewer.config.app",
            "nl.tailormap.viewer.config.metadata",
            "nl.tailormap.viewer.config.security",
            "nl.tailormap.viewer.config.services"
        })
@Disabled
class ApplicationRepositoryTest {
    @Autowired private ApplicationRepository applicationRepository;

    @Test
    void should_find_application_by_name() {
        final List<Application> a = applicationRepository.findByName("test");

        assertFalse(a.isEmpty(), "we should have found something");
        assertNotNull(a.get(0), "we should have found something");
        assertEquals("test", a.get(0).getName(), "application name is incorrect");
    }

    @Test
    void should_not_find_application_by_nonexistent_name() {
        final List<Application> a = applicationRepository.findByName("does-not-exist");

        assertTrue(a.isEmpty(), "we should not have found anything");
    }

    @Test
    void should_find_application_by_name_and_version() {
        final Application a = applicationRepository.findByNameAndVersion("test", "1");

        assertNotNull(a, "we should have found something");
        assertEquals("test", a.getName(), "the application name is incorrect");
        assertEquals("1", a.getVersion(), "the application version is incorrect");
    }

    @Test
    void should_not_find_application_by_name_and_nonexistent_version() {
        final Application a = applicationRepository.findByNameAndVersion("test", "no-version");

        assertNull(a, "we should not have found something but found " + a);
    }
}
