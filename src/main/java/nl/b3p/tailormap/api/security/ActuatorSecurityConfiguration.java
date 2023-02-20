/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.security;

import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.repository.GroupRepository;
import nl.b3p.tailormap.api.repository.UserRepository;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@Order(1)
public class ActuatorSecurityConfiguration {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${management.endpoints.web.base-path}")
  private String basePath;

  @Value("${spring.boot.admin.client.instance.metadata.user.password:actuator}")
  private String password;

  private final UserRepository userRepository;
  private final GroupRepository groupRepository;

  private final PasswordEncoder passwordEncoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public ActuatorSecurityConfiguration(
      UserRepository userRepository, GroupRepository groupRepository) {
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  @DependsOn("tailormap-database-initialization")
  public void createActuatorAccount() {
    if (StringUtils.isBlank(password)) {
      return;
    }
    InternalAdminAuthentication.setInSecurityContext();
    try {
      // Use the group/authority name as account name
      User account = userRepository.findById(Group.ACTUATOR).orElse(null);
      if (account != null) {
        logger.info("Actuator account already exists");
      } else {
        String hashedPassword = passwordEncoder.encode(password);
        account = new User().setUsername(Group.ACTUATOR).setPassword(hashedPassword);
        account.getGroups().add(groupRepository.findById(Group.ACTUATOR).get());
        userRepository.save(account);
        logger.info("Created {} account for management", Group.ACTUATOR);
      }
    } finally {
      InternalAdminAuthentication.clearSecurityContextAuthentication();
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
