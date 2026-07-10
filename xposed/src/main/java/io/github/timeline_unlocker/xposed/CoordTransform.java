package io.github.timeline_unlocker.xposed;

/**
 * WGS-84 (GPS) -> GCJ-02 (火星坐标系) 转换。
 * 算法来自互联网公开版本，国内地图应用通用。
 */
public final class CoordTransform {

    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private CoordTransform() {}

    /** 粗略中国大陆边界判断；境外不做转换。 */
    public static boolean isInChina(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -90.0 && lat <= 90.0
                && lng >= 72.004 && lng <= 137.8347
                && lat >= 0.8293 && lat <= 55.8271;
    }

    /** 返回长度为 2 的数组 [latitude, longitude]，已转换为 GCJ-02。 */
    public static double[] wgs84ToGcj02(double lat, double lng) {
        double[] result = new double[2];
        wgs84ToGcj02(lat, lng, result);
        return result;
    }

    /** 将转换结果写入 result[0..1]，用于避免热路径临时数组分配。 */
    public static void wgs84ToGcj02(double lat, double lng, double[] result) {
        if (result == null || result.length < 2) {
            throw new IllegalArgumentException("result must contain at least two elements");
        }
        if (!isInChina(lat, lng)) {
            result[0] = lat;
            result[1] = lng;
            return;
        }
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        result[0] = lat + dLat;
        result[1] = lng + dLng;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y
                + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI)
                + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI)
                + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI)
                + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x
                + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI)
                + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI)
                + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI)
                + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }
}
