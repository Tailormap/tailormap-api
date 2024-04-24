/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.repository.GroupRepository;
import nl.b3p.tailormap.api.repository.OIDCConfigurationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
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
    // Spring CSRF protection requires an X-XSRF-TOKEN header read from the XSRF-TOKEN cookie by
    // JavaScript so set HttpOnly to false. Angular has automatic XSRF protection support:
    // https://angular.io/guide/http#security-xsrf-protection
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    // Allow cross-domain non-GET (unsafe) requests for embedding with an iframe
    csrfTokenRepository.setCookieCustomizer(
        cookieCustomizer -> {
          // Do not set SameSite=None when testing with HTTP instead of HTTPS
          // Ideally use HttpServletRequest.isSecure(), but look at built cookie for now...
          if (cookieCustomizer.build().isSecure()) {
            cookieCustomizer.sameSite(Cookie.SameSite.NONE.attributeValue());
          }
        });
    return csrfTokenRepository;
  }

  @Bean
  public SecurityFilterChain apiFilterChain(
      HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository) throws Exception {

    // Disable CSRF protection for development with HAL explorer
    // https://github.com/spring-projects/spring-data-rest/issues/1347
    if (disableCsrf) {
      http.csrf(AbstractHttpConfigurer::disable);
    } else {
      // https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa
      http.csrf(
          csrf ->
              csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                  .csrfTokenRepository(csrfTokenRepository)
                  // This uses POST for large filter in body, but is safe (read-only)
                  .ignoringRequestMatchers(
                      apiBasePath + "/{viewerKind}/{viewerName}/layer/{appLayerId}/features"));
      http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
    }

    // Before redirecting the user to the OAuth2 authorization endpoint, store the requested
    // redirect URL.
    RedirectStrategy redirectStrategy =
        new DefaultRedirectStrategy() {
          @Override
          public void sendRedirect(
              HttpServletRequest request, HttpServletResponse response, String url)
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
    AuthenticationSuccessHandler authenticationSuccessHandler =
        (request, response, authentication) -> {
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
        .authorizeHttpRequests(
            authorize -> {
              authorize.requestMatchers(adminApiBasePath + "/**").hasAuthority(Group.ADMIN);
              authorize.requestMatchers(apiBasePath + "/**").permitAll();
            })
        .formLogin(
            formLogin ->
                formLogin
                    .loginPage(apiBasePath + "/unauthorized")
                    .loginProcessingUrl(apiBasePath + "/login"))
        .oauth2Login(
            login ->
                login
                    .authorizationEndpoint(
                        endpoint ->
                            endpoint
                                .baseUri(apiBasePath + "/oauth2/authorization")
                                .authorizationRedirectStrategy(redirectStrategy))
                    .redirectionEndpoint(
                        endpoint -> endpoint.baseUri(apiBasePath + "/oauth2/callback"))
                    .successHandler(authenticationSuccessHandler))
        .anonymous(anonymous -> anonymous.authorities(Group.ANONYMOUS))
        .logout(
            logout ->
                logout
                    .logoutUrl(apiBasePath + "/logout")
                    .logoutSuccessHandler(
                        (request, response, authentication) ->
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
      Set<String> wantedGroups = new HashSet<>();

      try {
        authorities.forEach(
            authority -> {
              mappedAuthorities.add(authority);
              if (authority instanceof OidcUserAuthority oidcUserAuthority) {
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
        // Ignore
      }

      return mappedAuthorities;
    };
  }
}
