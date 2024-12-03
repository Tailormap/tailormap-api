/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import io.sentry.quartz.SentryJobListener;
import jakarta.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.tailormap.api.scheduling.DebugLoggingJobListener;
import org.tailormap.api.scheduling.DebugLoggingTriggerListener;

@Configuration
public class SchedulerConfiguration {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final SchedulerFactoryBean schedulerFactoryBean;

  public SchedulerConfiguration(@Autowired SchedulerFactoryBean schedulerFactoryBean) {
    this.schedulerFactoryBean = schedulerFactoryBean;
  }

  @PostConstruct
  public void addListeners() throws SchedulerException {
    schedulerFactoryBean
        .getScheduler()
        .getListenerManager()
        .addJobListener(new SentryJobListener());
    if (logger.isDebugEnabled()) {
      // Add debug logging listeners to the scheduler
      schedulerFactoryBean
          .getScheduler()
          .getListenerManager()
          .addJobListener(new DebugLoggingJobListener());

      schedulerFactoryBean
          .getScheduler()
          .getListenerManager()
          .addTriggerListener(new DebugLoggingTriggerListener());
    }
  }
}
