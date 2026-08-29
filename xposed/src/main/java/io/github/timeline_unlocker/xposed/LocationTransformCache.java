package io.github.timeline_unlocker.xposed;

/**
 * Location 坐标转换的按对象缓存：同一个 Location 对象且 lat/lng 未变时
 * 复用上次转换结果，避免 getLatitude/getLongitude 高频调用反复计算。
 * key 只用作引用相等判断（MainHook 传 Location 实例）。
 */
final class LocationTransformCache {

    private final double[] transformed = new double[2];
    private Object key;
    private double lat;
    private double lng;
    private boolean valid;

    /** 记录一次读取；返回 true 表示重新计算了坐标，false 表示命中缓存。 */
    boolean update(Object key, double lat, double lng) {
        if (valid && this.key == key
                && Double.compare(this.lat, lat) == 0
                && Double.compare(this.lng, lng) == 0) {
            return false;
        }
        this.key = key;
        this.lat = lat;
        this.lng = lng;
        CoordTransform.wgs84ToGcj02(lat, lng, transformed);
        valid = true;
        return true;
    }

    double transformedLatitude() {
        requireValid();
        return transformed[0];
    }

    double transformedLongitude() {
        requireValid();
        return transformed[1];
    }

    private void requireValid() {
        if (!valid) {
            throw new IllegalStateException("update() must be called before reading results");
        }
    }
}
