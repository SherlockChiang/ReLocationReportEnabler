package io.github.timeline_unlocker.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SpoofedSystemPropertiesTest {

    @Test
    public void matchesExactAndNumericSlotKeys() {
        assertEquals("310030", valueFor("gsm.operator.numeric"));
        assertEquals("310030", valueFor("gsm.sim.operator.numeric.0"));
        assertEquals("us", valueFor("gsm.operator.iso-country,12"));
    }

    @Test
    public void rejectsMalformedAndUnrelatedKeys() {
        assertNull(valueFor(null));
        assertNull(valueFor("gsm.operator.numeric."));
        assertNull(valueFor("gsm.operator.numeric.0anything"));
        assertNull(valueFor("gsm.operator.alpha"));
    }

    private static String valueFor(String key) {
        return SpoofedSystemProperties.valueFor(key, "310030", "us");
    }
}
