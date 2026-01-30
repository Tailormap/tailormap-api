/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.User;
import org.tailormap.api.persistence.json.AdminAdditionalProperty;
import org.tailormap.api.repository.GroupRepository;
import org.tailormap.api.security.TailormapAdditionalProperty;
import org.tailormap.api.security.TailormapUserDetailsImpl;

/**
 * Integration test to verify custom PostgreSQL-specific SQL queries for session attribute storage work correctly with
 * the database.
 */
@PostgresIntegrationTest
@AutoConfigureMockMvc
@SuppressWarnings("unchecked")
class JdbcSessionConfigurationIntegrationTest {

  private static final String SELECT_ATTRIBUTE_BYTES_SQL = """
select jsonb_pretty(attribute_bytes) as attribute from spring_session_attributes
where attribute_name = 'SPRING_SECURITY_CONTEXT'
and session_primary_id = (select primary_id from spring_session where session_id = ?)
""";

  /** this is in fact a org.springframework.session.jdbc.JdbcIndexedSessionRepository. */
  @Autowired
  @SuppressWarnings("rawtypes")
  private FindByIndexNameSessionRepository sessionRepository;

  @Autowired
  private GroupRepository groupRepository;

  @Autowired
  private DataSource dataSource;

  /** this is in fact a org.springframework.session.jdbc.JdbcIndexedSessionRepository.JdbcSession. */
  private Session session;

  private String sessionId;
  private JdbcTemplate jdbcTemplate;

  private String getAttribute(String sessionId) {
    return jdbcTemplate.queryForObject(
        SELECT_ATTRIBUTE_BYTES_SQL, (rs, rowNum) -> rs.getString("attribute"), sessionId);
  }

  @BeforeEach
  void setUp() {
    session = sessionRepository.createSession();
    sessionId = session.getId();
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM spring_session WHERE session_id=?", sessionId);
  }

  @Test
  void should_create_session_attribute_using_postgresql_convert_from_and_jsonb() {
    User user = new User()
        .setUsername("test-user")
        .setAdditionalProperties(List.of(new AdminAdditionalProperty("userkey", true, "uservalue")))
        .setGroups(Set.of(new Group().setName("test-bar")));
    // Note: group additional properties are normally loaded from the database via GroupRepository
    TailormapUserDetailsImpl userDetails = new TailormapUserDetailsImpl(user, groupRepository);

    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextImpl ctx = new SecurityContextImpl(auth);

    session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);
    sessionRepository.save(session);

    String jsonbContent = getAttribute(session.getId());
    assertNotNull(jsonbContent, "JSONB content should not be null");
    assertTrue(jsonbContent.contains("test-user"), "JSON should contain username");
    assertTrue(
        jsonbContent.contains("UsernamePasswordAuthenticationToken"),
        "JSON should contain authentication type");

    Session retrievedSession = sessionRepository.findById(sessionId);
    assertNotNull(retrievedSession, "Session should be retrievable");

    SecurityContextImpl retrievedCtx = retrievedSession.getAttribute("SPRING_SECURITY_CONTEXT");
    assertNotNull(retrievedCtx, "Security context should be retrievable");
    assertNotNull(retrievedCtx.getAuthentication(), "Authentication should be present");

