/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.ddl;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.lang.invoke.MethodHandles;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Delete the file in the "javax.persistence.schema-generation.scripts.create-target" property
 * because it is appended to instead of overwritten, which we don't want.
 */
@Configuration
@ConfigurationProperties(prefix = "spring.datasource")
@ConditionalOnProperty(
    name = "spring.jpa.properties.javax.persistence.schema-generation.scripts.delete-first",
    havingValue = "true")
public class DeleteDDLScriptBeforeCreating {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${spring.jpa.properties.javax.persistence.schema-generation.scripts.create-target}")
  private String target;

  private String url;
  private String username;
  private String password;

  public void setUrl(String url) {
    this.url = url;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  /* Override this bean, so we can execute code in @PostConstruct before the entity manager is
   * initialized and write the DDL script.
   */
  @Bean
  public DataSource getDataSource() {
    return DataSourceBuilder.create().url(url).username(username).password(password).build();
  }

  @PostConstruct
  public void delete() {
    if (target != null) {
      File f = new File(target);
      if (f.exists()) {
        String absolutePath = f.getAbsolutePath();
        if (!f.delete()) {
          logger.info("Could not delete DDL target file {}", absolutePath);
        } else {
          logger.debug("Deleted DDL target file {}", absolutePath);
        }
      }
    }
  }
}
