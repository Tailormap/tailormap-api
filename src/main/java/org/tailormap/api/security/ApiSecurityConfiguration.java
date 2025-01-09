/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.Cookie;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.repository.GroupRepository;
import org.tailormap.api.repository.OIDCConfigurationRepository;
import org.tailormap.api.security.events.DefaultAuthenticationFailureEvent;
import org.tailormap.api.security.events.OAuth2AuthenticationFailureEvent;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ApiSecurityConfiguration {
  private static final Logger log = LoggerFactory.getLogger(ApiSecurityConfiguration.class);

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Value("${tailormap-api.admin.base-path}")
  private String adminApiBasePath;

  @Value("${tailormap-api.security.disable-csrf:false}")
  private boolean disableCsrf;

  @Bean
  public CookieCsrfTokenRepository csrfTokenRepository() {
    // Spring CSRF protection requires an X-XSRF-TOKEN header read from the XSRF-TOKEN cookie by
    // JavaScript so set HttpOnly to false. Angular has automatic XSRF protection support:
    // https://angular.io/guide/http#security-xsrf-protection
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    // Allow cross-domain non-GET (unsafe) requests for embedding with an iframe
    csrfTokenRepository.setCookieCustomizer(cookieCustomizer -> {
      // Do not set SameSite=None when testing with HTTP instead of HTTPS
      // Ideally use HttpServletRequest.isSecure(), but look at built cookie for now...
      if (cookieCustomizer.build().isSecure()) {
        cookieCustomizer.sameSite(Cookie.SameSite.NONE.attributeValue());
      }
    });
    return csrfTokenRepository;
  }

  @Bean
  public AuthenticationEventPublisher authenticationEventPublisher(
      ApplicationEventPublisher applicationEventPublisher) {
    DefaultAuthenticationEventPublisher authenticationEventPublisher =
        new DefaultAuthenticationEventPublisher(applicationEventPublisher);

    authenticationEventPublisher.setAdditionalExceptionMappings(
        Collections.singletonMap(OAuth2AuthenticationException.class, OAuth2AuthenticationFailureEvent.class));

    authenticationEventPublisher.setDefaultAuthenticationFailureEvent(DefaultAuthenticationFailureEvent.class);

    return authenticationEventPublisher;
  }

  @Bean
  public SecurityFilterChain apiFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository)
      throws Exception {

    // Disable CSRF protection for development with HAL explorer
    // https://github.com/spring-projects/spring-data-rest/issues/1347
    if (disableCsrf) {
      http.csrf(AbstractHttpConfigurer::disable);
    } else {
      // https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa
      http.csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
          .csrfTokenRepository(csrfTokenRepository)
          // This uses POST for large filter in body, but is safe (read-only)
          .ignoringRequestMatchers(apiBasePath + "/{viewerKind}/{viewerName}/layer/{appLayerId}/features"));
      http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
    }

    // Before redirecting the user to the OAuth2 authorization endpoint, store the requested
    // redirect URL.
    RedirectStrategy redirectStrategy = new DefaultRedirectStrategy() {
      @Override
      public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url)
          throws IOException {
        String redirectUrl = request.getParameter("redirectUrl");
        if (redirectUrl != null && redirectUrl.startsWith("/")) {
          request.getSession().setAttribute("redirectUrl", redirectUrl);
        }
        super.sendRedirect(request, response, url);
      }
    };

    // When OAuth2 authentication succeeds, use the redirect URL stored in the session to send them
    // back.
    AuthenticationSuccessHandler authenticationSuccessHandler = (request, response, authentication) -> {
      HttpSession session = request.getSession(false);
      if (session != null) {
        String redirectUrl = (String) session.getAttribute("redirectUrl");
        if (redirectUrl != null) {
          response.sendRedirect(redirectUrl);
          return;
        }
      }
      response.sendRedirect("/");
    };

    http.securityMatchers(matchers -> matchers.requestMatchers(apiBasePath + "/**"))
        .addFilterAfter(
            /* (debug) log user making the request */ new AuditInterceptor(),
            AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(authorize -> {
          authorize.requestMatchers(adminApiBasePath + "/**").hasAuthority(Group.ADMIN);
          authorize.requestMatchers(apiBasePath + "/**").permitAll();
        })
        .formLogin(formLogin ->
            formLogin.loginPage(apiBasePath + "/unauthorized").loginProcessingUrl(apiBasePath + "/login"))
        .oauth2Login(login -> login.authorizationEndpoint(
                endpoint -> endpoint.baseUri(apiBasePath + "/oauth2/authorization")
                    .authorizationRedirectStrategy(redirectStrategy))
            .redirectionEndpoint(endpoint -> endpoint.baseUri(apiBasePath + "/oauth2/callback"))
            .successHandler(authenticationSuccessHandler))
        .anonymous(anonymous -> anonymous.authorities(Group.ANONYMOUS))
        .logout(logout -> logout.logoutUrl(apiBasePath + "/logout")
            .logoutSuccessHandler((request, response, authentication) ->
                response.sendError(HttpStatus.OK.value(), "OK")));
    return http.build();
  }

  @Bean
  public OIDCRepository clientRegistrationRepository(OIDCConfigurationRepository repository) {
    return new OIDCRepository(repository);
  }

  @Bean
  public GrantedAuthoritiesMapper userAuthoritiesMapper(GroupRepository repository) {
    return (authorities) -> {
      Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

      try {
        mappedAuthorities.addAll(authorities);

        OidcIdToken idToken = authorities.stream()
            .filter(OidcUserAuthority.class::isInstance)
            .map(OidcUserAuthority.class::cast)
            .map(OidcUserAuthority::getIdToken)
            .findFirst()
            .orElseThrow();

        List<String> roles = Optional.ofNullable(idToken.getClaimAsStringList("roles"))
            .orElse(Collections.emptyList());

        for (String role : roles) {
          mappedAuthorities.add(new SimpleGrantedAuthority(role));

          Optional<Group> groupEntity = repository.findById(role);
          if (groupEntity.isPresent()) {
            String alias = groupEntity.get().getAliasForGroup();
            if (StringUtils.isNotBlank(alias)) {
              mappedAuthorities.add(new SimpleGrantedAuthority(alias));
            }
          }
        }
      } catch (Exception e) {
        log.error("Error mapping OIDC authorities", e);
      }
      return mappedAuthorities;
    };
  }
}
