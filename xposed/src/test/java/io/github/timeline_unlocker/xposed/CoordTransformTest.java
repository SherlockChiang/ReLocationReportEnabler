package io.github.timeline_unlocker.xposed;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CoordTransformTest {

    @Test
    public void rejectsInvalidAndNonChinaCoordinates() {
        assertFalse(CoordTransform.isInChina(Double.NaN, 116.4));
        assertFalse(CoordTransform.isInChina(39.9, Double.POSITIVE_INFINITY));
        assertFalse(CoordTransform.isInChina(91.0, 116.4));
        assertFalse(CoordTransform.isInChina(37.8, -122.4));
    }

    @Test
    public void transformsKnownBeijingCoordinate() {
        double[] result = CoordTransform.wgs84ToGcj02(39.908823, 116.397470);

        assertArrayEquals(new double[]{39.910226, 116.403714}, result, 0.000001);
    }

    @Test
    public void writesIntoProvidedResult() {
        double[] result = new double[2];
        CoordTransform.wgs84ToGcj02(37.7749, -122.4194, result);

        assertArrayEquals(new double[]{37.7749, -122.4194}, result, 0.0);
    }

    @Test
    public void rejectsSmallResultBuffer() {
        assertThrows(IllegalArgumentException.class,
                () -> CoordTransform.wgs84ToGcj02(39.9, 116.4, new double[1]));
    }

    @Test
    public void includesBoundingBoxEdges() {
        assertTrue(CoordTransform.isInChina(0.8293, 72.004));
        assertTrue(CoordTransform.isInChina(55.8271, 137.8347));
    }
}
