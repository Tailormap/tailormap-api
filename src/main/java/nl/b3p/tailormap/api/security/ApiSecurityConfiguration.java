/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.repository.GroupRepository;
import nl.b3p.tailormap.api.repository.OIDCConfigurationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ApiSecurityConfiguration {
  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Value("${tailormap-api.admin.base-path}")
  private String adminApiBasePath;

  @Value("${tailormap-api.security.disable-csrf:false}")
  private boolean disableCsrf;

  @Bean
  public CookieCsrfTokenRepository csrfTokenRepository() {
    // Note: CSRF protection only required when using cookies for authentication. This requires an
    // X-XSRF-TOKEN header read from the XSRF-TOKEN cookie by JavaScript so set HttpOnly to false.
    // Angular has automatic XSRF protection support:
    // https://angular.io/guide/http#security-xsrf-protection
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookiePath("/");
    return csrfTokenRepository;
  }

  @Bean
  public SecurityFilterChain apiFilterChain(
      HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository) throws Exception {

    // Disable CSRF protection for development with HAL explorer
    // https://github.com/spring-projects/spring-data-rest/issues/1347
    if (disableCsrf) {
      http.csrf().disable();
    } else {
      http = http.csrf().csrfTokenRepository(csrfTokenRepository).and();
    }

    http.securityMatchers(matchers -> matchers.requestMatchers(apiBasePath + "/**"))
        .authorizeHttpRequests(
            authorize ->
                authorize.requestMatchers(adminApiBasePath + "/**").hasAuthority(Group.ADMIN))
        .formLogin()
        .loginPage(apiBasePath + "/unauthorized")
        .loginProcessingUrl(apiBasePath + "/login")
        .and()
        .oauth2Login(
            login ->
                login
                    .authorizationEndpoint(
                        endpoint -> endpoint.baseUri(apiBasePath + "/oauth2/authorization"))
                    .redirectionEndpoint(
                        endpoint -> endpoint.baseUri(apiBasePath + "/oauth2/callback")))
        .logout()
        .logoutUrl(apiBasePath + "/logout")
        .logoutSuccessHandler(
            (request, response, authentication) -> response.sendError(HttpStatus.OK.value(), "OK"));

    return http.build();
  }

  @Bean
  public ClientRegistrationRepository clientRegistrationRepository(
      OIDCConfigurationRepository repository) {
    return new OIDCRepository(repository);
  }

  @Bean
  public GrantedAuthoritiesMapper userAuthoritiesMapper(GroupRepository repository) {
    return (authorities) -> {
      Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
      Set<String> wantedGroups = new HashSet<>();

      try {
        authorities.forEach(
            authority -> {
              mappedAuthorities.add(authority);
              if (authority instanceof OidcUserAuthority) {
                OidcUserAuthority oidcUserAuthority = (OidcUserAuthority) authority;
                OidcIdToken idToken = oidcUserAuthority.getIdToken();

                List<String> roles = idToken.getClaimAsStringList("roles");
                if (roles != null) {
                  wantedGroups.addAll(roles);
                }
              }
            });

        for (String groupName : wantedGroups) {
          if (repository.findById(groupName).isEmpty()) {
            Group group = new Group();
            group.setName(groupName);
            group.setDescription("<imported from SSO>");
            repository.save(group);
          }

          mappedAuthorities.add(new SimpleGrantedAuthority(groupName));
        }
      } catch (Exception e) {
      }

      return mappedAuthorities;
    };
  }
}
