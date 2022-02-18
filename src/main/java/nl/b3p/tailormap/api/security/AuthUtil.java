/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtil {
    private AuthUtil() {
        /* utility class */
    }

    /**
     * check if current user is logged in.
     *
     * @return {@code true} when current user has a valid session.
     */
    public static boolean isAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (null == authentication) {
            // in case no authentication information is available
            return false;
        }
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.isAuthenticated();
        }
        return false;
    }

    /**
     * get the username of the current user.
     *
     * @return username or an empty string when not logged in
     */
    public static String getAuthenticatedUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (null != authentication && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        } else return "";
    }
}
