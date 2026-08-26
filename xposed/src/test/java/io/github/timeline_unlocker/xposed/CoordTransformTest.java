package io.github.timeline_unlocker.xposed;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CoordTransformTest {

    @Test
    public void rejectsInvalidAndNonChinaCoordinates() {
        assertFalse(CoordTransform.shouldApplyGcj02(Double.NaN, 116.4));
        assertFalse(CoordTransform.shouldApplyGcj02(39.9, Double.POSITIVE_INFINITY));
        assertFalse(CoordTransform.shouldApplyGcj02(91.0, 116.4));
        assertFalse(CoordTransform.shouldApplyGcj02(37.8, -122.4));
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
    public void appliesCompensationAtMainlandBoundingBoxEdges() {
        assertTrue(CoordTransform.shouldApplyGcj02(0.8293, 72.004));
        assertTrue(CoordTransform.shouldApplyGcj02(55.8271, 137.8347));
    }

    @Test
    public void excludesHongKongMacaoAndTaiwan() {
        assertFalse(CoordTransform.shouldApplyGcj02(22.3193, 114.1694)); // Hong Kong
        assertFalse(CoordTransform.shouldApplyGcj02(22.1987, 113.5439)); // Macao
        assertFalse(CoordTransform.shouldApplyGcj02(25.0330, 121.5654)); // Taipei
        assertFalse(CoordTransform.shouldApplyGcj02(22.6273, 120.3014)); // Kaohsiung
        assertFalse(CoordTransform.shouldApplyGcj02(23.5700, 119.5800)); // Penghu
        assertFalse(CoordTransform.shouldApplyGcj02(24.4400, 118.3200)); // Kinmen
        assertFalse(CoordTransform.shouldApplyGcj02(26.1600, 119.9500)); // Matsu
    }

    @Test
    public void leavesExcludedRegionCoordinatesUnchanged() {
        double[] result = CoordTransform.wgs84ToGcj02(22.3193, 114.1694);

        assertArrayEquals(new double[]{22.3193, 114.1694}, result, 0.0);
    }

    @Test
    public void keepsCompensationForAdjacentMainlandCoordinates() {
        assertTrue(CoordTransform.shouldApplyGcj02(22.5431, 114.0579)); // Shenzhen
        assertTrue(CoordTransform.shouldApplyGcj02(22.2700, 113.5760)); // Zhuhai
    }
}
