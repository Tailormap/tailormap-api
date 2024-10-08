/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import jakarta.annotation.PostConstruct;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.tailormap.api.scheduling.LoggingJobListener;

@Configuration
public class SchedulerConfiguration {

  private final SchedulerFactoryBean schedulerFactoryBean;

  public SchedulerConfiguration(@Autowired SchedulerFactoryBean schedulerFactoryBean) {
    this.schedulerFactoryBean = schedulerFactoryBean;
  }

  @PostConstruct
  public void addListeners() throws SchedulerException {
    schedulerFactoryBean
        .getScheduler()
        .getListenerManager()
        .addJobListener(new LoggingJobListener());
  }
}
