/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.User;
import org.tailormap.api.persistence.json.AdminAdditionalProperty;
import org.tailormap.api.security.TailormapAdditionalProperty;
import org.tailormap.api.security.TailormapUserDetailsImpl;

/**
 * Integration test to verify custom PostgreSQL-specific SQL queries for session attribute storage
 * work correctly with the database.
 */
@PostgresIntegrationTest
@AutoConfigureMockMvc
class JdbcSessionConfigurationIntegrationTest {

  private static final String SELECT_ATTRIBUTE_BYTES_SQL =
      "SELECT ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID = ? AND ATTRIBUTE_NAME = ?";

  @Autowired
  private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  @Autowired
  private DataSource dataSource;

  private String getAttributeBytes(String sessionId, String attributeName) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    return jdbcTemplate.queryForObject(
        SELECT_ATTRIBUTE_BYTES_SQL,
        (rs, rowNum) -> rs.getString("ATTRIBUTE_BYTES"),
        sessionId,
        attributeName);
  }

  @Test
  void should_create_session_attribute_using_postgresql_convert_from_and_jsonb() {
    // Create a session with a complex object as an attribute
    Session session = sessionRepository.createSession();
    String sessionId = session.getId();

    User user = new User()
        .setUsername("test-user")
        .setAdditionalProperties(List.of(new AdminAdditionalProperty("key1", true, "value1")));
    TailormapUserDetailsImpl userDetails = new TailormapUserDetailsImpl(user, null);
    userDetails.getAdditionalGroupProperties().add(new TailormapAdditionalProperty("groupkey", true, "groupvalue"));

    Authentication auth = new UsernamePasswordAuthenticationToken(
        userDetails, null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextImpl ctx = new SecurityContextImpl(auth);

    session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);
    sessionRepository.save(session);

    // Verify the attribute was stored as JSONB in the database using the custom CREATE query
    String jsonbContent = getAttributeBytes(session.getId(), "SPRING_SECURITY_CONTEXT");

    assertNotNull(jsonbContent, "JSONB content should not be null");
    assertTrue(jsonbContent.contains("test-user"), "JSON should contain username");
    assertTrue(jsonbContent.contains("UsernamePasswordAuthenticationToken"), "JSON should contain authentication type");

    // Verify we can retrieve the session and the attribute is correctly deserialized
    Session retrievedSession = sessionRepository.findById(sessionId);
    assertNotNull(retrievedSession, "Session should be retrievable");

    SecurityContextImpl retrievedCtx = retrievedSession.getAttribute("SPRING_SECURITY_CONTEXT");
    assertNotNull(retrievedCtx, "Security context should be retrievable");
    assertNotNull(retrievedCtx.getAuthentication(), "Authentication should be present");

    TailormapUserDetailsImpl retrievedUserDetails = 
        (TailormapUserDetailsImpl) retrievedCtx.getAuthentication().getPrincipal();
    assertEquals("test-user", retrievedUserDetails.getUsername(), "Username should match");
    assertEquals(1, retrievedUserDetails.getAdditionalProperties().size(), "Should have one additional property");
    assertEquals(1, retrievedUserDetails.getAdditionalGroupProperties().size(), "Should have one group property");
  }

  @Test
  void should_update_session_attribute_using_postgresql_encode_and_jsonb() {
    // Create a session with an initial attribute
    Session session = sessionRepository.createSession();
    String sessionId = session.getId();

    User user = new User().setUsername("initial-user");
    TailormapUserDetailsImpl userDetails = new TailormapUserDetailsImpl(user, null);
    Authentication auth = new UsernamePasswordAuthenticationToken(
        userDetails, null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextImpl ctx = new SecurityContextImpl(auth);

    session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);
    sessionRepository.save(session);

    // Update the attribute with a different user
    Session retrievedSession = sessionRepository.findById(sessionId);
    assertNotNull(retrievedSession, "Session should exist");

    User updatedUser = new User()
        .setUsername("updated-user")
        .setAdditionalProperties(List.of(
            new AdminAdditionalProperty("newkey", false, "newvalue")));
    TailormapUserDetailsImpl updatedUserDetails = new TailormapUserDetailsImpl(updatedUser, null);
    Authentication updatedAuth = new UsernamePasswordAuthenticationToken(
        updatedUserDetails, null, List.of(new SimpleGrantedAuthority("ADMIN")));
    SecurityContextImpl updatedCtx = new SecurityContextImpl(updatedAuth);

    retrievedSession.setAttribute("SPRING_SECURITY_CONTEXT", updatedCtx);
    sessionRepository.save(retrievedSession);

    // Verify the attribute was updated as JSONB in the database using the custom UPDATE query
    String jsonbContent = getAttributeBytes(sessionId, "SPRING_SECURITY_CONTEXT");

    assertNotNull(jsonbContent, "Updated JSONB content should not be null");
    assertTrue(jsonbContent.contains("updated-user"), "JSON should contain updated username");
    assertTrue(jsonbContent.contains("ADMIN"), "JSON should contain updated authority");

    // Verify we can retrieve the updated session
    Session finalSession = sessionRepository.findById(sessionId);
    assertNotNull(finalSession, "Updated session should be retrievable");

    SecurityContextImpl finalCtx = finalSession.getAttribute("SPRING_SECURITY_CONTEXT");
    assertNotNull(finalCtx, "Updated security context should be retrievable");

    TailormapUserDetailsImpl finalUserDetails = 
        (TailormapUserDetailsImpl) finalCtx.getAuthentication().getPrincipal();
    assertEquals("updated-user", finalUserDetails.getUsername(), "Username should be updated");
    assertEquals(1, finalUserDetails.getAdditionalProperties().size(), "Should have updated properties");
    assertEquals("newkey", finalUserDetails.getAdditionalProperties().get(0).getKey(), 
        "Property key should match");
  }

  @Test
  void should_handle_multiple_attributes_in_same_session() {
    // Create a session with multiple attributes
    Session session = sessionRepository.createSession();
    String sessionId = session.getId();

    // Add first attribute - security context
    User user = new User().setUsername("multi-attr-user");
    TailormapUserDetailsImpl userDetails = new TailormapUserDetailsImpl(user, null);
    Authentication auth = new UsernamePasswordAuthenticationToken(
        userDetails, null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextImpl ctx = new SecurityContextImpl(auth);
    session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);

    // Add second attribute - custom data
    session.setAttribute("CUSTOM_DATA", "test-value");

    // Add third attribute - timestamp
    session.setAttribute("LOGIN_TIME", Instant.now().toEpochMilli());

    sessionRepository.save(session);

    // Verify all attributes are stored correctly
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    Integer attributeCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID = ?",
        Integer.class,
        sessionId);

    assertEquals(3, attributeCount, "Should have three attributes");

    // Retrieve and verify each attribute
    Session retrievedSession = sessionRepository.findById(sessionId);
    assertNotNull(retrievedSession, "Session should be retrievable");

    SecurityContextImpl retrievedCtx = retrievedSession.getAttribute("SPRING_SECURITY_CONTEXT");
    assertNotNull(retrievedCtx, "Security context should be retrievable");

    String customData = retrievedSession.getAttribute("CUSTOM_DATA");
    assertEquals("test-value", customData, "Custom data should match");

    Long loginTime = retrievedSession.getAttribute("LOGIN_TIME");
    assertNotNull(loginTime, "Login time should be retrievable");
  }

  @Test
  void should_handle_jsonb_queries_with_special_characters() {
    // Test that special JSON characters are properly escaped
    Session session = sessionRepository.createSession();
    String sessionId = session.getId();

    User user = new User()
        .setUsername("user-with-\"quotes\"")
        .setAdditionalProperties(List.of(
            new AdminAdditionalProperty("key with spaces", true, "value with 'quotes' and \"double quotes\"")));
    TailormapUserDetailsImpl userDetails = new TailormapUserDetailsImpl(user, null);
    Authentication auth = new UsernamePasswordAuthenticationToken(
        userDetails, null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextImpl ctx = new SecurityContextImpl(auth);

    session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);
    sessionRepository.save(session);

    // Verify the data with special characters is stored and retrievable
    Session retrievedSession = sessionRepository.findById(sessionId);
    assertNotNull(retrievedSession, "Session should be retrievable");

    SecurityContextImpl retrievedCtx = retrievedSession.getAttribute("SPRING_SECURITY_CONTEXT");
    assertNotNull(retrievedCtx, "Security context should be retrievable");

    TailormapUserDetailsImpl retrievedUserDetails = 
        (TailormapUserDetailsImpl) retrievedCtx.getAuthentication().getPrincipal();
    assertEquals("user-with-\"quotes\"", retrievedUserDetails.getUsername(), 
        "Username with special characters should match");

    AdminAdditionalProperty prop = (AdminAdditionalProperty) retrievedUserDetails.getAdditionalProperties().get(0);
    assertEquals("key with spaces", prop.getKey(), "Key with spaces should match");
    assertTrue(prop.getValue().contains("'quotes'"), "Value should contain single quotes");
    assertTrue(prop.getValue().contains("\"double quotes\""), "Value should contain double quotes");
  }

  @Test
  void should_verify_jsonb_storage_type_in_database() {
    // Verify that the ATTRIBUTE_BYTES column actually uses JSONB type
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    String dataTypeSql = """
        SELECT data_type 
        FROM information_schema.columns 
        WHERE table_name = 'spring_session_attributes' 
        AND column_name = 'attribute_bytes'
        """;
    
    String dataType = jdbcTemplate.queryForObject(dataTypeSql, String.class);
    assertEquals("jsonb", dataType.toLowerCase(), "ATTRIBUTE_BYTES should be JSONB type");
  }
}
