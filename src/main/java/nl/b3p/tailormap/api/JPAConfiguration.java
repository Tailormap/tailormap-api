/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

/**
 * JPA configuration beans.
 *
 * @since 0.1
 */
@Configuration
@EnableJpaRepositories(basePackages = {"nl.b3p.tailormap.api.repository"})
@EntityScan(
        basePackages = {
            "nl.tailormap.viewer.config",
            "nl.tailormap.viewer.config.app",
            "nl.tailormap.viewer.config.metadata",
            "nl.tailormap.viewer.config.security",
            "nl.tailormap.viewer.config.services"
        })
@EnableTransactionManagement
public class JPAConfiguration {
    @Autowired private Environment env;

    private final Log logger = LogFactory.getLog(getClass());

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        final LocalContainerEntityManagerFactoryBean em =
                new LocalContainerEntityManagerFactoryBean();

        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        em.setJpaProperties(additionalProperties());
        em.setPersistenceUnitName("viewer-config-postgresql");
        em.setDataSource(dataSource());

        return em;
    }

    /**
     * This is to override the config of the provided persistence module.
     *
     * @return configured datasource
     */
    @Bean
    public DataSource dataSource() {
        final DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(env.getProperty("spring.datasource.url"));
        dataSource.setDriverClassName(env.getProperty("spring.datasource.driver-class-name"));
        dataSource.setUsername(env.getProperty("spring.datasource.username"));
        dataSource.setPassword(env.getProperty("spring.datasource.password"));

        logger.debug(
                "using database: "
                        + env.getProperty("spring.datasource.url")
                        + " with driver: "
                        + env.getProperty("spring.datasource.driver-class-name"));
        return dataSource;
    }

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

    private final Properties additionalProperties() {
        final Properties hibernateProperties = new Properties();
        //    hibernateProperties.setProperty(
        //        "hibernate.hbm2ddl.auto", env.getProperty("spring.jpa.hibernate.ddl-auto", ""));
        hibernateProperties.setProperty(
                "hibernate.show_sql", env.getProperty("spring.jpa.show-sql", "false"));

        return hibernateProperties;
    }
}
