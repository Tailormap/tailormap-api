/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration beans.
 *
 * @since 0.1
 */
@Configuration
@EnableJpaRepositories(basePackages = {"nl.b3p.tailormap.api.repository"})
@EntityScan(basePackages = {"nl.b3p.tailormap.api.persistence"})
@EnableTransactionManagement
@Profile("!test")
public class JPAConfiguration {

  @Bean
  JpaTransactionManager transactionManager(final EntityManagerFactory entityManagerFactory) {
    final JpaTransactionManager transactionManager = new JpaTransactionManager();
    transactionManager.setEntityManagerFactory(entityManagerFactory);
    return transactionManager;
  }

  @Bean
  public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
    return new PersistenceExceptionTranslationPostProcessor();
  }
}
