/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.drawing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.tailormap.api.viewer.model.Drawing;

public class DrawingMapper implements RowMapper<Drawing> {

  @Override
  public Drawing mapRow(ResultSet rs, int rowNum) throws SQLException {
    Drawing drawing = new Drawing();
    drawing.setId(UUID.fromString(rs.getString("id")));
    drawing.setName(rs.getString("name"));
    drawing.setDescription(rs.getString("description"));
    drawing.setSrid(rs.getInt("srid"));
    drawing.setVersion(rs.getInt("version"));
    drawing.setDomainData(rs.getString("domaindata"));
    drawing.setAccess(Drawing.AccessEnum.fromValue(rs.getString("access")));
    drawing.setCreatedAt(rs.getTimestamp("created_at")
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .toOffsetDateTime());
    drawing.setCreatedBy(rs.getString("created_by"));
    // updated_at and updated_by are optional
    drawing.setUpdatedAt(
        rs.getTimestamp("updated_at") == null
            ? null
            : rs.getTimestamp("updated_at")
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime());
    drawing.setUpdatedBy(rs.getString("updated_by"));

    return drawing;
  }
}
