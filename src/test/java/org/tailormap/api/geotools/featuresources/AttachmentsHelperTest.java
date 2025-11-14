/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class AttachmentsHelperTest {
  @Test
  void testCheckArrayOfByteObject() {
    Object testData = new byte[] {1, 2, 3, 4, 5};
    Comparable<?> result = AttachmentsHelper.checkAndMakeFeaturePkComparable(testData);
    assertNotNull(result);
    assertInstanceOf(ByteBuffer.class, result);
  }

  @Test
  void testCheckIntegerObject() {
    Object testData = 12345;
    Comparable<?> result = AttachmentsHelper.checkAndMakeFeaturePkComparable(testData);
    assertNotNull(result);
    assertInstanceOf(Integer.class, result);
  }
}
