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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.tailormap.api.security.events.DefaultAuthenticationFailureEvent;

@Component
public class AuthenticationEventsLogger {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static String getIPAddressInfo(AbstractAuthenticationEvent event) {
    String extraInfo = "";
    // prevent leaking personal data in logs unless trace logging is enabled
    if (logger.isTraceEnabled()
        && event.getAuthentication().getDetails() instanceof WebAuthenticationDetails details) {
      extraInfo = " (IP: %s)".formatted(details.getRemoteAddress());
    }
    return extraInfo;
  }

  @EventListener
  public void onSuccess(AuthenticationSuccessEvent success) {
    String authInfo = "";
    if (success.getSource() instanceof OAuth2LoginAuthenticationToken token) {
      // prevent leaking personal data in logs unless trace logging is enabled
      String userClaims = "";
      if (logger.isTraceEnabled() && token.getPrincipal() instanceof DefaultOidcUser oidcUser) {
        userClaims = ", user claims: " + oidcUser.getUserInfo().getClaims();
      }

      authInfo = "via OIDC registration \"%s\" with client ID %s%s"
          .formatted(
              token.getClientRegistration().getClientName(),
              token.getClientRegistration().getClientId(),
              userClaims);
    }
    if (success.getSource() instanceof UsernamePasswordAuthenticationToken) {
      authInfo = "using username/password";
    }

    logger.info(
        "Authentication successful for user \"{}\"{}, granted authorities: {}, {}",
        // prevent leaking personal data in logs unless trace logging is enabled
        logger.isTraceEnabled() ? success.getAuthentication().getName() : "<username hidden>",
        getIPAddressInfo(success),
        success.getAuthentication().getAuthorities().toString(),
        authInfo);
  }

  @EventListener
  public void onFailure(AbstractAuthenticationFailureEvent failure) {
    String userInfo = "";
    if (failure.getAuthentication().getPrincipal() != null) {
      userInfo = String.format(
          " for user \"%s\"", failure.getAuthentication().getPrincipal());
    }
    logger.info(
        "Authentication failure: {} {}{}",
        failure.getException().getMessage(),
        // in this case logging the "login" is useful/warranted for analysis
        userInfo,
        getIPAddressInfo(failure));
  }

  @EventListener
  public void onDefaultAuthenticationFailureEvent(DefaultAuthenticationFailureEvent event) {
    logger.info("Default authentication failure", event.getException());
  }
}
