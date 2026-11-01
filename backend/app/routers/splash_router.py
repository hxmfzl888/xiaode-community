# -*- coding: utf-8 -*-
"""开屏广告：App 拉取 + 后台管理"""
import os
from datetime import datetime
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import SplashAd, User
from ..deps import get_current_admin

router = APIRouter(prefix="/api/splash", tags=["开屏广告"])

UPLOAD_DIR = "/opt/xiaode-api/uploads/ads"


def _abs_url(path: str) -> str:
    p = (path or "").strip()
    if not p:
        return ""
    if p.startswith("http"):
        return p
    return p if p.startswith("/") else "/" + p


def _ad_dict(a: SplashAd) -> dict:
    return {
        "id": a.id,
        "image": _abs_url(a.image),
        "link": a.link or "",
        "title": a.title or "",
        "skip_seconds": int(a.skip_seconds or 3),
        "enabled": bool(a.enabled),
        "sort": int(a.sort or 0),
        "start_at": a.start_at.isoformat() if a.start_at else "",
        "end_at": a.end_at.isoformat() if a.end_at else "",
        "view_count": int(a.view_count or 0),
        "click_count": int(a.click_count or 0),
        "created_at": a.created_at.isoformat() if a.created_at else "",
    }


@router.get("", summary="获取当前开屏广告（App 启动调用）", response_model=None)
def get_splash(db: Session = Depends(get_db)):
    now = datetime.utcnow()
    rows = db.query(SplashAd).filter(SplashAd.enabled == True).all()  # noqa: E712
    valid = []
    for a in rows:
        if a.start_at and a.start_at > now:
            continue
        if a.end_at and a.end_at < now:
            continue
        if not (a.image or "").strip():
            continue
        valid.append(a)
    if not valid:
        return {"ok": True, "ad": None}
    valid.sort(key=lambda x: (-(x.sort or 0), -(x.id or 0)))
    ad = valid[0]
    try:
        ad.view_count = int(ad.view_count or 0) + 1
        db.commit()
    except Exception:
        db.rollback()
    return {"ok": True, "ad": _ad_dict(ad)}


@router.post("/{ad_id}/click", summary="记录广告点击", response_model=None)
def click_splash(ad_id: int, db: Session = Depends(get_db)):
    a = db.query(SplashAd).filter(SplashAd.id == ad_id).first()
    if a:
        try:
            a.click_count = int(a.click_count or 0) + 1
            db.commit()
        except Exception:
            db.rollback()
    return {"ok": True}


# ==================== 后台管理 ====================

@router.get("/admin/list", summary="开屏广告列表（管理员）", response_model=None)
def admin_list(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    rows = db.query(SplashAd).order_by(SplashAd.sort.desc(), SplashAd.id.desc()).all()
    return [_ad_dict(a) for a in rows]


@router.post("/admin/upload", summary="上传开屏广告图（管理员）", response_model=None)
async def admin_upload(file: UploadFile = File(...), db: Session = Depends(get_db),
                       admin: User = Depends(get_current_admin)):
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    ext = os.path.splitext(file.filename or "img.png")[1].lower()
    if ext not in (".png", ".jpg", ".jpeg", ".webp", ".gif"):
        raise HTTPException(status_code=400, detail="仅支持 png/jpg/webp/gif")
    name = datetime.now().strftime("%Y%m%d%H%M%S_") + str(os.urandom(4).hex()) + ext
    path = os.path.join(UPLOAD_DIR, name)
    data = await file.read()
    if len(data) > 8 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="图片不能超过 8MB")
    with open(path, "wb") as f:
        f.write(data)
    try:
        os.chmod(path, 0o644)
    except Exception:
        pass
    return {"ok": True, "url": "/uploads/ads/" + name}


@router.post("/admin/create", summary="新建开屏广告（管理员）", response_model=None)
def admin_create(data: dict, db: Session = Depends(get_db),
                 admin: User = Depends(get_current_admin)):
    img = (data.get("image") or "").strip()
    if not img:
        raise HTTPException(status_code=400, detail="请先上传广告图")
    a = SplashAd(
        image=img,
        link=(data.get("link") or "").strip(),
        title=(data.get("title") or "").strip(),
        skip_seconds=int(data.get("skip_seconds") or 3),
        enabled=bool(data.get("enabled", True)),
        sort=int(data.get("sort") or 0),
    )
    if a.skip_seconds < 0:
        a.skip_seconds = 0
    if a.skip_seconds > 15:
        a.skip_seconds = 15
    db.add(a)
    db.commit()
    db.refresh(a)
    return {"ok": True, "id": a.id}


@router.post("/admin/{ad_id}/update", summary="修改开屏广告（管理员）", response_model=None)
def admin_update(ad_id: int, data: dict, db: Session = Depends(get_db),
                 admin: User = Depends(get_current_admin)):
    a = db.query(SplashAd).filter(SplashAd.id == ad_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="广告不存在")
    if "image" in data and (data.get("image") or "").strip():
        a.image = data["image"].strip()
    if "link" in data:
        a.link = (data.get("link") or "").strip()
    if "title" in data:
        a.title = (data.get("title") or "").strip()
    if "skip_seconds" in data:
        v = int(data.get("skip_seconds") or 3)
        a.skip_seconds = max(0, min(15, v))
    if "sort" in data:
        a.sort = int(data.get("sort") or 0)
    if "enabled" in data:
        a.enabled = bool(data.get("enabled"))
    db.commit()
    return {"ok": True, "id": a.id}


@router.post("/admin/{ad_id}/toggle", summary="启用/停用开屏广告（管理员）", response_model=None)
def admin_toggle(ad_id: int, db: Session = Depends(get_db),
                 admin: User = Depends(get_current_admin)):
    a = db.query(SplashAd).filter(SplashAd.id == ad_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="广告不存在")
    a.enabled = not bool(a.enabled)
    db.commit()
    return {"ok": True, "id": a.id, "enabled": bool(a.enabled)}


@router.delete("/admin/{ad_id}", summary="删除开屏广告（管理员）", response_model=None)
def admin_delete(ad_id: int, db: Session = Depends(get_db),
                 admin: User = Depends(get_current_admin)):
    a = db.query(SplashAd).filter(SplashAd.id == ad_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="广告不存在")
    img = a.image or ""
    db.delete(a)
    db.commit()
    # 顺手删图片文件
    try:
        if img.startswith("/uploads/ads/"):
            fp = "/opt/xiaode-api" + img
            if os.path.isfile(fp):
                os.remove(fp)
    except Exception:
        pass
    return {"ok": True, "deleted": ad_id}