/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.util.Set;

class UniqueValuesResponseTest {

    @Test
    void testUniqueValuesResponse() {
        UniqueValuesResponse uniqueValuesResponse = new UniqueValuesResponse().filterapplied(false);
        uniqueValuesResponse.addValuesItem("value 1");
        uniqueValuesResponse.addValuesItem("value 2");
        uniqueValuesResponse.addValuesItem("value 2");
        assertEquals(
                Set.of("value 1", "value 2"),
                uniqueValuesResponse.getValues(),
                "values don't match");
        assertFalse(uniqueValuesResponse.getFilterapplied(), "filterapplied should be true");
    }
}
