/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static java.nio.ByteBuffer.wrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.tailormap.api.persistence.TMFeatureType;

class AttachmentsHelperTest {
  static final String ftName = "testFeatureType";
  static final TMFeatureType featureType = org.mockito.Mockito.mock(TMFeatureType.class, invocation -> {
    if ("getName".equals(invocation.getMethod().getName())) {
      return ftName;
    }
    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
  });

  @Test
  void check_array_of_byte_object() {
    UUID uuid = UUID.randomUUID();
    Object testData = new byte[16];
    wrap((byte[]) testData).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
    String result = AttachmentsHelper.fidFromPK(featureType, testData);
    assertEquals(ftName + "." + uuid, result);
  }

  @Test
  void check_integer_object() {
    Object testData = 12345;
    String result = AttachmentsHelper.fidFromPK(featureType, testData);
    assertEquals(ftName + "." + testData, result);
  }

  @Test
  void check_null() {
    assertThrows(IllegalArgumentException.class, () -> AttachmentsHelper.fidFromPK(featureType, null));
  }
}
