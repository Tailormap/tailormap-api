/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package db.migration;

import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class V18__UploadHash extends BaseJavaMigration {
  @Override
  public void migrate(Context context) {
    var jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
    jdbcTemplate.execute("alter table upload add column hash char(40) null");

    var rows = jdbcTemplate.queryForList("select id, content from upload");
    for (var row : rows) {
      UUID id = (UUID) row.get("id");
      byte[] content = (byte[]) row.get("content");
      if (content != null) {
        String hash = DigestUtils.sha1Hex(content);
        jdbcTemplate.update("update upload set hash = ? where id = ?", hash, id);
      }
    }
  }
}
