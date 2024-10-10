/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DebugLoggingTriggerListener implements TriggerListener {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
    // this listener does not veto any job executions
    return false;
  }

  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void triggerFired(Trigger trigger, JobExecutionContext context) {
    if (logger.isDebugEnabled())
      logger.debug("Trigger {}:{} fired.", trigger.getKey().getGroup(), trigger.getKey().getName());
  }

  @Override
  public void triggerMisfired(Trigger trigger) {
    if (logger.isDebugEnabled())
      logger.warn(
          "Trigger {}:{} misfired.", trigger.getKey().getGroup(), trigger.getKey().getName());
  }

  @Override
  public void triggerComplete(
      Trigger trigger,
      JobExecutionContext context,
      Trigger.CompletedExecutionInstruction triggerInstructionCode) {

    if (logger.isDebugEnabled()) {
      context.getJobDetail().getJobDataMap().put("runtime", context.getJobRunTime());

      logger.debug(
          "Trigger {}:{} completed with instruction code {}.",
          trigger.getKey().getGroup(),
          trigger.getKey().getName(),
          triggerInstructionCode);

      logger.debug(
          "Job data map after trigger complete: {}", context.getMergedJobDataMap().getWrappedMap());
    }
  }
}
