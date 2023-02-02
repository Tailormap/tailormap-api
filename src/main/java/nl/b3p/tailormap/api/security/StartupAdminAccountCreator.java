/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import java.util.Arrays;
import java.util.UUID;
import nl.b3p.tailormap.api.repository.UserRepository;
import nl.tailormap.viewer.config.security.Group;
import nl.tailormap.viewer.config.security.User;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class StartupAdminAccountCreator {
  private final Log logger = LogFactory.getLog(getClass());

  final UserRepository userRepository;

  private final PasswordEncoder passwordEncoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public StartupAdminAccountCreator(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @EventListener
  public void onApplicationEvent(ContextRefreshedEvent event) {
    logger.trace("Checking whether an Admin or UserAdmin account exists...");

    if (!userRepository.existsByGroupsNameIn(Arrays.asList(Group.USER_ADMIN, Group.ADMIN))) {
      // Create a new UserAdmin account with a random generated password that is logged

      String password = UUID.randomUUID().toString();

      User u = new User();
      u.setUsername("admin");
      u.setPassword(passwordEncoder.encode(password));
      Group g = new Group();
      g.setName(Group.USER_ADMIN);
      u.getGroups().add(g);
      userRepository.saveAndFlush(u);

      logger.info(
          String.format(
              "\n***\n*** Use this account for administrating users:\n***"
                  + "\n*** Username: \033[0;1madmin\033[0m"
                  + "\n*** Password: \033[0;1m%s\033[0m"
                  + "\n***",
              password));
    }
  }
}
