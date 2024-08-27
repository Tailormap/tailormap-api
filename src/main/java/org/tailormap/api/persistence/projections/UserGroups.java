/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.projections;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZonedDateTime;
import java.util.Set;
import org.springframework.data.rest.core.config.Projection;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.User;

/** Projection for {@link User} that includes the groups (but only the group name). */
@Projection(
    name = "userGroups",
    types = {User.class})
public interface UserGroups {
  String getUsername();

  String getName();

  String getEmail();

  ZonedDateTime getValidUntil();

  boolean isEnabled();

  String getNotes();

  JsonNode getAdditionalProperties();

  Set<Group> getGroups();
}
