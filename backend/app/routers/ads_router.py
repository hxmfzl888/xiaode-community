# -*- coding: utf-8 -*-
"""小德社区 v9.1 - 广告位 & 客服问答"""
import os
import uuid
from datetime import datetime
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import Ad, Faq
from ..deps import get_current_admin

router = APIRouter(prefix="/api", tags=["广告和客服"])

BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
AD_DIR = os.path.join(BASE_DIR, "uploads", "ads")
os.makedirs(AD_DIR, exist_ok=True)
ALLOWED_IMG_EXT = {"jpg", "jpeg", "png", "gif", "webp", "bmp"}
MAX_AD_BYTES = 5 * 1024 * 1024


def _save_ad_image(file):
    ext = (file.filename or "").rsplit(".", 1)[-1].lower() if "." in (file.filename or "") else ""
    if ext not in ALLOWED_IMG_EXT:
        raise HTTPException(status_code=400, detail="只支持 jpg/png/gif/webp/bmp 图片")
    data = file.file.read()
    if len(data) > MAX_AD_BYTES:
        raise HTTPException(status_code=400, detail="图片过大，最大 5MB")
    name = "%s_%s.%s" % (datetime.now().strftime("%Y%m%d%H%M%S"), uuid.uuid4().hex[:8], ext)
    with open(os.path.join(AD_DIR, name), "wb") as f:
        f.write(data)
    return "/uploads/ads/%s" % name


def _ad_out(a):
    return {"id": a.id, "title": a.title or "", "image": a.image or "",
            "link": a.link or "", "sort": a.sort or 0, "active": bool(a.active),
            "start_at": a.start_at.isoformat() if a.start_at else "",
            "end_at": a.end_at.isoformat() if a.end_at else ""}


def _faq_out(f):
    return {"id": f.id, "question": f.question or "", "answer": f.answer or "",
            "category": f.category or "", "sort": f.sort or 0,
            "pinned": bool(f.pinned), "active": bool(f.active)}


# ---------- 广告：App 拉取（公开）----------
@router.get("/ads", summary="广告位列表（App用，仅生效中）")
def list_ads(db: Session = Depends(get_db)):
    now = datetime.utcnow()
    rows = db.query(Ad).filter(Ad.active == True).order_by(Ad.sort.asc(), Ad.id.desc()).all()
    out = []
    for a in rows:
        if a.start_at and a.start_at > now:
            continue
        if a.end_at and a.end_at < now:
            continue
        out.append(_ad_out(a))
    return out


# ---------- 广告：后台管理 ----------
@router.get("/admin/ads", summary="广告列表（管理员）")
def admin_list_ads(db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    rows = db.query(Ad).order_by(Ad.sort.asc(), Ad.id.desc()).all()
    return [_ad_out(a) for a in rows]


@router.post("/admin/ads", summary="新建广告（管理员）")
def admin_create_ad(data: dict, db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    a = Ad(title=(data.get("title") or "")[:120], image=(data.get("image") or "")[:500],
           link=(data.get("link") or "")[:500], sort=int(data.get("sort") or 0),
           active=bool(data.get("active", True)))
    db.add(a); db.commit(); db.refresh(a)
    return _ad_out(a)


@router.put("/admin/ads/{ad_id}", summary="修改广告（管理员）")
def admin_update_ad(ad_id: int, data: dict, db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    a = db.query(Ad).filter(Ad.id == ad_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="广告不存在")
    if "title" in data: a.title = (data.get("title") or "")[:120]
    if "image" in data: a.image = (data.get("image") or "")[:500]
    if "link" in data: a.link = (data.get("link") or "")[:500]
    if "sort" in data: a.sort = int(data.get("sort") or 0)
    if "active" in data: a.active = bool(data.get("active"))
    db.commit(); db.refresh(a)
    return _ad_out(a)


@router.delete("/admin/ads/{ad_id}", summary="删除广告（管理员）")
def admin_delete_ad(ad_id: int, db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    a = db.query(Ad).filter(Ad.id == ad_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="广告不存在")
    db.delete(a); db.commit()
    return {"ok": True}


@router.post("/admin/ads/upload", summary="上传广告图（管理员）")
def admin_upload_ad(file: UploadFile = File(...), admin=Depends(get_current_admin)):
    return {"url": _save_ad_image(file)}


# ---------- 客服问答 ----------
@router.get("/faqs", summary="客服问答列表（App用）")
def list_faqs(category: Optional[str] = Query(None), db: Session = Depends(get_db)):
    q = db.query(Faq).filter(Faq.active == True)
    if category:
        q = q.filter(Faq.category == category)
    rows = q.order_by(Faq.pinned.desc(), Faq.sort.asc(), Faq.id.asc()).all()
    return [_faq_out(f) for f in rows]


@router.get("/admin/faqs", summary="客服问答列表（管理员）")
def admin_list_faqs(db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    rows = db.query(Faq).order_by(Faq.pinned.desc(), Faq.sort.asc(), Faq.id.asc()).all()
    return [_faq_out(f) for f in rows]


@router.post("/admin/faqs", summary="新建问答（管理员）")
def admin_create_faq(data: dict, db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    f = Faq(question=(data.get("question") or "")[:255], answer=data.get("answer") or "",
            category=(data.get("category") or "")[:50], sort=int(data.get("sort") or 0),
            pinned=bool(data.get("pinned", False)), active=bool(data.get("active", True)))
    db.add(f); db.commit(); db.refresh(f)
    return _faq_out(f)


@router.put("/admin/faqs/{faq_id}", summary="修改问答（管理员）")
def admin_update_faq(faq_id: int, data: dict, db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    f = db.query(Faq).filter(Faq.id == faq_id).first()
    if not f:
        raise HTTPException(status_code=404, detail="问答不存在")
    if "question" in data: f.question = (data.get("question") or "")[:255]
    if "answer" in data: f.answer = data.get("answer") or ""
    if "category" in data: f.category = (data.get("category") or "")[:50]
    if "sort" in data: f.sort = int(data.get("sort") or 0)
    if "pinned" in data: f.pinned = bool(data.get("pinned"))
    if "active" in data: f.active = bool(data.get("active"))
    db.commit(); db.refresh(f)
    return _faq_out(f)


@router.delete("/admin/faqs/{faq_id}", summary="删除问答（管理员）")
def admin_delete_faq(faq_id: int, db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    f = db.query(Faq).filter(Faq.id == faq_id).first()
    if not f:
        raise HTTPException(status_code=404, detail="问答不存在")
    db.delete(f); db.commit()
    return {"ok": True}
