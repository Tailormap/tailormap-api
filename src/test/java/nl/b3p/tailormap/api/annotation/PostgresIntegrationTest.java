/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import nl.b3p.tailormap.api.configuration.JPAConfiguration;
import nl.b3p.tailormap.api.security.ActuatorSecurityConfiguration;
import nl.b3p.tailormap.api.security.ApiSecurityConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

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
@ComponentScan(basePackages = {"nl.b3p.tailormap.api"})
@EnableWebMvc
@ActiveProfiles("postgresql")
public @interface PostgresIntegrationTest {}
