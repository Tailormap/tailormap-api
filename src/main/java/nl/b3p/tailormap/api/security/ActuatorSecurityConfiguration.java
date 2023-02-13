/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.security;

import javax.annotation.PostConstruct;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.repository.UserRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@Order(1)
public class ActuatorSecurityConfiguration {
  private static final Log log = LogFactory.getLog(ActuatorSecurityConfiguration.class);

  @Value("${management.endpoints.web.base-path}")
  private String basePath;

  @Value("${tailormap-api.management.hashed-password}")
  private String hashedPassword;

  final UserRepository userRepository;

  public ActuatorSecurityConfiguration(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @PostConstruct
  @DependsOn("tailormap-database-initialization")
  public void createActuatorAccount() {
    if (StringUtils.isBlank(hashedPassword)) {
      return;
    }
    // Use the group/authority name as account name
    User account = userRepository.findByUsername(Group.ACTUATOR);
    if (account != null) {
      String msg;
      if (hashedPassword.equals(account.getPassword())) {
        msg = "with the hashed password in";
      } else {
        msg = "with a different password from";
      }
      log.info(
          String.format(
              "Actuator account already exists %s the MANAGEMENT_HASHED_ACCOUNT environment variable",
              msg));
    } else {
      if (!hashedPassword.startsWith("{bcrypt}")) {
        log.error("Invalid password hash, must start with {bcrypt}");
      } else {
        account = new User().setUsername(Group.ACTUATOR).setPassword(hashedPassword);
        account.getGroups().add(new Group().setName(Group.ACTUATOR));
        userRepository.save(account);
        log.info("Created " + Group.ACTUATOR + " account with hashed password for management");
      }
    }
  }

  @Bean
  public SecurityFilterChain actuatorFilterChain(
      HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository) throws Exception {
    http.csrf()
        .csrfTokenRepository(csrfTokenRepository)
        .and()
        .securityMatcher(basePath + "/**")
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(basePath + "/health/**")
                    .permitAll()
                    .requestMatchers(basePath + "/info")
                    .permitAll()
                    .requestMatchers(basePath + "/**")
                    .hasAnyAuthority(Group.ADMIN, Group.ACTUATOR))
        .httpBasic();
    return http.build();
  }
}
