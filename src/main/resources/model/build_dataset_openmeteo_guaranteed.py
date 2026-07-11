# build_dataset_guaranteed_min.py
# ✅ Genera SIEMPRE filas (Open-Meteo si hay, imputado si no).
# ✅ Incluye barra de progreso (tqdm) para ver el avance.
# ✅ Garantiza datasets grandes (10k+) sin quedarse en 0 filas.

import csv, math, argparse
from datetime import datetime, timedelta
from math import radians, sin, cos, sqrt, atan2

import numpy as np
import requests

try:
    from tqdm import tqdm
    _HAS_TQDM = True
except Exception:
    _HAS_TQDM = False


# ---------- Puertos probados ----------
PORTS = [
    # América
    ("Callao", -12.0564, -77.1319), ("Buenos Aires", -34.6037, -58.3816),
    ("Valparaíso", -33.0472, -71.6128), ("Rio de Janeiro", -22.9068, -43.1729),
    ("Guayaquil", -2.1962, -79.8862), ("Balboa", 8.9833, -79.5167),
    ("Manzanillo", 19.0514, -104.3158), ("Long Beach", 33.7709, -118.1937),
    ("New York", 40.700569, -74.004175), ("Houston", 29.7604, -95.3698),
    ("Vancouver", 49.2827, -123.1207), ("Prince Rupert", 54.3150, -130.3208),
    ("Cartagena", 10.3932, -75.4832), ("San Antonio", -33.5983, -71.6123),

    # Europa
    ("Rotterdam", 51.9244, 4.4777), ("Valencia", 39.4699, -0.3763),
    ("Hamburgo", 53.5488, 9.9872), ("Le Havre", 49.4944, 0.1089),
    ("Genova", 44.4056, 8.9463), ("Lisboa", 38.7223, -9.1393),

    # Asia
    ("Singapore", 1.3521, 103.8198), ("Shanghai", 31.2304, 121.4737),
    ("Tokyo", 35.6895, 139.6917), ("Busan", 35.1796, 129.0756),
    ("Hong Kong", 22.3193, 114.1694), ("Dubai", 25.2769, 55.2962),

    # África
    ("Durban", -29.8833, 31.0500), ("Lagos", 6.4541, 3.3947),
    ("Casablanca", 33.5731, -7.5898), ("Tanger Med", 35.8844, -5.5036),

    # Oceanía
    ("Sídney", -33.8688, 151.2093), ("Melbourne", -37.8136, 144.9631),
]


# ---------- Utils ----------
def haversine(lat1, lon1, lat2, lon2):
    R = 6371.0
    dlat = radians(lat2 - lat1)
    dlon = radians(lon2 - lon1)
    a = sin(dlat/2)**2 + cos(radians(lat1))*cos(radians(lat2))*sin(dlon/2)**2
    return 2*R*atan2(sqrt(a), sqrt(1-a))

def season_from_month(m):
    return ["DJF","DJF","MAM","MAM","MAM","JJA","JJA","JJA","SON","SON","SON","DJF"][m-1]

def rand_date(rng, start, end):
    span = (end - start).days
    return start + timedelta(days=int(rng.random() * max(1, span)))

def req_json(url, timeout=10):
    try:
        r = requests.get(url, timeout=timeout)
        if r.status_code == 200:
            return r.json()
    except Exception:
        pass
    return None


# ---------- Fetchers ----------
def fetch_weather(lat, lon, date):
    d = date.strftime("%Y-%m-%d")
    url = ("https://archive-api.open-meteo.com/v1/archive"
           f"?latitude={lat:.2f}&longitude={lon:.2f}"
           f"&start_date={d}&end_date={d}"
           "&daily=wind_speed_10m_max,precipitation_sum,temperature_2m_max"
           "&timezone=UTC")
    j = req_json(url)
    if not j or "daily" not in j:
        return None
    try:
        dd = j["daily"]
        return {
            "max_wind_knots": float(dd["wind_speed_10m_max"][0]) * 0.54,
            "rain_mm": float(dd["precipitation_sum"][0]),
            "temp_c": float(dd["temperature_2m_max"][0]),
        }
    except Exception:
        return None

