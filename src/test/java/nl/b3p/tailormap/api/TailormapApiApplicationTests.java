/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import nl.b3p.tailormap.api.controller.VersionController;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * testcases for {@link TailormapApiApplication}.
 *
 * @since 0.1
 */
@SpringBootTest
class TailormapApiApplicationTests {
    @Autowired private VersionController vController;

    @Test
    void contextLoads() {
        assertNotNull(vController, "vController should be initilialized");
    }
}
