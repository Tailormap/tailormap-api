/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

/**
 * setup testdata from property file.
 *
 * @author mprins
 */
public class StaticTestData {
  public static final Properties testData = new Properties();

  static {
    try {
      testData.load(StaticTestData.class.getResourceAsStream("/StaticTestData.properties"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String get(String key) {
    return testData.getProperty(key);
  }

  public static String getResourceString(Resource resource) throws IOException {
    return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
  }
}
