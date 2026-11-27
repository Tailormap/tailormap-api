/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WMSServiceExceptionUtilTest {
  private static String getMessage(String xml) {
    return WMSServiceExceptionUtil.tryGetServiceExceptionMessage(xml.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void wms_1_3_0_service_exception() {
    assertEquals("code: LayerNotDefined: locator: layers: Could not find layer roads", getMessage("""
<?xml version="1.0" encoding="UTF-8"?>
<ServiceExceptionReport version="1.3.0" xmlns="http://www.opengis.net/ogc">
<ServiceException code="LayerNotDefined" locator="layers">
Could not find layer roads
</ServiceException>
</ServiceExceptionReport>
"""));
  }

  @Test
  void ows_exception_report() {
    assertEquals("code: NoApplicableCode: Error", getMessage("""
<?xml version="1.0" encoding="UTF-8"?>
<ows:ExceptionReport xmlns:ows="http://www.opengis.net/ows/1.1" version="2.0.0">
<ows:Exception exceptionCode="NoApplicableCode"><ows:ExceptionText>Error</ows:ExceptionText></ows:Exception>
</ows:ExceptionReport>
"""));
  }

  @Test
  void not_xml() {
    assertNull(getMessage("Internal Server Error"));
  }

  @Test
  void wms_1_1_1_with_doctype_does_not_load_external_dtd() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse.Builder().body("<!ENTITY e \"from DTD\">").build());
      server.start();
      String dtd = server.url("/exception_1_1_1.dtd").toString();
      assertEquals("Error", getMessage("""
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ServiceExceptionReport SYSTEM "%s">
<ServiceExceptionReport version="1.1.1"><ServiceException>Error</ServiceException></ServiceExceptionReport>
""".formatted(dtd)));
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  void does_not_resolve_external_entity(@TempDir Path tempDir) throws Exception {
    Path secret = tempDir.resolve("secret.txt");
    Files.writeString(secret, "secret");
    String message = getMessage("""
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ServiceExceptionReport [<!ENTITY e SYSTEM "%s">]>
<ServiceExceptionReport version="1.3.0"><ServiceException>Error &e;</ServiceException></ServiceExceptionReport>
""".formatted(secret.toUri()));
    assertFalse(message != null && message.contains("secret"), message);
  }

  @Test
  void does_not_load_external_parameter_entity() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse.Builder().body("<!ENTITY e \"from DTD\">").build());
      server.start();
      String url = server.url("/entities.dtd").toString();
      getMessage("""
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ServiceExceptionReport [<!ENTITY %% p SYSTEM "%s"> %%p;]>
<ServiceExceptionReport version="1.3.0"><ServiceException>Error &e;</ServiceException></ServiceExceptionReport>
""".formatted(url));
      assertEquals(0, server.getRequestCount());
    }
  }
}
