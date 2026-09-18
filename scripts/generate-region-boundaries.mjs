#!/usr/bin/env node

import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const OUTPUT = resolve("xposed/src/main/java/io/github/timeline_unlocker/xposed/RegionBoundary.java");
const THRESHOLD = "0.00005";
const SOURCES = [
  { name: "HONG_KONG", relation: "R20044132" },
  { name: "MACAO", relation: "R1867188" },
  { name: "TAIWAN", relation: "R449220" },
];

async function fetchBoundary({ relation }) {
  const url = new URL("https://nominatim.openstreetmap.org/lookup");
  url.searchParams.set("osm_ids", relation);
  url.searchParams.set("format", "jsonv2");
  url.searchParams.set("polygon_geojson", "1");
  url.searchParams.set("polygon_threshold", THRESHOLD);

  const response = await fetch(url, {
    headers: {
      "User-Agent": "ReLocationReportEnabler boundary data generator (GitHub: stmtc233/ReLocationReportEnabler)",
    },
  });
  if (!response.ok) throw new Error(`Nominatim lookup failed for ${relation}: ${response.status}`);
  const [result] = await response.json();
  if (!result?.geojson) throw new Error(`No boundary returned for ${relation}`);
  return result.geojson;
}

function asMultiPolygon({ type, coordinates }) {
  if (type === "Polygon") return [coordinates];
  if (type === "MultiPolygon") return coordinates;
  throw new Error(`Unsupported GeoJSON geometry: ${type}`);
}

function javaNumber(value) {
  return Number(value).toFixed(7).replace(/0+$/, "").replace(/\.$/, ".0");
}

function javaMultiPolygon(geometry) {
  return asMultiPolygon(geometry)
    .map((polygon) => `        {\n${polygon.map((ring) => `            {${ring.map(([lng, lat]) => `{${javaNumber(lat)}, ${javaNumber(lng)}}`).join(", ")}}`).join(",\n")}\n        }`)
    .join(",\n");
}

function bounds(geometry) {
  const values = asMultiPolygon(geometry).flat(2);
  const lats = values.map(([, lat]) => lat);
  const lngs = values.map(([lng]) => lng);
  return [Math.min(...lats), Math.max(...lats), Math.min(...lngs), Math.max(...lngs)];
}

function polygonBounds(geometry) {
  return asMultiPolygon(geometry).map(([outerRing]) => {
    const lats = outerRing.map(([, lat]) => lat);
    const lngs = outerRing.map(([lng]) => lng);
    return [Math.min(...lats), Math.max(...lats), Math.min(...lngs), Math.max(...lngs)];
  });
}

const geometries = await Promise.all(SOURCES.map(fetchBoundary));
const generated = `package io.github.timeline_unlocker.xposed;

/**
 * Administrative boundaries generated from OpenStreetMap through Nominatim.
 * Source relations: Hong Kong R20044132, Macao R1867188, Taiwan R449220.
 * Geometry is simplified by 0.00005 degrees for size. See NOTICE and
 * scripts/generate-region-boundaries.mjs for attribution and regeneration.
 */
final class RegionBoundary {

    private static final double[][][][] HONG_KONG = {
${javaMultiPolygon(geometries[0])}
    };
    private static final double[] HONG_KONG_BOUNDS = {${bounds(geometries[0]).map(javaNumber).join(", ")}};
    private static final double[][] HONG_KONG_POLYGON_BOUNDS = {${polygonBounds(geometries[0]).map((value) => `{${value.map(javaNumber).join(", ")}}`).join(", ")}};

    private static final double[][][][] MACAO = {
${javaMultiPolygon(geometries[1])}
    };
    private static final double[] MACAO_BOUNDS = {${bounds(geometries[1]).map(javaNumber).join(", ")}};
    private static final double[][] MACAO_POLYGON_BOUNDS = {${polygonBounds(geometries[1]).map((value) => `{${value.map(javaNumber).join(", ")}}`).join(", ")}};

    private static final double[][][][] TAIWAN = {
${javaMultiPolygon(geometries[2])}
    };
    private static final double[] TAIWAN_BOUNDS = {${bounds(geometries[2]).map(javaNumber).join(", ")}};
    private static final double[][] TAIWAN_POLYGON_BOUNDS = {${polygonBounds(geometries[2]).map((value) => `{${value.map(javaNumber).join(", ")}}`).join(", ")}};

    private RegionBoundary() {}

    static boolean isExcludedWgs84Region(double lat, double lng) {
        return contains(lat, lng, HONG_KONG_BOUNDS, HONG_KONG_POLYGON_BOUNDS, HONG_KONG)
                || contains(lat, lng, MACAO_BOUNDS, MACAO_POLYGON_BOUNDS, MACAO)
                || contains(lat, lng, TAIWAN_BOUNDS, TAIWAN_POLYGON_BOUNDS, TAIWAN);
    }

    private static boolean contains(
            double lat, double lng, double[] bounds, double[][] polygonBounds, double[][][][] multiPolygon) {
        if (lat < bounds[0] || lat > bounds[1] || lng < bounds[2] || lng > bounds[3]) {
            return false;
        }
        for (int polygonIndex = 0; polygonIndex < multiPolygon.length; polygonIndex++) {
            double[] polygonBound = polygonBounds[polygonIndex];
            if (lat < polygonBound[0] || lat > polygonBound[1]
                    || lng < polygonBound[2] || lng > polygonBound[3]) {
                continue;
            }
            double[][][] polygon = multiPolygon[polygonIndex];
            if (!isInsideRing(lat, lng, polygon[0])) continue;
            boolean inHole = false;
            for (int i = 1; i < polygon.length; i++) {
                if (isInsideRing(lat, lng, polygon[i])) {
                    inHole = true;
                    break;
                }
            }
            if (!inHole) return true;
        }
        return false;
    }

    /** Ray-casting containment test. Polygon points are {latitude, longitude}. */
    private static boolean isInsideRing(double lat, double lng, double[][] ring) {
        boolean inside = false;
        for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            double latI = ring[i][0];
            double lngI = ring[i][1];
            double latJ = ring[j][0];
            double lngJ = ring[j][1];
            boolean crossesLatitude = (latI > lat) != (latJ > lat);
            if (crossesLatitude && lng < (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI) {
                inside = !inside;
            }
        }
        return inside;
    }
}
`;

await mkdir(dirname(OUTPUT), { recursive: true });
await writeFile(OUTPUT, generated);
