/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.tailormap.api.persistence.User;
import org.tailormap.api.repository.UserRepository;

@Service
public class TailormapUserDetailsService implements UserDetailsService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final UserRepository userRepository;

  public TailormapUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findById(username).orElse(null);
    if (user == null) {
      throw new UsernameNotFoundException("User " + username + " not found");
    }
    // This will usually log a {bcrypt}... password unless it was explicitly changed to {noop}...
    // So no plaintext passwords are logged
    logger.trace("Found user: {}, password {}", user.getUsername(), user.getPassword());
    return new TailormapUserDetails(user);
  }
}
