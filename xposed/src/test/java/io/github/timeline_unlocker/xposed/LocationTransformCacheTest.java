package io.github.timeline_unlocker.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationTransformCacheTest {

    private static final Object KEY = new Object();

    @Test
    public void firstUpdateTransformsCoordinates() {
        LocationTransformCache cache = new LocationTransformCache();

        assertTrue(cache.update(KEY, 39.908823, 116.397470));
        assertEquals(39.910226, cache.transformedLatitude(), 0.000001);
        assertEquals(116.403714, cache.transformedLongitude(), 0.000001);
    }

    @Test
    public void sameKeyAndCoordinatesHitCache() {
        LocationTransformCache cache = new LocationTransformCache();
        cache.update(KEY, 39.908823, 116.397470);

        assertFalse(cache.update(KEY, 39.908823, 116.397470));
        assertEquals(39.910226, cache.transformedLatitude(), 0.000001);
        assertEquals(116.403714, cache.transformedLongitude(), 0.000001);
    }

    @Test
    public void changedCoordinatesRecompute() {
        LocationTransformCache cache = new LocationTransformCache();
        cache.update(KEY, 39.908823, 116.397470);

        assertTrue(cache.update(KEY, 31.2304, 121.4737));
        assertEquals(31.228458, cache.transformedLatitude(), 0.000001);
        assertEquals(121.478223, cache.transformedLongitude(), 0.000001);
    }

    @Test
    public void differentKeyRecomputes() {
        LocationTransformCache cache = new LocationTransformCache();
        cache.update(KEY, 39.908823, 116.397470);

        assertTrue(cache.update(new Object(), 39.908823, 116.397470));
    }

    @Test
    public void nonChinaCoordinatesPassThrough() {
        LocationTransformCache cache = new LocationTransformCache();

        assertTrue(cache.update(KEY, 37.7749, -122.4194));
        assertEquals(37.7749, cache.transformedLatitude(), 0.0);
        assertEquals(-122.4194, cache.transformedLongitude(), 0.0);
    }

    @Test
    public void readingBeforeUpdateFails() {
        LocationTransformCache cache = new LocationTransformCache();

        assertThrows(IllegalStateException.class, cache::transformedLatitude);
        assertThrows(IllegalStateException.class, cache::transformedLongitude);
    }
}