def fetch_marine(lat, lon, date, win=8):
    start = (date - timedelta(days=win)).strftime("%Y-%m-%d")
    end   = (date + timedelta(days=win)).strftime("%Y-%m-%d")
    url = ("https://marine-api.open-meteo.com/v1/marine"
           f"?latitude={lat:.2f}&longitude={lon:.2f}"
           f"&start_date={start}&end_date={end}"
           "&daily=wave_height_max&timezone=UTC")
    j = req_json(url)
    if not j or "daily" not in j:
        return None
    vals = j["daily"].get("wave_height_max")
    if not vals:
        return None
    for v in vals:
        if v is not None:
            return {"max_wave_m": float(v)}
    return None


# ---------- Fila garantizada ----------
def build_row(rng, port_o, port_d, dep):
    name_o, lat_o, lon_o = port_o
    name_d, lat_d, lon_d = port_d
    latm = lat_o + (lat_d - lat_o) * 0.6
    lonm = lon_o + (lon_d - lon_o) * 0.6 + 0.4 * (1 if lon_o >= 0 else -1)

    dist_km = haversine(lat_o, lon_o, lat_d, lon_d)
    month = dep.month
    season = season_from_month(month)

    w = fetch_weather(latm, lonm, dep)
    m = fetch_marine(latm, lonm, dep, win=8)

    if not w:
        w = {
            "max_wind_knots": float(rng.uniform(6, 28)),
            "rain_mm": float(rng.uniform(0, 12)),
            "temp_c": float(rng.uniform(5, 30)),
        }
    if not m:
        m = {"max_wave_m": float(rng.uniform(0.5, 2.8))}

    origin_queue = int(rng.integers(0, 8))
    dest_queue   = int(rng.integers(0, 8))
    port_congestion_idx = float(rng.uniform(0.2, 1.2))
    visibility_km = max(2.0, 18.0 - 0.4 * w["rain_mm"] + float(rng.normal(0, 1.2)))

    delay = (
            0.22 * w["max_wind_knots"] +
            0.12 * w["rain_mm"] +
            1.05 * m["max_wave_m"] +
            0.55 * port_congestion_idx +
            0.30 * origin_queue + 0.30 * dest_queue +
            float(rng.normal(1.2, 1.0))
    )
    delay = max(0.0, float(delay))

    return [
        name_o, name_d,
        lat_o, lon_o, lat_d, lon_d,
        dist_km,
        dep.strftime("%Y-%m-%d"),
        w["max_wind_knots"], w["rain_mm"], w["temp_c"],
        m["max_wave_m"],
        visibility_km,
        month, season,
        origin_queue, dest_queue,
        port_congestion_idx,
        delay
    ]


# ---------- Main ----------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--samples", type=int, default=10000)
    ap.add_argument("--output", type=str, default="maritime_delays.csv")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--start", type=str, default="2019-01-01")
    ap.add_argument("--end", type=str, default="2024-12-31")
    args = ap.parse_args()

    rng = np.random.default_rng(args.seed)
    start_date = datetime.fromisoformat(args.start)
    end_date   = datetime.fromisoformat(args.end)

    header = [
        "port_origin","port_dest","lat_o","lon_o","lat_d","lon_d","gc_distance_km",
        "departure_date","avg_wind_knots","rain_mm","temp_c","max_wave_m",
        "visibility_km","month","season","origin_queue","dest_queue",
        "port_congestion_idx","delay_hours"
    ]

    with open(args.output, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        iterator = tqdm(range(args.samples), desc="Generando filas", unit="fila") if _HAS_TQDM else range(args.samples)
        for _ in iterator:
            i, j = rng.choice(len(PORTS), 2, replace=False)
            dep = rand_date(rng, start_date, end_date)
            row = build_row(rng, PORTS[i], PORTS[j], dep)
            writer.writerow(row)

    print(f"✅ Dataset generado: {args.samples} filas → {args.output}")


if __name__ == "__main__":
    main()
