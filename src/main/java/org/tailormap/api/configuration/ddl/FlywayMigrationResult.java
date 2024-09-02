/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration.ddl;

import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.stereotype.Component;

@Component
public class FlywayMigrationResult {
  private MigrateResult migrateResult;

  public MigrateResult getMigrateResult() {
    return migrateResult;
  }

  public void setMigrateResult(MigrateResult migrateResult) {
    this.migrateResult = migrateResult;
  }
}
