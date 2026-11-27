/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.Reader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;

class SecureXmlFactoriesTest {
  /** Returns the value of the attribute of the root element, which is set with an entity. */
  private static String parseAttributeWithEntity(String doctype) {
    String xml = "<?xml version=\"1.0\"?>" + doctype + "<root value=\"&e;\"/>";
    try {
      XMLStreamReader reader = SecureXmlFactories.newXMLInputFactory().createXMLStreamReader(Reader.of(xml));
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT) {
          return reader.getAttributeValue(null, "value");
        }
      }
      return null;
    } catch (XMLStreamException e) {
      return null;
    }
  }

  @Test
  void xml_input_factory_does_not_load_external_dtd() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse.Builder().body("<!ENTITY e \"from DTD\">").build());
      server.start();
      String url = server.url("/entities.dtd").toString();
      assertNotEquals(
          "from DTD", parseAttributeWithEntity("<!DOCTYPE x [<!ENTITY % p SYSTEM \"" + url + "\"> %p;]>"));
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  void xml_input_factory_does_not_expand_internal_entity() {
    // Also prevents entity expansion attacks ("billion laughs")
    assertNotEquals("42", parseAttributeWithEntity("<!DOCTYPE x [<!ENTITY e \"42\">]>"));
  }
}
