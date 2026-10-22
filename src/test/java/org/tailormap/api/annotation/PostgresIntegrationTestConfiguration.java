/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.annotation;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Component scan for {@link PostgresIntegrationTest}.
 *
 * <p>A {@code @ComponentScan} on the test class (or on a meta-annotation of the test class) is only processed when the
 * test class is also annotated with {@code @Import} (directly or as a meta-annotation, such as {@code @EnableWebMvc} or
 * {@code @AutoConfigureMockMvc}), because only then Spring Boot registers the test class as a configuration class. This
 * configuration class is listed explicitly in {@code @SpringBootTest(classes = ...)} so the component scan is always
 * processed.
 */
@TestConfiguration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"org.tailormap.api"})
public class PostgresIntegrationTestConfiguration {}
