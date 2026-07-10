package io.github.timeline_unlocker.xposed;

final class SpoofedSystemProperties {

    private SpoofedSystemProperties() {}

    static String valueFor(String key, String mccMnc, String iso) {
        if (key == null) return null;
        if (matches(key, "gsm.sim.operator.numeric")
                || matches(key, "gsm.operator.numeric")) {
            return mccMnc;
        }
        if (matches(key, "gsm.sim.operator.iso-country")
                || matches(key, "gsm.operator.iso-country")) {
            return iso;
        }
        return null;
    }

    private static boolean matches(String key, String prefix) {
        if (!key.startsWith(prefix)) return false;
        int len = prefix.length();
        if (key.length() == len) return true;
        char separator = key.charAt(len);
        if (separator != '.' && separator != ',') return false;
        if (key.length() == len + 1) return false;
        for (int i = len + 1; i < key.length(); i++) {
            char digit = key.charAt(i);
            if (digit < '0' || digit > '9') return false;
        }
        return true;
    }
}
