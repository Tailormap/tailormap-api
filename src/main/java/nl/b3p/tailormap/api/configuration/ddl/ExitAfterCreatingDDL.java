/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.ddl;

import jakarta.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tailormap-api.exit-after-creating-ddl", havingValue = "true")
public class ExitAfterCreatingDDL {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ApplicationContext appContext;

  public ExitAfterCreatingDDL(ApplicationContext appContext) {
    this.appContext = appContext;
  }

  @PostConstruct
  public void exit() {
    logger.info("Created DDL, exiting Spring application");
    SpringApplication.exit(appContext, () -> 0);
    logger.info("Exiting JVM");
    System.exit(0);
  }
}
