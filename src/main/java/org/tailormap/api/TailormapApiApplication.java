/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.tailormap.api.configuration.base"})
public class TailormapApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(TailormapApiApplication.class, args);
  }
}
