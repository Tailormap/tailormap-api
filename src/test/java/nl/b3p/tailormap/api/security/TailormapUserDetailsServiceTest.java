/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

@SpringBootTest(
        classes = {HSQLDBTestProfileJPAConfiguration.class, TailormapUserDetailsService.class})
@ActiveProfiles("test")
class TailormapUserDetailsServiceTest {

    @Autowired TailormapUserDetailsService userDetailsService;

    @Test
    void testLoadUserByUsername_Admin() {
        UserDetails userDetails = userDetailsService.loadUserByUsername("admin");
        assertNotNull(userDetails, "admin is defined in the test dataset");
        assertEquals(
                "admin",
                userDetails.getUsername(),
                () -> "incorrect username " + userDetails.getUsername());
        assertTrue(
                null != userDetails.getPassword() && userDetails.getPassword().length() > 0,
                "password should not be null or empty string");

        assertEquals(
                Collections.singleton(new SimpleGrantedAuthority("Admin")),
                userDetails.getAuthorities(),
                "Admin should be Admin only");

        assertTrue(userDetails.isAccountNonExpired(), "hardcoded to return true");
        assertTrue(userDetails.isAccountNonLocked(), "hardcoded to return true");
        assertTrue(userDetails.isCredentialsNonExpired(), "hardcoded to return true");
        assertTrue(userDetails.isEnabled(), "hardcoded to return true");
    }

    @Test
    void testLoadUserByUsername_Pietje() {
        UserDetails userDetails = userDetailsService.loadUserByUsername("pietje");
        assertNotNull(userDetails, "pietje is defined in the test dataset");
        assertEquals(
                "pietje",
                userDetails.getUsername(),
                () -> "incorrect username " + userDetails.getUsername());
        assertTrue(
                null != userDetails.getPassword() && userDetails.getPassword().length() > 0,
                "password should not be null or empty string");

        assertEquals(
                Collections.singleton(new SimpleGrantedAuthority("UserAdmin")),
                userDetails.getAuthorities(),
                "Pietje should be UserAdmin only");

        assertTrue(userDetails.isAccountNonExpired(), "hardcoded to return true");
        assertTrue(userDetails.isAccountNonLocked(), "hardcoded to return true");
        assertTrue(userDetails.isCredentialsNonExpired(), "hardcoded to return true");
        assertTrue(userDetails.isEnabled(), "hardcoded to return true");
    }

    @Test
    void testLoadUserByUsernameUserDoesNotExist() {
        UsernameNotFoundException thrown =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> userDetailsService.loadUserByUsername("doesnotexist"),
                        "UsernameNotFoundException was expected");
        assertNotNull(thrown, "thrown exception should not be null");
    }
}
