/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.tailormap.api.security.events.DefaultAuthenticationFailureEvent;
import org.tailormap.api.security.events.OAuth2AuthenticationFailureEvent;

@Component
public class AuthenticationEvents {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @EventListener
  public void onSuccess(AuthenticationSuccessEvent success) {
    logger.info("Authentication success: {}", success.toString());
  }

  @EventListener
  public void onFailure(AuthenticationSuccessEvent failure) {
    logger.info("Authentication failure: {}", failure.toString());
  }

  @EventListener
  public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
    logger.info("Authentication bad credentials: {}", event.toString());
  }

  @EventListener
  public void onAuthenticationServiceException(AuthenticationServiceException e) {
    logger.info("Authentication service exception: {}", e.toString());
  }

  @EventListener
  public void onOAuth2AuthenticationFailureEvent(OAuth2AuthenticationFailureEvent event) {
    logger.info(
        "OAuth2AuthenticationFailureEvent: {}, {}", event.getException().getMessage(), event);
  }

  @EventListener
  public void onDefaultAuthenticationFailureEvent(DefaultAuthenticationFailureEvent event) {
    logger.info("Default authentication failure: {}", event.toString());
  }
}
