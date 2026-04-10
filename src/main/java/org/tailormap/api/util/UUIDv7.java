/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * A utility class to create UUID version 7, which is time-sortable.
 *
 * @see <a href="https://antonz.org/uuidv7/#java">UUIDv7 in 33 languages</a> for more details.
 * @see <a href="https://bugs.openjdk.org/browse/JDK-8357251">for a potential future built-in implementation in Java
 *     26.</a>
 */
public class UUIDv7 {
  private static final SecureRandom random = new SecureRandom();

  private UUIDv7() {
    // private constructor for utility class
  }

  /**
   * Create a random version 7 UUID.
   *
   * @return a random version 7 UUID
   */
  public static UUID randomV7() {
    byte[] value = randomBytes();
    ByteBuffer buf = ByteBuffer.wrap(value);
    long high = buf.getLong();
    long low = buf.getLong();
    return new UUID(high, low);
  }

  private static byte[] randomBytes() {
    byte[] value = new byte[16];
    random.nextBytes(value);
    ByteBuffer timestamp = ByteBuffer.allocate(Long.BYTES).putLong(System.currentTimeMillis());
    System.arraycopy(timestamp.array(), 2, value, 0, 6);
    // set version 7
    value[6] = (byte) ((value[6] & 0x0F) | 0x70);
    // set RFC 4122 variant
    value[8] = (byte) ((value[8] & 0x3F) | 0x80);
    return value;
  }

  /**
   * Extract the Unix epoch timestamp (milliseconds) from a UUIDv7.
   *
   * @param uuidv7 the UUIDv7 value
   * @return the embedded Unix epoch timestamp in milliseconds
   * @throws IllegalArgumentException if the given UUID is not version 7
   */
  public static long timestamp(UUID uuidv7) throws IllegalArgumentException {
    if (uuidv7 == null) {
      throw new IllegalArgumentException("UUID cannot be null");
    }
    if (uuidv7.version() != 7) {
      throw new IllegalArgumentException("UUID is not version 7");
    }
    // UUIDv7 stores a 48-bit Unix epoch millisecond timestamp in the first 6 bytes.
    return (uuidv7.getMostSignificantBits() >>> 16) & 0x0000FFFFFFFFFFFFL;
  }

  /**
   * Extract the Unix epoch timestamp as an Instant from a UUIDv7.
   *
   * @param uuidv7 the UUIDv7 value
   * @return the embedded Unix epoch timestamp in milliseconds
   * @throws IllegalArgumentException if the given UUID is not version 7
   * @see #timestamp(UUID)
   */
  public static Instant timestampAsInstant(UUID uuidv7) throws IllegalArgumentException {
    return Instant.ofEpochMilli(timestamp(uuidv7));
  }

  /**
   * Parse a string representation of a UUIDv7.
   *
   * <p>Accepts the standard {@code xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx} format.
   *
   * @param value the string to parse
   * @return the parsed UUIDv7
   * @throws IllegalArgumentException if the string is not a valid UUID or not version 7
   */
  public static UUID fromString(String value) throws IllegalArgumentException {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("UUID string cannot be null or blank");
    }
    // Remove hyphens and parse the 32 hex characters directly
    String hex = value.replace("-", "");
    if (hex.length() != 32) {
      throw new IllegalArgumentException("Invalid UUID string: " + value);
    }
    try {
      long high = Long.parseUnsignedLong(hex, 0, 16, 16);
      long low = Long.parseUnsignedLong(hex, 16, 32, 16);
      UUID uuid = new UUID(high, low);
      if (uuid.version() != 7) {
        throw new IllegalArgumentException("UUID is not version 7: " + value);
      }
      return uuid;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid UUID string: " + value, e);
    }
  }
}
