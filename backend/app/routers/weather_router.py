# -*- coding: utf-8 -*-
"""小德助手 API - 天气代理路由
- GET /api/weather?city=北京   按城市查询
- GET /api/weather/loc?lat=..&lon=..  按经纬度查询（App 授权定位后传坐标）
- GET /api/weather/ip          按访问来源 IP 自动定位（无需权限）
数据源：wttr.in （免 key），做一层代理与字段归一化，方便 App 端统一解析。
"""
from typing import Optional
from fastapi import APIRouter, Query, Request
from fastapi.responses import JSONResponse
import urllib.request
import urllib.parse
import json

router = APIRouter(prefix="/api/weather", tags=["天气"])

UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 XiaodeApp/1.0"
TIMEOUT = 8


def _fetch_wttr(query: str):
    """请求 wttr.in 的 j1 (JSON) 格式，返回 dict 或 None"""
    url = "https://wttr.in/%s?format=j1&lang=zh" % urllib.parse.quote(query)
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            raw = r.read().decode("utf-8", "ignore")
        return json.loads(raw)
    except Exception:
        return None


def _norm(data: dict, fallback_city: str = ""):
    """把 wttr.in 的返回归一化成 App 易用的结构"""
    if not data:
        return None
    cur = (data.get("current_condition") or [{}])[0]
    area = (data.get("nearest_area") or [{}])[0]
    try:
        city = area.get("areaName", [{}])[0].get("value", "") or fallback_city
        region = area.get("region", [{}])[0].get("value", "")
        country = area.get("country", [{}])[0].get("value", "")
    except Exception:
        city, region, country = fallback_city, "", ""

    desc = ""
    try:
        desc = cur.get("lang_zh", [{}])[0].get("value", "") or \
               cur.get("weatherDesc", [{}])[0].get("value", "")
    except Exception:
        desc = ""

    def _num(v, d=0):
        try:
            return int(v)
        except Exception:
            return d

    today = (data.get("weather") or [{}])[0]
    forecast = []
    for i, d in enumerate(data.get("weather", []) or []):
        if i >= 3:
            break
        try:
            forecast.append({
                "date": d.get("date", ""),
                "max_temp": _num(d.get("maxtempC")),
                "min_temp": _num(d.get("mintempC")),
                "desc": d.get("hourly", [{}])[4].get("lang_zh", [{}])[0].get("value", "") or "",
            })
        except Exception:
            forecast.append({"date": d.get("date", ""), "max_temp": _num(d.get("maxtempC")),
                             "min_temp": _num(d.get("mintempC")), "desc": ""})

    # 今日日出日落
    astro = (today.get("astronomy") or [{}])[0]
    return {
        "ok": True,
        "city": city,
        "region": region,
        "country": country,
        "temp": _num(cur.get("temp_C")),
        "feels_like": _num(cur.get("FeelsLikeC")),
        "desc": desc,
        "humidity": _num(cur.get("humidity")),
        "wind": _num(cur.get("windspeedKmph")),
        "wind_dir": cur.get("winddir16Point", ""),
        "visibility": _num(cur.get("visibility")),
        "pressure": _num(cur.get("pressure")),
        "uv_index": _num(cur.get("uvIndex")),
        "sunrise": astro.get("sunrise", ""),
        "sunset": astro.get("sunset", ""),
        "forecast": forecast,
        "source": "wttr.in",
    }


@router.get("", summary="按城市查询天气")
def weather_by_city(city: str = Query(..., min_length=1, max_length=40)):
    data = _fetch_wttr(city)
    out = _norm(data, city)
    if not out:
        return JSONResponse(status_code=502, content={"ok": False, "error": "天气服务暂时不可用"})
    return out


@router.get("/loc", summary="按经纬度查询天气（App 授权定位后调用）")
def weather_by_loc(lat: float = Query(...), lon: float = Query(...)):
    data = _fetch_wttr("%s,%s" % (lat, lon))
    out = _norm(data)
    if not out:
        return JSONResponse(status_code=502, content={"ok": False, "error": "天气服务暂时不可用"})
    return out


@router.get("/ip", summary="按来源 IP 自动定位（无需权限）")
def weather_by_ip(request: Request):
    # 尝试从反代头拿真实 IP
    ip = (request.headers.get("x-forwarded-for", "").split(",")[0].strip()
          or request.headers.get("x-real-ip", "")
          or (request.client.host if request.client else ""))
    # 内网/本地 IP 用 wttr.in 的自动定位
    query = "auto" if (not ip or ip.startswith(("127.", "10.", "192.168.", "172."))) else ip
    data = _fetch_wttr(query)
    out = _norm(data)
    if not out:
        return JSONResponse(status_code=502, content={"ok": False, "error": "天气服务暂时不可用"})
    out["ip"] = ip
    return out
