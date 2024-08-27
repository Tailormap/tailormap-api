/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.projections;

import org.springframework.data.rest.core.config.Projection;
import org.tailormap.api.persistence.Group;

@Projection(
    name = "groupName",
    types = {Group.class})
public interface GroupName {
  String getName();
}
