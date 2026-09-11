/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.security.OIDCRepository;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(value = ExecutionMode.SAME_THREAD, reason = "run in same thread because of mocked OIDCRepository")
class UserControllerOidcIntegrationTest {

  @Value("${tailormap-api.password-reset.enabled}")
  boolean passwordResetEnabled;

  @MockitoBean
  private OIDCRepository oidcRepository;

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  void should_return_login_configuration_with_sso_links_and_password_reset_enabled() throws Exception {
    ClientRegistration clientRegistration = mock(ClientRegistration.class);
    when(clientRegistration.getRegistrationId()).thenReturn("test-registration");
    when(clientRegistration.getClientName()).thenReturn("Test Client");
    OIDCRepository.OIDCRegistrationMetadata metadata = mock(OIDCRepository.OIDCRegistrationMetadata.class);
    when(metadata.getShowForViewer()).thenReturn(true);
    when(metadata.getImage()).thenReturn(null);
    when(oidcRepository.iterator()).thenReturn(List.of(clientRegistration).iterator());
    when(oidcRepository.getMetadataForRegistrationId("test-registration")).thenReturn(metadata);

    mockMvc.perform(get(apiBasePath + "/login/configuration"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.ssoLinks.length()").value(1))
        .andExpect(jsonPath("$.ssoLinks[0].name").value("Test Client"))
        .andExpect(jsonPath("$.ssoLinks[0].url").value("/api/oauth2/authorization/test-registration"))
        .andExpect(jsonPath("$.ssoLinks[0].showForViewer").value(true))
        .andExpect(jsonPath("$.ssoLinks[0].image").isEmpty())
        .andExpect(jsonPath("$.enablePasswordReset").value(passwordResetEnabled));
  }

  @Test
  void should_return_empty_login_configuration_when_no_sso_configured() throws Exception {
    when(oidcRepository.iterator()).thenReturn(Collections.emptyIterator());
    mockMvc.perform(get(apiBasePath + "/login/configuration"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.ssoLinks").isEmpty())
        .andExpect(jsonPath("$.enablePasswordReset").value(passwordResetEnabled));
  }
}
