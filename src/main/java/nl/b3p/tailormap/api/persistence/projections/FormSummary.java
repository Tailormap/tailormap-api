/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.projections;

import nl.b3p.tailormap.api.persistence.Form;
import org.springframework.data.rest.core.config.Projection;

@Projection(
    name = "summary",
    types = {Form.class})
public interface FormSummary {

  Long getId();

  String getName();

  Long getFeatureSourceId();

  String getFeatureTypeName();
}
