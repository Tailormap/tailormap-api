/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.tailormap.api.configuration.JPAConfiguration;
import org.tailormap.api.security.ActuatorSecurityConfiguration;
import org.tailormap.api.security.ApiSecurityConfiguration;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = {
      JPAConfiguration.class,
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      ActuatorSecurityConfiguration.class,
      ApiSecurityConfiguration.class,
    })
@ComponentScan(basePackages = {"org.tailormap.api"})
@EnableWebMvc
@ActiveProfiles("postgresql")
public @interface PostgresIntegrationTest {}
