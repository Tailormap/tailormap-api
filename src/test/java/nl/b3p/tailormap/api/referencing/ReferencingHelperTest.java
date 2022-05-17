package nl.b3p.tailormap.api.referencing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import nl.b3p.tailormap.api.geotools.referencing.ReferencingHelper;
import nl.b3p.tailormap.api.model.Bounds;

import org.junit.jupiter.api.Test;

public class ReferencingHelperTest {

    @Test
    void testcrsBoundsExtractorforRD() {
        Bounds b = ReferencingHelper.crsBoundsExtractor("EPSG:28992");
        assertNotNull(b, "bounds should not be null");
        assertEquals("EPSG:28992", b.getCrs(), "crs should match");
        assertEquals(306594.5, b.getMiny(), 0.1d);
        assertEquals(636981.7, b.getMaxy(), 0.1d);
        assertEquals(634.5, b.getMinx(), 0.1d);
        assertEquals(284300.1, b.getMaxx(), 0.1d);
    }

    @Test
    void testcrsBoundsExtractorforWGS84() {
        Bounds b = ReferencingHelper.crsBoundsExtractor("EPSG:4326");
        assertNotNull(b, "bounds should not be null");
        assertEquals("EPSG:4326", b.getCrs(), "crs should match");
        assertEquals(-180, b.getMiny(), 0.1d);
        assertEquals(180, b.getMaxy(), 0.1d);
        assertEquals(-90, b.getMinx(), 0.1d);
        assertEquals(90, b.getMaxx(), 0.1d);
    }
}