    TailormapUserDetailsImpl retrievedUserDetails =
        (TailormapUserDetailsImpl) retrievedCtx.getAuthentication().getPrincipal();
    assertEquals("test-user", retrievedUserDetails.getUsername(), "Username should match");
    assertEquals(1, retrievedUserDetails.getAdditionalProperties().size(), "Should have one additional property");
    assertEquals(2, retrievedUserDetails.getAdditionalGroupProperties().size(), "Should have two group properties");
  }

  @Test
  void should_update_session_attribute_using_postgresql_encode_and_jsonb() {
    User user = new User().setUsername("initial-user");
    TailormapUserDetailsImpl userDetails = new TailormapUserDetailsImpl(user, null);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextImpl ctx = new SecurityContextImpl(auth);

    session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);
    sessionRepository.save(session);

    Session retrievedSession = sessionRepository.findById(sessionId);
    assertNotNull(retrievedSession, "Session should exist");

    User updatedUser = new User()
        .setUsername("updated-user")
        .setAdditionalProperties(List.of(new AdminAdditionalProperty("newkey", false, "newvalue")));
    TailormapUserDetailsImpl updatedUserDetails = new TailormapUserDetailsImpl(updatedUser, null);
    Authentication updatedAuth = new UsernamePasswordAuthenticationToken(
        updatedUserDetails, null, List.of(new SimpleGrantedAuthority("ADMIN")));
    SecurityContextImpl updatedCtx = new SecurityContextImpl(updatedAuth);

    retrievedSession.setAttribute("SPRING_SECURITY_CONTEXT", updatedCtx);
    sessionRepository.save(retrievedSession);
    String jsonbContent = getAttribute(sessionId);

    assertNotNull(jsonbContent, "Updated JSONB content should not be null");
    assertTrue(jsonbContent.contains("updated-user"), "JSON should contain updated username");
    assertTrue(jsonbContent.contains("ADMIN"), "JSON should contain updated authority");

    Session finalSession = sessionRepository.findById(sessionId);
    assertNotNull(finalSession, "Updated session should be retrievable");

    SecurityContextImpl finalCtx = finalSession.getAttribute("SPRING_SECURITY_CONTEXT");
    assertNotNull(finalCtx, "Updated security context should be retrievable");

    TailormapUserDetailsImpl finalUserDetails =
        (TailormapUserDetailsImpl) finalCtx.getAuthentication().getPrincipal();
    assertEquals("updated-user", finalUserDetails.getUsername(), "Username should be updated");
    assertEquals(1, finalUserDetails.getAdditionalProperties().size(), "Should have updated properties");
    assertEquals(
        "newkey",
        finalUserDetails.getAdditionalProperties().stream()
            .findFirst()
            .orElseThrow()
            .key(),
        "Property key should match");
  }

  @Test
  void should_handle_multiple_attributes_in_same_session() {
    User user = new User().setUsername("multi-attr-user");
    TailormapUserDetailsImpl userDetails = new TailormapUserDetailsImpl(user, null);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextImpl ctx = new SecurityContextImpl(auth);
    session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);

    session.setAttribute("CUSTOM_DATA", "test-value");
    session.setAttribute("LOGIN_TIME", Instant.now().toEpochMilli());
    sessionRepository.save(session);

    Integer attributeCount = jdbcTemplate.queryForObject("""
select count(*) from spring_session_attributes
where session_primary_id = (select primary_id from spring_session where session_id = ?)
""", Integer.class, sessionId);

    assertEquals(3, attributeCount, "Should have three attributes");

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

    User user = new User()
        .setUsername("user-with-\"quotes\"")
        .setAdditionalProperties(List.of(new AdminAdditionalProperty(
            "key with spaces", true, "value with 'quotes' and \"double quotes\"")));
    TailormapUserDetailsImpl userDetails = new TailormapUserDetailsImpl(user, null);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextImpl ctx = new SecurityContextImpl(auth);

    session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);
    sessionRepository.save(session);

    Session retrievedSession = sessionRepository.findById(sessionId);
    assertNotNull(retrievedSession, "Session should be retrievable");

    SecurityContextImpl retrievedCtx = retrievedSession.getAttribute("SPRING_SECURITY_CONTEXT");
    assertNotNull(retrievedCtx, "Security context should be retrievable");

    TailormapUserDetailsImpl retrievedUserDetails =
        (TailormapUserDetailsImpl) retrievedCtx.getAuthentication().getPrincipal();
    assertEquals(
        "user-with-\"quotes\"",
        retrievedUserDetails.getUsername(),
        "Username with special characters should match");

    TailormapAdditionalProperty prop = retrievedUserDetails.getAdditionalProperties().stream()
        .findFirst()
        .orElseThrow();
    assertEquals("key with spaces", prop.key(), "Key with spaces should match");
    assertTrue(((String) prop.value()).contains("'quotes'"), "Value should contain single quotes");
    assertTrue(((String) prop.value()).contains("\"double quotes\""), "Value should contain double quotes");
  }

  @Test
  void should_verify_jsonb_storage_type_in_database() {
    String dataTypeSql = """
SELECT data_type
FROM information_schema.columns
WHERE table_name = 'spring_session_attributes'
AND column_name = 'attribute_bytes'
""";

    String dataType = jdbcTemplate.queryForObject(dataTypeSql, String.class);
    assertNotNull(dataType, "Data type should not be null");
    assertEquals("jsonb", dataType.toLowerCase(java.util.Locale.ROOT), "ATTRIBUTE_BYTES should be JSONB type");
  }
}
