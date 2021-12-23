/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */ package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MapControllerTest {
    @Autowired private MapController mapController;

    @Test
    void controller_should_not_be_null() {
        assertNotNull(mapController, "mapController should not be null");
    }
}
