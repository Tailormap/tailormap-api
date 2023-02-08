/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"nl.b3p.tailormap.api.configuration.base"})
public class TailormapApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(TailormapApiApplication.class, args);
  }
}
