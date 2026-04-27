package io.github.timeline_unlocker.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "TimelineUnlocker-X";

    private static final String FAKE_MCC_MNC = "310030";
    private static final String FAKE_ISO = "us";

    private static void log(String fmt, Object... args) {
        XposedBridge.log("[" + TAG + "] " + String.format(fmt, args));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        if (!"com.google.android.gms".equals(pkg)
                && !"com.google.android.gsf".equals(pkg)
                && !"com.google.android.apps.maps".equals(pkg)) {
            return;
        }

        log("loading package: %s (process=%s)", pkg, lpparam.processName);

        ClassLoader cl = lpparam.classLoader;
        hookTelephonyManager(cl);
        hookSystemProperties(cl);
    }

    private static XC_MethodReplacement constReplacement(final Object value, final String methodTag) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                Object[] args = param.args;
                if (args != null && args.length > 0) {
                    log("%s(args[0]=%s) -> %s", methodTag, String.valueOf(args[0]), value);
                } else {
                    log("%s() -> %s", methodTag, value);
                }
                return value;
            }
        };
    }

    private void hookTelephonyManager(ClassLoader cl) {
        Class<?> tm;
        try {
            tm = XposedHelpers.findClass("android.telephony.TelephonyManager", cl);
        } catch (Throwable t) {
            log("TelephonyManager not found: %s", t);
            return;
        }

        // 这些方法可能存在多种重载（无参 / int subId / String callingPackage 等）
        // 用 hookAllMethods 一网打尽，每个都返回伪造值。
        hookAllReturning(tm, "getSimCountryIso", FAKE_ISO);
        hookAllReturning(tm, "getNetworkCountryIso", FAKE_ISO);
        hookAllReturning(tm, "getSimOperator", FAKE_MCC_MNC);
        hookAllReturning(tm, "getNetworkOperator", FAKE_MCC_MNC);
        hookAllReturning(tm, "getSimOperatorNumeric", FAKE_MCC_MNC);
        hookAllReturning(tm, "getNetworkOperatorNumeric", FAKE_MCC_MNC);
        hookAllReturning(tm, "getSimOperatorNumericForPhone", FAKE_MCC_MNC);
        hookAllReturning(tm, "getNetworkOperatorForPhone", FAKE_MCC_MNC);
        hookAllReturning(tm, "getSimCountryIsoForPhone", FAKE_ISO);
        hookAllReturning(tm, "getNetworkCountryIsoForPhone", FAKE_ISO);
    }

    private void hookAllReturning(Class<?> clazz, String name, String value) {
        try {
            int hooked = XposedBridge.hookAllMethods(clazz, name, constReplacement(value, name)).size();
            if (hooked > 0) {
                log("hooked %d overload(s) of %s.%s -> %s",
                        hooked, clazz.getSimpleName(), name, value);
            }
        } catch (Throwable t) {
            // 没这个方法就跳过
        }
    }

    private void hookSystemProperties(ClassLoader cl) {
        Class<?> sp;
        try {
            sp = XposedHelpers.findClass("android.os.SystemProperties", cl);
        } catch (Throwable t) {
            log("SystemProperties not found: %s", t);
            return;
        }

        // 拦 SystemProperties.get(String) / get(String, String)，
        // 命中 gsm.(sim.)?operator.(numeric|iso-country) 才改值，其他原样返回。
        XC_MethodReplacement spHook = new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                String key = (String) param.args[0];
                String def = param.args.length > 1 ? (String) param.args[1] : "";
                String original = (String) XposedBridge.invokeOriginalMethod(
                        param.method, param.thisObject, param.args);
                String fake = fakeFor(key);
                if (fake != null) {
                    log("SystemProperties.get(%s) %s -> %s", key, original, fake);
                    return fake;
                }
                return original == null ? def : original;
            }
        };

        try {
            XposedHelpers.findAndHookMethod(sp, "get", String.class, spHook);
        } catch (Throwable t) {
            log("hook SystemProperties.get(String) failed: %s", t);
        }
        try {
            XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class, spHook);
        } catch (Throwable t) {
            log("hook SystemProperties.get(String,String) failed: %s", t);
        }
    }

    private static String fakeFor(String key) {
        if (key == null) return null;
        if (startsWithKey(key, "gsm.sim.operator.numeric")
                || startsWithKey(key, "gsm.operator.numeric")) {
            return FAKE_MCC_MNC;
        }
        if (startsWithKey(key, "gsm.sim.operator.iso-country")
                || startsWithKey(key, "gsm.operator.iso-country")) {
            return FAKE_ISO;
        }
        return null;
    }

    /** 精确匹配，或 prefix 后跟 .N / ,N 卡槽后缀。 */
    private static boolean startsWithKey(String key, String prefix) {
        if (!key.startsWith(prefix)) return false;
        int len = prefix.length();
        if (key.length() == len) return true;
        char c = key.charAt(len);
        if (c != '.' && c != ',') return false;
        if (key.length() == len + 1) return false;
        char d = key.charAt(len + 1);
        return d >= '0' && d <= '9';
    }
}
