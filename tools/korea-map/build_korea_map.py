#!/usr/bin/env python3
"""
Builds the app's embedded Korea map (시·도 and 시·군·구 outlines) from vuski/admdongkor.

Input : vuski/admdongkor ver20260701 행정동 boundaries (GeoJSON, WGS84), a corrected edition of the
        통계청 통계지리정보서비스(SGIS) 행정동 경계. SGIS data: 공공누리 제1유형(출처표시); admdongkor edits: CC BY 4.0.
Output: shared/src/commonMain/kotlin/com/coffeejournal/domain/reference/KoreaMapData.kt
        + KoreaMapProvince<code>.kt (one per 시·도, the 시·군·구 outlines).

Steps
  1. read the GeoJSON (downloaded when --input is not a file) and record its SHA-256;
  2. project every 행정동 to app units: x = (lng − 124.5)·cos 36°·10000, y = (39 − lat)·10000
     (equirectangular for the Korean peninsula; one unit is about 11 m in both directions);
  3. union the 행정동 into 시·군·구 by `sgg` and remove the few square-metre overlaps between neighbours, so the
     districts form a valid polygon coverage;
  4. drop specks: island parts and holes smaller than --min-km2 (Dokdo is always kept);
  5. simplify with GEOS coverage simplification (shared borders stay shared, no gaps or slivers):
     the 시·군·구 of each 시·도 with a tolerance scaled to the 시·도's size, and the 16 시·도 (the union of their
     districts) with --province-tol for the national map;
  6. snap to the integer unit grid, compute label points (pole of inaccessibility of the largest part) and write
     Kotlin. Rings are delta-encoded: zig-zag varints in 5-bit groups over the URL-safe base64 alphabet, the first
     point of a ring absolute, rings separated by a space. Each string constant stays far below the JVM's 65535-byte
     constant limit (the script fails if one gets near it).

Usage
  pip install shapely            # tooling only; nothing of it ships in the app
  python3 tools/korea-map/build_korea_map.py [--input hjd.geojson] [--preview out_dir]
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import math
import os
import re
import sys
import urllib.request

import numpy as np
import shapely
from shapely.geometry import MultiPolygon, Polygon, shape
from shapely.ops import polylabel, unary_union
from shapely.strtree import STRtree

SOURCE_URL = "https://raw.githubusercontent.com/vuski/admdongkor/master/ver20260701/HangJeongDong_ver20260701.geojson"
SOURCE_VERSION = "ver20260701"
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(REPO, "shared/src/commonMain/kotlin/com/coffeejournal/domain/reference")

LNG0 = 124.5
LAT0 = 39.0
SCALE = 10000.0
COS36 = math.cos(math.radians(36.0))
UNIT_KM = 1.0 / SCALE * 111.32  # one unit of latitude (and, after the cos 36° correction, of longitude) in km

ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
MAX_CONST_BYTES = 60_000

# Short names for the national map's labels (the web and everyday use: 서울, 경기, 충북 …).
SHORT = {
    "서울특별시": "서울", "부산광역시": "부산", "대구광역시": "대구", "인천광역시": "인천", "광주광역시": "광주",
    "대전광역시": "대전", "울산광역시": "울산", "세종특별자치시": "세종", "경기도": "경기", "강원특별자치도": "강원",
    "충청북도": "충북", "충청남도": "충남", "전북특별자치도": "전북", "전라남도": "전남", "경상북도": "경북",
    "경상남도": "경남", "제주특별자치도": "제주", "전남광주통합특별시": "전남광주",
}

# the five districts of the former 광주광역시 inside 전남광주통합특별시 (2026-07): "광주" in a location text means them
GWANGJU_FORMER = ("동구", "서구", "남구", "북구", "광산구")


def project(coords: np.ndarray) -> np.ndarray:
    out = np.empty_like(coords)
    out[:, 0] = (coords[:, 0] - LNG0) * COS36 * SCALE
    out[:, 1] = (LAT0 - coords[:, 1]) * SCALE
    return out


def unproject(x: float, y: float) -> tuple[float, float]:
    return (x / (COS36 * SCALE) + LNG0, LAT0 - y / SCALE)


def km2(area_units: float) -> float:
    return area_units * UNIT_KM * UNIT_KM


def parts(g) -> list[Polygon]:
    if g is None or g.is_empty:
        return []
    if g.geom_type == "Polygon":
        return [g]
    return [p for p in getattr(g, "geoms", []) if p.geom_type == "Polygon"]


def is_dokdo(p: Polygon) -> bool:
    lng, lat = unproject(*p.centroid.coords[0])
    return 131.8 < lng < 131.95 and 37.2 < lat < 37.3


def read_source(path: str | None) -> tuple[bytes, str]:
    if path and os.path.isfile(path):
        data = open(path, "rb").read()
        where = path
    else:
        print(f"downloading {SOURCE_URL}", file=sys.stderr)
        with urllib.request.urlopen(SOURCE_URL, timeout=120) as r:
            data = r.read()
        where = SOURCE_URL
        if path:
            with open(path, "wb") as f:
                f.write(data)
    return data, where


def zigzag(v: int) -> int:
    return (v << 1) ^ (v >> 63) if v < 0 else v << 1


def varint(v: int, out: list[str]) -> None:
    z = zigzag(v)
    while z >= 32:
        out.append(ALPHABET[32 | (z & 31)])
        z >>= 5
    out.append(ALPHABET[z])


def quantize_ring(coords) -> list[tuple[int, int]]:
    pts: list[tuple[int, int]] = []
    for x, y in coords:
        q = (int(round(x)), int(round(y)))
        if not pts or pts[-1] != q:
            pts.append(q)
    if len(pts) > 1 and pts[0] == pts[-1]:
        pts.pop()
    return pts


def encode(geom) -> tuple[str, int]:
    """Rings of every part (outer and holes; the app fills even-odd) → encoded string, point count."""
    rings: list[str] = []
    n = 0
    for p in parts(geom):
        for ring in [p.exterior, *p.interiors]:
            pts = quantize_ring(ring.coords)
            if len(set(pts)) < 3:
                continue
            out: list[str] = []
            px = py = 0
            for x, y in pts:
                varint(x - px, out)
                varint(y - py, out)
                px, py = x, y
            rings.append("".join(out))
            n += len(pts)
    return " ".join(rings), n


def decode(s: str) -> list[list[tuple[int, int]]]:
    """Mirror of the Kotlin decoder; used to check the round trip and to draw previews."""
    rings = []
    for chunk in s.split(" "):
        vals = []
        z = shift = 0
        for ch in chunk:
            c = ALPHABET.index(ch)
            z |= (c & 31) << shift
            if c & 32:
                shift += 5
            else:
                vals.append((z >> 1) ^ -(z & 1))
                z = shift = 0
        pts = []
        x = y = 0
        for i in range(0, len(vals), 2):
            x += vals[i]
            y += vals[i + 1]
            pts.append((x, y))
        rings.append(pts)
    return rings


def label_point(geom) -> tuple[int, int]:
    biggest = max(parts(geom), key=lambda p: p.area)
    tol = max(1.0, math.sqrt(biggest.area) / 200.0)
    pt = polylabel(biggest, tolerance=tol)
    return int(round(pt.x)), int(round(pt.y))


def main_frame(geom, reach_km: float) -> tuple[float, float, float, float]:
    """Bounds of the largest part plus every part within [reach_km] of the growing cluster (far islands such as
    Ulleungdo/Dokdo or the Ongjin islands stay out, so the 시·도 view opens on its mainland)."""
    ps = sorted(parts(geom), key=lambda p: -p.area)
    reach = reach_km / UNIT_KM
    x0, y0, x1, y1 = ps[0].bounds
    rest = ps[1:]
    grown = True
    while grown:
        grown = False
        for p in list(rest):
            a, b, c, d = p.bounds
            if a <= x1 + reach and c >= x0 - reach and b <= y1 + reach and d >= y0 - reach:
                x0, y0, x1, y1 = min(x0, a), min(y0, b), max(x1, c), max(y1, d)
                rest.remove(p)
                grown = True
    return x0, y0, x1, y1


def kstr(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def check_const(name: str, s: str) -> None:
    size = len(s.encode("utf-8"))
    if size > MAX_CONST_BYTES:
        raise SystemExit(f"{name} is {size} bytes; split it (JVM constants must stay under 65535 bytes)")


def display_district(sggnm: str) -> tuple[str, str | None]:
    """'수원시장안구' → ('수원시 장안구', '수원시'); plain names have no parent city."""
    m = re.match(r"^(.+?시)(.+구)$", sggnm)
    if m:
        return f"{m.group(1)} {m.group(2)}", m.group(1)
    return sggnm, None


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--input", help="local copy of the GeoJSON (downloaded from the source URL when missing)")
    ap.add_argument("--out", default=OUT_DIR, help="output directory for the Kotlin files")
    ap.add_argument("--min-km2", type=float, default=0.5, help="drop island parts and holes smaller than this (km²)")
    ap.add_argument("--province-tol", type=float, default=45.0, help="시·도 outline tolerance for the national map (units ≈ 11 m)")
    ap.add_argument("--district-div", type=float, default=700.0, help="시·군·구 tolerance = 시·도 extent / this")
    ap.add_argument("--district-min-tol", type=float, default=7.0, help="smallest 시·군·구 tolerance (units)")
    ap.add_argument("--reach-km", type=float, default=35.0, help="islands farther than this from a 시·도's mainland cluster start outside its opening frame")
    ap.add_argument("--preview", help="write PNG previews (needs Pillow) into this directory")
    args = ap.parse_args()

    raw, where = read_source(args.input)
    sha256 = hashlib.sha256(raw).hexdigest()
    src = json.loads(raw)
    feats = src["features"]

    # ── 1. project and union 행정동 → 시·군·구 ──
    by_sgg: dict[str, list] = collections.defaultdict(list)
    meta: dict[str, tuple[str, str, str]] = {}
    sido_names: dict[str, str] = {}
    for f in feats:
        p = f["properties"]
        g = shape(f["geometry"])
        if not g.is_valid:
            g = shapely.make_valid(g)
        by_sgg[p["sgg"]].append(shapely.transform(g, project))
        meta[p["sgg"]] = (p["sido"], p["sidonm"], p["sggnm"])
        sido_names[p["sido"]] = p["sidonm"]
    codes = sorted(by_sgg)
    geoms = [unary_union(by_sgg[c]) for c in codes]

    # neighbours overlapping by a few square metres would make the coverage invalid
    tree = STRtree(geoms)
    fixed = 0
    for i, j in zip(*tree.query(geoms, predicate="intersects")):
        if i < j and geoms[i].intersection(geoms[j]).area > 0:
            geoms[j] = geoms[j].difference(geoms[i])
            fixed += 1

    # ── 2. drop specks (tiny islets and holes); Dokdo always stays ──
    def clean(g):
        keep = []
        for poly in parts(g):
            if km2(poly.area) >= args.min_km2 or is_dokdo(poly):
                holes = [h for h in poly.interiors if km2(Polygon(h).area) >= args.min_km2]
                keep.append(Polygon(poly.exterior, holes))
        return MultiPolygon(keep)

    raw_parts = sum(len(parts(g)) for g in geoms)
    geoms = [clean(g) for g in geoms]
    kept_parts = sum(len(parts(g)) for g in geoms)
    if not shapely.coverage_is_valid(geoms):
        raise SystemExit("the 시·군·구 do not form a valid coverage")
    district = dict(zip(codes, geoms))

    # ── 3. simplify: 시·도 for the national map, 시·군·구 per 시·도 ──
    sido_codes = sorted(sido_names, key=lambda c: min(k for k in codes if meta[k][0] == c))
    sido_geoms = [unary_union([district[k] for k in codes if meta[k][0] == s]) for s in sido_codes]
    sido_simple = dict(zip(sido_codes, shapely.coverage_simplify(sido_geoms, args.province_tol)))
    district_simple: dict[str, object] = {}
    district_tol: dict[str, float] = {}
    frames: dict[str, tuple[float, float, float, float]] = {}
    for s, whole in zip(sido_codes, sido_geoms):
        ks = [k for k in codes if meta[k][0] == s]
        b = main_frame(whole, args.reach_km)
        frames[s] = b
        tol = max(args.district_min_tol, max(b[2] - b[0], b[3] - b[1]) / args.district_div)
        district_tol[s] = tol
        for k, g in zip(ks, shapely.coverage_simplify([district[k] for k in ks], tol)):
            district_simple[k] = g

    # ── 4. encode ──
    total_points = 0
    provinces = []
    for s in sido_codes:
        enc, n = encode(sido_simple[s])
        total_points += n
        check_const(f"province {s}", enc)
        lx, ly = label_point(sido_simple[s])
        fb = frames[s]
        ab = sido_geoms[sido_codes.index(s)].bounds
        provinces.append(dict(
            code=s, name=sido_names[s], short=SHORT.get(sido_names[s], sido_names[s]), lx=lx, ly=ly, outline=enc, points=n,
            frame=[int(fb[0]), int(fb[1]), int(math.ceil(fb[2] - fb[0])), int(math.ceil(fb[3] - fb[1]))],
            bounds=[int(ab[0]), int(ab[1]), int(math.ceil(ab[2] - ab[0])), int(math.ceil(ab[3] - ab[1]))],
        ))
    districts: dict[str, list[dict]] = collections.defaultdict(list)
    cities: dict[tuple[str, str], list[str]] = collections.defaultdict(list)
    for k in codes:
        s, _, sggnm = meta[k]
        enc, n = encode(district_simple[k])
        if not enc:
            raise SystemExit(f"district {k} {sggnm} lost every ring")
        total_points += n
        check_const(f"district {k}", enc)
        # round trip
        dec = decode(enc)
        assert sum(len(r) for r in dec) == n
        name, city = display_district(sggnm)
        if city:
            cities[(s, city)].append(k)
        lx, ly = label_point(district_simple[k])
        districts[s].append(dict(code=k, name=name, lx=lx, ly=ly, outline=enc, points=n))
    gwangju = [k for k in codes if sido_names[meta[k][0]] == "전남광주통합특별시" and meta[k][2] in GWANGJU_FORMER]
    city_rows = []
    for (s, city), ks in sorted(cities.items()):
        lx, ly = label_point(unary_union([district_simple[k] for k in ks]))
        city_rows.append(dict(sido=s, name=city, lx=lx, ly=ly, codes=ks))
    if len(gwangju) == len(GWANGJU_FORMER):
        s = meta[gwangju[0]][0]
        lx, ly = label_point(unary_union([district_simple[k] for k in gwangju]))
        city_rows.append(dict(sido=s, name="광주", lx=lx, ly=ly, codes=gwangju))

    b = shapely.total_bounds(list(sido_simple.values()))
    margin = 800
    view = (int(b[0]) - margin, int(b[1]) - margin, int(b[2] - b[0]) + 2 * margin, int(b[3] - b[1]) + 2 * margin)

    header = [
        "// GENERATED by tools/korea-map/build_korea_map.py. Do not edit by hand; re-run the script instead.",
        f"// Source: {SOURCE_URL}",
        f"// Version: vuski/admdongkor {SOURCE_VERSION} (행정동 경계, 2026-07-01 기준), input SHA-256 {sha256}",
        "// Licenses: 통계청 통계지리정보서비스(SGIS) 행정구역 경계 — 공공누리 제1유형(출처표시);",
        "//           vuski/admdongkor 보정본 — CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).",
        "// Processing: 행정동 → 시·군·구(sgg)·시·도(sido) 병합, GEOS coverage simplification, 앱 좌표 투영"
        f" (x = (lng − {LNG0})·cos 36°·{int(SCALE)}, y = ({LAT0} − lat)·{int(SCALE)}).",
    ]
    files: dict[str, str] = {}
    lines = header + [
        "package com.coffeejournal.domain.reference",
        "",
        "/**",
        f" * Korea's {len(provinces)} 시·도 and {len(codes)} 시·군·구 (일반구 separately) as simplified outlines in map units.",
        " * Outlines are delta-encoded rings (see [KoreaMapData.decodeRings]); holes are separate rings, fill even-odd.",
        " */",
        "object KoreaMapData {",
        f"    const val SOURCE_URL: String = {kstr(SOURCE_URL)}",
        f"    const val SOURCE_VERSION: String = {kstr(SOURCE_VERSION)}",
        f"    const val SOURCE_SHA256: String = {kstr(sha256)}",
        "",
        "    /** Projection origin and scale: x = (lng − LNG0)·cos 36°·SCALE, y = (LAT0 − lat)·SCALE. */",
        f"    const val LNG0: Double = {LNG0}",
        f"    const val LAT0: Double = {LAT0}",
        f"    const val SCALE: Double = {SCALE}",
        "",
        "    /** The national map's frame (map units), with a margin around every 시·도 including Dokdo. */",
        f"    const val VIEW_X: Float = {view[0]}f",
        f"    const val VIEW_Y: Float = {view[1]}f",
        f"    const val VIEW_W: Float = {view[2]}f",
        f"    const val VIEW_H: Float = {view[3]}f",
        "",
        "    /**",
        "     * One 시·도. [frame] (x, y, w, h in map units) is its mainland with the islands near it, where the 시·도 view opens;",
        "     * [bounds] also holds far islands (Ulleungdo and Dokdo, the Ongjin islands), reached by zooming out.",
        "     */",
        "    class Province(",
        "        val code: String, val name: String, val short: String, val labelX: Int, val labelY: Int,",
        "        val frame: IntArray, val bounds: IntArray, val outline: String,",
        "    )",
        "    class District(val code: String, val name: String, val labelX: Int, val labelY: Int, val outline: String)",
        "    /** A city made of several 일반구 (or the former 광주광역시), for location texts that name only the city. */",
        "    class City(val provinceCode: String, val name: String, val labelX: Int, val labelY: Int, val districtCodes: List<String>)",
        "",
        "    val provinces: List<Province> = listOf(",
    ]
    for p in provinces:
        fr = ", ".join(str(v) for v in p["frame"])
        bd = ", ".join(str(v) for v in p["bounds"])
        lines.append(
            f"        Province({kstr(p['code'])}, {kstr(p['name'])}, {kstr(p['short'])}, {p['lx']}, {p['ly']}, "
            f"intArrayOf({fr}), intArrayOf({bd}), OUTLINE_{p['code']}),"
        )
    lines += ["    )", "", "    /** The 시·군·구 of the 시·도 [provinceCode], empty for an unknown code. */", "    fun districts(provinceCode: String): List<District> = when (provinceCode) {"]
    for p in provinces:
        lines.append(f"        {kstr(p['code'])} -> KoreaMapProvince{p['code']}.districts")
    lines += ["        else -> emptyList()", "    }", "", "    val cities: List<City> = listOf("]
    for c in city_rows:
        codes_kt = ", ".join(kstr(k) for k in c["codes"])
        lines.append(f"        City({kstr(c['sido'])}, {kstr(c['name'])}, {c['lx']}, {c['ly']}, listOf({codes_kt})),")
    lines += ["    )", ""]
    for p in provinces:
        lines.append(f"    private const val OUTLINE_{p['code']}: String = {kstr(p['outline'])}")
    lines += [
        "",
        "    private const val ALPHABET: String = " + kstr(ALPHABET),
        "",
        "    /** Decodes an outline into rings of (x, y) map-unit pairs: zig-zag varints, 5 bits per character, deltas after the first point. */",
        "    fun decodeRings(encoded: String): List<IntArray> {",
        "        val rings = ArrayList<IntArray>()",
        "        for (chunk in encoded.split(' ')) {",
        "            if (chunk.isEmpty()) continue",
        "            val values = IntArray(chunk.length)",
        "            var count = 0",
        "            var z = 0",
        "            var shift = 0",
        "            for (ch in chunk) {",
        "                val c = ALPHABET.indexOf(ch)",
        "                require(c >= 0) { \"bad outline character '$ch'\" }",
        "                z = z or ((c and 31) shl shift)",
        "                if (c and 32 != 0) shift += 5 else { values[count++] = (z ushr 1) xor -(z and 1); z = 0; shift = 0 }",
        "            }",
        "            val ring = IntArray(count - count % 2)",
        "            var x = 0",
        "            var y = 0",
        "            var i = 0",
        "            while (i + 1 < count) {",
        "                x += values[i]; y += values[i + 1]",
        "                ring[i] = x; ring[i + 1] = y",
        "                i += 2",
        "            }",
        "            rings.add(ring)",
        "        }",
        "        return rings",
        "    }",
        "}",
        "",
    ]
    files["KoreaMapData.kt"] = "\n".join(lines)
    for p in provinces:
        rows = districts[p["code"]]
        body = header + [
            "package com.coffeejournal.domain.reference",
            "",
            f"/** {p['name']}: {len(rows)} 시·군·구, simplified with a tolerance of {district_tol[p['code']]:.0f} map units. */",
            f"internal object KoreaMapProvince{p['code']} {{",
            "    val districts: List<KoreaMapData.District> = listOf(",
        ]
        for d in rows:
            body.append(f"        KoreaMapData.District({kstr(d['code'])}, {kstr(d['name'])}, {d['lx']}, {d['ly']}, D{d['code']}),")
        body += ["    )", ""]
        for d in rows:
            body.append(f"    private const val D{d['code']}: String = {kstr(d['outline'])}")
        body.append("}")
        body.append("")
        files[f"KoreaMapProvince{p['code']}.kt"] = "\n".join(body)

    os.makedirs(args.out, exist_ok=True)
    for old in os.listdir(args.out):
        if re.fullmatch(r"KoreaMapProvince\d+\.kt", old) and old not in files:
            os.remove(os.path.join(args.out, old))
    total = 0
    for name, text in files.items():
        with open(os.path.join(args.out, name), "w", encoding="utf-8") as f:
            f.write(text)
        total += len(text.encode("utf-8"))
    print(f"source {where}  sha256 {sha256}")
    print(f"행정동 {len(feats)} → 시·군·구 {len(codes)} (overlaps fixed {fixed}), 시·도 {len(provinces)}, cities {len(city_rows)}")
    print(f"parts {raw_parts} → {kept_parts} after dropping specks < {args.min_km2} km²")
    print(f"points {total_points}; Kotlin {len(files)} files, {total / 1024:.1f} KiB")
    for s in sido_codes:
        print(f"  {s} {sido_names[s]}: district tolerance {district_tol[s]:.1f}, {sum(d['points'] for d in districts[s])} points")

    if args.preview:
        write_previews(args.preview, provinces, districts, view)


def write_previews(out: str, provinces, districts, view) -> None:
    from PIL import Image, ImageDraw

    os.makedirs(out, exist_ok=True)

    def draw(encoded_list, fname, size=900):
        rings = [r for e in encoded_list for r in decode(e)]
        xs = [x for r in rings for x, _ in r]
        ys = [y for r in rings for _, y in r]
        x0, y0, x1, y1 = min(xs), min(ys), max(xs), max(ys)
        s = size / max(x1 - x0, y1 - y0)
        im = Image.new("RGB", (int((x1 - x0) * s) + 20, int((y1 - y0) * s) + 20), "white")
        dr = ImageDraw.Draw(im)
        for e in encoded_list:
            for r in decode(e):
                dr.polygon([((x - x0) * s + 10, (y - y0) * s + 10) for x, y in r], fill=(236, 235, 229), outline=(60, 60, 60))
        im.save(os.path.join(out, fname))

    draw([p["outline"] for p in provinces], "national.png")
    for p in provinces:
        draw([d["outline"] for d in districts[p["code"]]], f"province_{p['code']}.png")


if __name__ == "__main__":
    main()
