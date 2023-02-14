/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "tailormap-api")
public class TailormapConfig {
  private int timeout;

  public int getTimeout() {
    return timeout;
  }

  public TailormapConfig setTimeout(int timeout) {
    this.timeout = timeout;
    return this;
  }
}
