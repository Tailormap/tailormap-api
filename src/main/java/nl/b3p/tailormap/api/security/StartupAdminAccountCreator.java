/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import javax.annotation.PostConstruct;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.repository.UserRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ansi.AnsiPropertySource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StreamUtils;

@Configuration
@Profile("!test")
public class StartupAdminAccountCreator {
  private final Log logger = LogFactory.getLog(getClass());

  @Value("${tailormap-api.new-admin-username:admin}")
  private String newAdminUsername;

  final UserRepository userRepository;

  private final PasswordEncoder passwordEncoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public StartupAdminAccountCreator(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @PostConstruct
  public void postConstruct() throws IOException {
    logger.trace("Checking whether an admin or admin-users account exists...");

    if (!userRepository.existsByGroupsNameIn(Arrays.asList(Group.ADMIN_USERS, Group.ADMIN))) {
      // Create a new admin-users account with a random generated password
      String password = UUID.randomUUID().toString();

      User u =
          new User().setUsername(newAdminUsername).setPassword(passwordEncoder.encode(password));
      u.getGroups().add(new Group().setName(Group.ADMIN_USERS));
      userRepository.saveAndFlush(u);

      // Log generated password
      logger.info(getAccountBanner(newAdminUsername, password));
    }
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
