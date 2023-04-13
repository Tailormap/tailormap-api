/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.base.ddl;

import java.io.File;
import java.lang.invoke.MethodHandles;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Delete the file in the "javax.persistence.schema-generation.scripts.create-target" property
 * because it is appended to instead of overwritten, which we don't want.
 */
@Configuration
@ConfigurationProperties(prefix = "spring.datasource")
@Profile("ddl")
public class DeleteDDLScriptBeforeCreating {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private String url;
  private String username;
  private String password;

  @Value("${spring.jpa.properties.javax.persistence.schema-generation.scripts.create-target}")
  private String target;

  public void setUrl(String url) {
    this.url = url;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setPassword(String password) {
    this.password = password;
  }
  @Bean
  public DataSource getDataSource() {
    return DataSourceBuilder.create().url(url).username(username).password(password).build();
  }

  @PostConstruct
  public void delete() {
    File f = new File(target);
    if (f.exists()) {
      String absolutePath = f.getAbsolutePath();
      if(!f.delete()) {
        logger.info("Could not delete DDL target file {}", absolutePath);
      } else {
        logger.debug("Deleted DDL target file {}", absolutePath);
      }
    }
  }
}
