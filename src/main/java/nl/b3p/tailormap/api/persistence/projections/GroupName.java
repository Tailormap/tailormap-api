/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.projections;

import nl.b3p.tailormap.api.persistence.Group;
import org.springframework.data.rest.core.config.Projection;

@Projection(
    name = "groupName",
    types = {Group.class})
public interface GroupName {
  String getName();
}
