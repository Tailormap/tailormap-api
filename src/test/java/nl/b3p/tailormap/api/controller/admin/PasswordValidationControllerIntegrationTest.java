package nl.b3p.tailormap.api.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Group;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
@AutoConfigureMockMvc
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordValidationControllerIntegrationTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @ParameterizedTest
  @CsvSource({
    "short,false",
    "jI@#%09,false",
    "jI@#%09y,true",
    "very long password without special characters but unguessable,true"
  })
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test(String password, String expected) throws Exception {
    mockMvc
        .perform(
            post(adminBasePath + "/validate-password")
                //                    .servletPath(adminBasePath + "/validate-password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .param("password", password)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.result").value(expected));
  }
}
