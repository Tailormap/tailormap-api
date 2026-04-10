/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UUIDv7Test {
  @Test
  void testExtractUuid() throws InterruptedException {
    ArrayList<UUID> uuids = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Thread.sleep(10); // Ensure different timestamps for each UUID
      UUID uuid = UUIDv7.randomV7();
      assertNotNull(uuid);
      uuids.add(uuid);
      assertEquals(7, uuid.version(), () -> "Expected version 7, got " + uuid.version());
      assertEquals(2, uuid.variant(), () -> "Expected RFC 4122 variant, got " + uuid.variant());
      assertThat((double) (Instant.now().toEpochMilli() - UUIDv7.timestamp(uuid)), closeTo(0d, 100d));
    }

    UUID[] cloned = uuids.toArray(new UUID[0]).clone();
    uuids.sort(UUID::compareTo);
    for (int i = 0; i < uuids.size(); i++) {
      assertEquals(cloned[i], uuids.get(i), "Expected UUIDs to be in the same order after sorting " + i);
      if (i > 0) {
        assertTrue(
            UUIDv7.timestampAsInstant(uuids.get(i)).isAfter(UUIDv7.timestampAsInstant(uuids.get(i - 1))),
            "Expected timestamps to be in ascending order " + i);
      }
    }
  }

  @Test
  void testRoundTrip() {
    UUID uuid = UUIDv7.randomV7();
    Instant timestamp = UUIDv7.timestampAsInstant(uuid);
    assertNotNull(timestamp);
    // uuid v4 check
    assertNotNull(UUID.fromString(uuid.toString()));

    UUID parsed = UUIDv7.fromString(uuid.toString());
    assertNotNull(parsed);
    assertEquals(7, parsed.version(), () -> "Expected version 7, got " + parsed.version());
    assertEquals(timestamp.toEpochMilli(), UUIDv7.timestamp(parsed));
    assertEquals(uuid, parsed);
  }
}
