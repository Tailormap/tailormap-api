/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.opengis.fes20.ConformanceType;
import net.opengis.fes20.Fes20Factory;
import net.opengis.fes20.FilterCapabilitiesType;
import net.opengis.ows11.DomainType;
import net.opengis.ows11.Ows11Factory;
import net.opengis.ows11.ValueType;
import net.opengis.wfs20.WFSCapabilitiesType;
import net.opengis.wfs20.Wfs20Factory;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SortingWFSClientTest {

  private static WFSGetCapabilities capabilities(String constraintName, String defaultValue) {
    WFSCapabilitiesType caps = Wfs20Factory.eINSTANCE.createWFSCapabilitiesType();
    FilterCapabilitiesType filterCapabilities = Fes20Factory.eINSTANCE.createFilterCapabilitiesType();
    ConformanceType conformance = Fes20Factory.eINSTANCE.createConformanceType();
    if (constraintName != null) {
      DomainType constraint = Ows11Factory.eINSTANCE.createDomainType();
      constraint.setName(constraintName);
      if (defaultValue != null) {
        ValueType value = Ows11Factory.eINSTANCE.createValueType();
        value.setValue(defaultValue);
        constraint.setDefaultValue(value);
      }
      conformance.getConstraint().add(constraint);
    }
    filterCapabilities.setConformance(conformance);
    caps.setFilterCapabilities(filterCapabilities);
    return WFSGetCapabilities.create(caps, null);
  }

  @ParameterizedTest(name = "#{index}: {0}={1} -> {2}")
  @CsvSource(
      nullValues = "null",
      value = {
        "ImplementsSorting, TRUE, true",
        "ImplementsSorting, true, true",
        "ImplementsSorting, ' TRUE ', true",
        "ImplementsSorting, FALSE, false",
        "ImplementsSorting, null, false",
        "ImplementsResultPaging, TRUE, false",
        "null, null, false",
      })
  void implements_sorting(String constraintName, String defaultValue, boolean expected) {
    assertEquals(expected, SortingWFSClient.implementsSorting(capabilities(constraintName, defaultValue)));
  }

  @Test
  void no_filter_capabilities() {
    WFSCapabilitiesType caps = Wfs20Factory.eINSTANCE.createWFSCapabilitiesType();
    assertFalse(SortingWFSClient.implementsSorting(WFSGetCapabilities.create(caps, null)));
  }
}
