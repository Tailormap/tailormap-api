/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.tailormap.api.viewer.model.Feature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks that the auto-configured JsonMapper (also used by Spring MVC) serializes {@link java.sql.Date} values as a
 * date without a time. The application picks up {@link SqlDateSerializer} with the component scan in
 * {@code TailormapApiConfiguration}, which the {@code @JsonTest} slice does not run, so it is imported here.
 */
@JsonTest
@Import(SqlDateSerializer.class)
class SqlDateSerializerTest {
  @Autowired
  private JsonMapper jsonMapper;

  @Test
  void sql_date_is_serialized_as_date_only() {
    assertEquals("\"2026-10-21\"", jsonMapper.writeValueAsString(Date.valueOf("2026-10-21")));
  }

  @Test
  void sql_date_in_feature_attributes_is_serialized_as_date_only() {
    // Feature attributes are a Map<String, Object> filled with the values GeoTools returns
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("creationdate", Date.valueOf("2026-10-21"));
    attributes.put("terminationdate", null);
    Feature feature = new Feature().fid("test.1").attributes(attributes);

    String json = jsonMapper.writeValueAsString(feature);

    assertEquals(
        "2026-10-21",
        jsonMapper.readTree(json).get("attributes").get("creationdate").asString());
  }

  @Test
  void timestamp_is_still_serialized_as_timestamp() {
    String json = jsonMapper.writeValueAsString(Timestamp.from(Instant.parse("2026-10-21T10:15:30Z")));
    assertEquals("\"2026-10-21T10:15:30.000Z\"", json);
  }
}
