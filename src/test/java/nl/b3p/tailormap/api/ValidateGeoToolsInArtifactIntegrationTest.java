/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ValidateGeoToolsInArtifactIntegrationTest {

    private static final Log LOG =
            LogFactory.getLog(ValidateGeoToolsInArtifactIntegrationTest.class);
    /** this name is set in the pom file */
    private static final String RUNNABLE_JAR = "target/tailormap-api-exec.jar";

    /**
     * check if there is only one gt-main and only one gt-epsg jar in the runnable jar, so we can be
     * sure there are no or EPSG factory version conflicts. This test will fail if there are more
     * than one gt-main (versions) or gt-epsg (factory providers) in the runnable jar.
     */
    @Test
    void checkArtifact() {
        LOG.debug("Checking Tailormap API artifact: " + RUNNABLE_JAR + " for GeoTools artifacts.");
        try (ZipFile zipFile = new ZipFile(RUNNABLE_JAR)) {
            Enumeration<? extends ZipEntry> e = zipFile.entries();
            List<ZipEntry> gtMainJars = new ArrayList<>();
            List<ZipEntry> gtEPSGJars = new ArrayList<>();

            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                if (!entry.isDirectory()) {
                    if (entry.getName().contains("gt-main")) {
                        gtMainJars.add(entry);
                    }
                    if (entry.getName().contains("gt-epsg")) {
                        gtEPSGJars.add(entry);
                    }
                }
            }

            assertFalse(gtMainJars.isEmpty(), "No gt-main artifact in the runnable jar file");
            assertEquals(
                    1,
                    gtMainJars.size(),
                    "There are more than 1 gt-main artifacts in the runnable jar file");

            assertFalse(gtEPSGJars.isEmpty(), "No gt-epsg artifact in the runnable jar file");
            assertEquals(
                    1,
                    gtEPSGJars.size(),
                    "There are more than 1 gt-epsg artifacts in the runnable jar file");

        } catch (IOException e) {
            LOG.error(e);
            fail(e.getLocalizedMessage());
        }
    }
}
