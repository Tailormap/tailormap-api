/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import static org.springframework.core.NestedExceptionUtils.getRootCause;

import io.micrometer.core.instrument.Metrics;
import java.lang.invoke.MethodHandles;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.tailormap.api.security.events.DefaultAuthenticationFailureEvent;

@Component
public class AuthenticationEventsLogger {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static @NonNull String getUserInfo(AbstractAuthenticationEvent event, boolean includePersonalInfo) {
    String userInfo = "";
    if (event.getAuthentication().getPrincipal() != null) {
      // prevent leaking personal data in logs unless trace logging is enabled or failures are logged
      userInfo = " for user <username hidden>";
      if (logger.isTraceEnabled() || includePersonalInfo) {
        String ipInfo = "";
        if (event.getAuthentication().getDetails() instanceof WebAuthenticationDetails details) {
          ipInfo = " (IP: %s)".formatted(details.getRemoteAddress());
        }
        userInfo =
            " for user \"%s\"%s".formatted(event.getAuthentication().getPrincipal(), ipInfo);
      }
    }
    return userInfo;
  }

  private record LoginInfo(String authType, String authInfo, String clientId, String clientName) {}

  private static LoginInfo getLoginInfo(AbstractAuthenticationEvent event) {
    String authType = "";
    String clientId = "";
    String clientName = "";
    String authInfo = "";
    if (event.getAuthentication() instanceof OAuth2LoginAuthenticationToken token) {
      authType = "oauth2";
      // prevent leaking personal data in logs unless trace logging is enabled
      String userClaims = "";
      if (logger.isTraceEnabled() && token.getPrincipal() instanceof DefaultOidcUser oidcUser) {
        userClaims = ", user claims: " + oidcUser.getUserInfo().getClaims();
      }
      clientId = token.getClientRegistration().getClientId();
      clientName = token.getClientRegistration().getClientName();
      authInfo = "via OIDC registration \"%s\" with client ID %s%s".formatted(clientName, clientId, userClaims);
    } else if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken) {
      authType = "username_password";
      authInfo = "using username/password";
    }
    return new LoginInfo(authType, authInfo, clientId, clientName);
  }

  private static void incrementMetricsCounter(String name, LoginInfo loginInfo) {
    Metrics.counter(
            name,
            "type",
            loginInfo.authType(),
            "clientId",
            loginInfo.clientId(),
            "clientName",
            loginInfo.clientName())
        .increment();
  }

  @EventListener
  public void onSuccess(AuthenticationSuccessEvent success) {
    LoginInfo loginInfo = getLoginInfo(success);
    logger.info(
        "Authentication successful{}, granted authorities: {}, {}",
        getUserInfo(success, false),
        success.getAuthentication().getAuthorities(),
        loginInfo.authInfo());
    incrementMetricsCounter("tailormap_authentication_success", loginInfo);
  }

  @EventListener
  public void onFailure(AbstractAuthenticationFailureEvent failure) {
    Throwable exception = failure.getException();
    Throwable rootCause = getRootCause(exception);

    if (exception instanceof ProviderNotFoundException
        && failure.getAuthentication() instanceof OAuth2LoginAuthenticationToken) {
      // Ignore, because after this generic exception another event will come with details about the actual OIDC
      // authentication failure
      // Prevents logging and incrementing the Micrometer counter for the authentication failure twice
      logger.trace("Ignoring ProviderNotFoundException for OAuth2LoginAuthenticationToken", exception);
      return;
    }

    String oauth2Message = "";
    if (exception instanceof OAuth2AuthenticationException oauth2Ex && oauth2Ex.getError() != null) {
      oauth2Message = ", error code %s: %s"
          .formatted(
              oauth2Ex.getError().getErrorCode(),
              oauth2Ex.getError().getDescription());
    }

    LoginInfo loginInfo = getLoginInfo(failure);

    logger.warn(
        "Authentication failure{}, {}{}, exception {}: {}{}",
        // in this case logging the "login" is useful/warranted for analysis
        getUserInfo(failure, true),
        loginInfo.authInfo(),
        oauth2Message,
        exception.getClass().getName(),
        exception.getMessage(),
        rootCause != null
            ? ", root cause %s: %s".formatted(rootCause.getClass().getName(), rootCause.getMessage())
            : "");

    logger.debug("Authentication failure stacktrace", exception);

    incrementMetricsCounter("tailormap_authentication_failure", loginInfo);
  }

  @EventListener
  public void onDefaultAuthenticationFailureEvent(DefaultAuthenticationFailureEvent event) {
    logger.info("Default authentication failure", event.getException());
  }
}
