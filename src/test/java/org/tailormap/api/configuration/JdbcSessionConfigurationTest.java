/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.User;
import org.tailormap.api.persistence.json.AdminAdditionalProperty;
import org.tailormap.api.security.TailormapAdditionalProperty;
import org.tailormap.api.security.TailormapOidcUser;
import org.tailormap.api.security.TailormapUserDetailsImpl;

/** Basic test to verify session serialization/deserialization works with configured ConversionService. */
@JsonTest
class JdbcSessionConfigurationTest {

  private ConversionService conversionService;

  @BeforeEach
  void setUp() {
    JdbcSessionConfiguration cfg = new JdbcSessionConfiguration();
    cfg.setBeanClassLoader(this.getClass().getClassLoader());
    conversionService = cfg.springSessionConversionService(new ObjectMapper());
  }

  @Test
  void should_serialize_and_deserialize_security_context_with_tm_userdetails_round_trip() {
    Set<Group> groups = Set.of(new Group()
        .setName("admin")
        .setAdditionalProperties(List.of(new AdminAdditionalProperty("grouptest", true, "group"))));
    User user = new User()
        .setUsername("tm-admin")
        .setAdditionalProperties(List.of(new AdminAdditionalProperty("usertest", true, "user")))
        .setOrganisation("Tailormap")
        .setGroups(groups);
    TailormapUserDetailsImpl userDetails = new TailormapUserDetailsImpl(user, null);

    Authentication auth = new UsernamePasswordAuthenticationToken(
        userDetails, null, List.of(new SimpleGrantedAuthority("admin")));
    SecurityContextImpl ctx = new SecurityContextImpl(auth);

    // serialize
    byte[] bytes = conversionService.convert(ctx, byte[].class);
    assertNotNull(bytes);

    // deserialize
    Object back = conversionService.convert(bytes, Object.class);
    assertNotNull(back);
    assertEquals(SecurityContextImpl.class, back.getClass());
    SecurityContextImpl ctxBack = (SecurityContextImpl) back;
    assertNotNull(ctxBack.getAuthentication());
    assertEquals(
        UsernamePasswordAuthenticationToken.class,
        ctxBack.getAuthentication().getClass());

    TailormapUserDetailsImpl userDetailsBack =
        (TailormapUserDetailsImpl) ctxBack.getAuthentication().getPrincipal();
    assertEquals(userDetails.getAuthorities(), userDetailsBack.getAuthorities());
    assertEquals(userDetails.getUsername(), userDetailsBack.getUsername());
    assertEquals(userDetails.getOrganisation(), userDetailsBack.getOrganisation());
    Collection<String> in = userDetails.getAdditionalProperties().stream()
        .map(Objects::toString)
        .collect(Collectors.toList());
    Collection<String> out = userDetailsBack.getAdditionalProperties().stream()
        .map(Objects::toString)
        .collect(Collectors.toList());
    assertEquals(in, out);
    Set<String> ag = userDetails.getAdditionalGroupProperties().stream()
        .map(Object::toString)
        .collect(Collectors.toSet());
    Set<String> bg = userDetailsBack.getAdditionalGroupProperties().stream()
        .map(Object::toString)
        .collect(Collectors.toSet());
    assertEquals(ag, bg);
    assertEquals(userDetails.isAccountNonExpired(), userDetailsBack.isAccountNonExpired());
    assertEquals(userDetails.isEnabled(), userDetailsBack.isEnabled());
    assertEquals(userDetails.isCredentialsNonExpired(), userDetailsBack.isCredentialsNonExpired());
  }

  @Test
  void should_serialize_and_deserialize_security_context_with_oidc_userdetails_round_trip() {
    TailormapOidcUser userDetails = new TailormapOidcUser(
        List.of(new SimpleGrantedAuthority("admin")),
        OidcIdToken.withTokenValue("test")
            .claim(StandardClaimNames.NAME, "tm-admin")
            .build(),
        OidcUserInfo.builder().name("tm-admin").build(),
        "name",
        "oidc-registration",
        List.of(new TailormapAdditionalProperty("grouptest", true, "group")));

    assertEquals("tm-admin", userDetails.getUsername());

    Authentication auth =
        new OAuth2AuthenticationToken(userDetails, List.of(new SimpleGrantedAuthority("admin")), "client-id");
    SecurityContextImpl ctx = new SecurityContextImpl(auth);

    // serialize
    byte[] bytes = conversionService.convert(ctx, byte[].class);
    assertNotNull(bytes);

    // deserialize
    Object back = conversionService.convert(bytes, Object.class);
    assertNotNull(back);
    assertEquals(SecurityContextImpl.class, back.getClass());
    SecurityContextImpl ctxBack = (SecurityContextImpl) back;
    assertNotNull(ctxBack.getAuthentication());
    assertEquals(auth.getClass(), ctxBack.getAuthentication().getClass());

    TailormapOidcUser userDetailsBack =
        (TailormapOidcUser) ctxBack.getAuthentication().getPrincipal();
    assertEquals(userDetails.getAuthorities(), userDetailsBack.getAuthorities());
    assertEquals(userDetails.getUsername(), userDetailsBack.getUsername());
    assertEquals(userDetails.getOrganisation(), userDetailsBack.getOrganisation());
    assertEquals(userDetails.getAdditionalProperties(), userDetailsBack.getAdditionalProperties());
    assertEquals(userDetails.getAdditionalGroupProperties(), userDetailsBack.getAdditionalGroupProperties());
    assertEquals(userDetails.isAccountNonExpired(), userDetailsBack.isAccountNonExpired());
    assertEquals(userDetails.isEnabled(), userDetailsBack.isEnabled());
    assertEquals(userDetails.isCredentialsNonExpired(), userDetailsBack.isCredentialsNonExpired());
  }
}
