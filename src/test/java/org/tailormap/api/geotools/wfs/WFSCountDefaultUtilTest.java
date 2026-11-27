/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.geotools.api.data.DataStore;
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tailormap.api.util.SecureXmlFactories;
import org.w3c.dom.Document;

class WFSCountDefaultUtilTest {
  private static Document parse(String operationsMetadata, boolean namespaceAware) throws Exception {
    String xml = """
<wfs:WFS_Capabilities version="2.0.0" xmlns:wfs="http://www.opengis.net/wfs/2.0"
xmlns:ows="http://www.opengis.net/ows/1.1">
<ows:OperationsMetadata>%s</ows:OperationsMetadata>
</wfs:WFS_Capabilities>
""".formatted(operationsMetadata);
    DocumentBuilderFactory factory = SecureXmlFactories.newDocumentBuilderFactory();
    factory.setNamespaceAware(namespaceAware);
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  private static String constraint(String name, String defaultValue) {
    return """
<ows:Constraint name="%s"><ows:NoValues/><ows:DefaultValue>%s</ows:DefaultValue></ows:Constraint>
""".formatted(name, defaultValue);
  }

  private static String operation(String name, String constraints) {
    return "<ows:Operation name=\"%s\"><ows:DCP/>%s</ows:Operation>".formatted(name, constraints);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void operationsMetadataConstraint(boolean namespaceAware) throws Exception {
    Document doc = parse(
        operation("GetFeature", "")
            + constraint("ImplementsBasicWFS", "TRUE")
            + constraint("CountDefault", "50"),
        namespaceAware);
    assertEquals(50, WFSCountDefaultUtil.getCountDefault(doc));
  }

  @Test
  void getFeatureConstraintTakesPrecedence() throws Exception {
    Document doc = parse(
        constraint("CountDefault", "1000")
            + operation("GetPropertyValue", constraint("CountDefault", "10"))
            + operation("GetFeature", constraint("CountDefault", "50")),
        true);
    assertEquals(50, WFSCountDefaultUtil.getCountDefault(doc));
  }

  @Test
  void otherOperationConstraintIgnored() throws Exception {
    Document doc = parse(operation("GetPropertyValue", constraint("CountDefault", "10")), true);
    assertNull(WFSCountDefaultUtil.getCountDefault(doc));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "0", "-1", "unlimited", "1.5"})
  void invalidCountDefault(String value) throws Exception {
    assertNull(WFSCountDefaultUtil.getCountDefault(parse(constraint("CountDefault", value), true)));
  }

  @Test
  void noCountDefault() throws Exception {
    assertNull(WFSCountDefaultUtil.getCountDefault(parse(constraint("ImplementsResultPaging", "TRUE"), true)));
    assertNull(WFSCountDefaultUtil.getCountDefault((Document) null));
  }

  @Test
  void fromWfsDataStore() throws Exception {
    WFSGetCapabilities capabilities = mock(WFSGetCapabilities.class);
    when(capabilities.getRawDocument()).thenReturn(parse(constraint("CountDefault", " 1000 "), true));
    WFSClient client = mock(WFSClient.class);
    when(client.getCapabilities()).thenReturn(capabilities);
    WFSDataStore dataStore = mock(WFSDataStore.class);
    when(dataStore.getWfsClient()).thenReturn(client);
    assertEquals(1000, WFSCountDefaultUtil.getCountDefault(dataStore));
  }

  @Test
  void notAWfsDataStore() {
    assertNull(WFSCountDefaultUtil.getCountDefault(mock(DataStore.class)));
  }
}
