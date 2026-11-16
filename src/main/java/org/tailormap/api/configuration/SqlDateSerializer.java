/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration;

import java.sql.Date;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Serializes a {@link java.sql.Date} as an ISO date without a time, for example {@code "2026-10-21"}.
 *
 * <p>GeoTools returns the values of {@code date} database columns as {@link java.sql.Date}, and the features API passes
 * them to Jackson unchanged. Jackson 2 serialized a {@code java.sql.Date} with {@code toString()} as a date only.
 * Jackson 3 serializes it like a {@link java.util.Date}, as a timestamp in UTC (for example
 * {@code "2026-10-21T00:00:00.000Z"}), which the viewer does not accept for date fields. When the JVM time zone is not
 * UTC that timestamp can even fall on the previous day. Jackson 3 has no feature to restore the old behaviour, so this
 * serializer does.
 *
 * <p>{@link Date#toLocalDate()} uses the JVM default time zone, the same time zone the JDBC driver used to create the
 * {@code java.sql.Date}, so the date is the date stored in the database.
 */
@JacksonComponent
public class SqlDateSerializer extends StdSerializer<Date> {
  public SqlDateSerializer() {
    super(Date.class);
  }

  @Override
  public void serialize(Date value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
    gen.writeString(value.toLocalDate().toString());
  }
}
