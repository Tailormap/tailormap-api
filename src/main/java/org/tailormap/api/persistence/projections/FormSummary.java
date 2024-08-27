/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.projections;

import org.springframework.data.rest.core.config.Projection;
import org.tailormap.api.persistence.Form;

@Projection(
    name = "summary",
    types = {Form.class})
public interface FormSummary {

  Long getId();

  String getName();

  Long getFeatureSourceId();

  String getFeatureTypeName();
}
