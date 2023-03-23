/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.projections;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZonedDateTime;
import java.util.Set;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.User;
import org.springframework.data.rest.core.config.Projection;

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
