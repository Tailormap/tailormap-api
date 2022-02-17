/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        http.csrf()
                .disable()
                .authorizeRequests()
                .antMatchers("/admin/**")
                .hasRole("ADMIN")
                .anyRequest()
                .permitAll()
                .and()
                .formLogin()
                .failureHandler(
                        new SimpleUrlAuthenticationFailureHandler() {
                            @Override
                            public void onAuthenticationFailure(
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    AuthenticationException exception)
                                    throws IOException {
                                logger.debug("Authentication failure: " + exception.getMessage());
                                response.sendError(
                                        HttpServletResponse.SC_FORBIDDEN, "Authentication failed");
                            }
                        })
                .successHandler(
                        new SimpleUrlAuthenticationSuccessHandler() {
                            @Override
                            public void onAuthenticationSuccess(
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    Authentication authentication)
                                    throws IOException {
                                logger.trace(
                                        "Authentication success for " + authentication.getName());
                                // Do not send redirect
                                response.sendError(HttpServletResponse.SC_OK);
                            }
                        });
    }
}
