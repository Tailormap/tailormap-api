/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.repository.GroupRepository;
import nl.b3p.tailormap.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ansi.AnsiPropertySource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@Configuration
@ConditionalOnProperty(
    name = "tailormap-api.security.admin.create-if-not-exists",
    havingValue = "true")
public class AdminAccountCreator {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${tailormap-api.security.admin.username}")
  private String newAdminUsername;

  @Value("${tailormap-api.security.admin.hashed-password}")
  private String newAdminHashedPassword;

  private final UserRepository userRepository;
  private final GroupRepository groupRepository;

  private final PasswordEncoder passwordEncoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public AdminAccountCreator(UserRepository userRepository, GroupRepository groupRepository) {
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void postConstruct() throws IOException {
    logger.trace("Checking whether an admin account exists...");

    InternalAdminAuthentication.setInSecurityContext();
    try {
      if (!userRepository.existsByGroupsNameIn(List.of(Group.ADMIN))) {
        if (isNotBlank(newAdminHashedPassword) && newAdminHashedPassword.startsWith("{bcrypt}")) {
          createAdmin(newAdminUsername, newAdminHashedPassword);
          logger.info(
              "New admin account \"{}\" created with hashed password from environment",
              newAdminUsername);
        } else {
          // Create a new admin account with a random generated password
          String password = UUID.randomUUID().toString();
          createAdmin(newAdminUsername, passwordEncoder.encode(password));
          // Log generated password
          logger.info(getAccountBanner(newAdminUsername, password));
        }
      }
    } finally {
      InternalAdminAuthentication.clearSecurityContextAuthentication();
    }
  }

  private void createAdmin(String username, String hashedPassword) {
    User u = new User().setUsername(username).setPassword(hashedPassword);
    u.getGroups().add(groupRepository.getReferenceById(Group.ADMIN));
    userRepository.saveAndFlush(u);
  }

  private static String getAccountBanner(String username, String password) throws IOException {
    String accountBanner =
        StreamUtils.copyToString(
            new ClassPathResource("account-banner.txt").getInputStream(),
            StandardCharsets.US_ASCII);
    MutablePropertySources sources = new MutablePropertySources();
    sources.addFirst(new AnsiPropertySource("ansi", true));
    sources.addFirst(
        new MapPropertySource(
            "account",
            Map.of(
                "username", username,
                "password", password)));
    return new PropertySourcesPropertyResolver(sources).resolvePlaceholders(accountBanner);
  }
}
