# -*- coding: utf-8 -*-
"""小德社区 v7.1 - 频道：列表/详情/创建/加入/审核"""
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import User, Channel, ChannelMember
from ..deps import get_current_user, get_current_admin, get_current_user_optional

router = APIRouter(prefix="/api/channels", tags=["频道"])


def _c_out(c: Channel, db: Session, uid: Optional[int]):
    owner = db.query(User).filter(User.id == c.owner_id).first()
    joined = False
    if uid:
        joined = (db.query(ChannelMember)
                  .filter(ChannelMember.channel_id == c.id, ChannelMember.user_id == uid).first()) is not None
    return {
        "id": c.id,
        "name": c.name,
        "intro": c.intro or "",
        "image": c.image or "",
        "owner_id": c.owner_id,
        "owner_name": (owner.nickname or owner.username) if owner else "",
        "owner_username": owner.username if owner else "",
        "status": c.status or "pending",
        "member_count": c.member_count or 0,
        "joined": joined,
        "created_at": c.created_at.isoformat() if c.created_at else "",
    }


@router.get("", summary="频道列表（仅审核通过的）")
def list_channels(q: str = Query("", max_length=60),
                  db: Session = Depends(get_db),
                  current: Optional[User] = Depends(get_current_user_optional)):
    # 用 optional 鉴权：这里直接要求登录，简化
    query = db.query(Channel).filter(Channel.status == "approved")
    if q:
        query = query.filter(Channel.name.like("%" + q + "%"))
    rows = query.order_by(Channel.created_at.desc()).limit(100).all()
    return [_c_out(c, db, current.id if current else None) for c in rows]


@router.get("/mine", summary="我创建的频道（含待审核）")
def my_channels(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = (db.query(Channel).filter(Channel.owner_id == current.id)
            .order_by(Channel.created_at.desc()).all())
    return [_c_out(c, db, current.id if current else None) for c in rows]


@router.get("/joined", summary="我加入的频道")
def joined_channels(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    mems = db.query(ChannelMember).filter(ChannelMember.user_id == current.id).all()
    ids = [m.channel_id for m in mems]
    if not ids:
        return []
    rows = db.query(Channel).filter(Channel.id.in_(ids), Channel.status == "approved").all()
    return [_c_out(c, db, current.id if current else None) for c in rows]


@router.get("/{cid}", summary="频道详情")
def channel_detail(cid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    c = db.query(Channel).filter(Channel.id == cid).first()
    if not c:
        raise HTTPException(status_code=404, detail="频道不存在")
    if c.status != "approved" and c.owner_id != current.id and not current.is_admin:
        raise HTTPException(status_code=403, detail="频道未通过审核")
    return _c_out(c, db, current.id)


@router.post("", summary="创建频道（需审核）")
def create_channel(data: dict, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    name = (data.get("name") or "").strip()
    if len(name) < 2:
        raise HTTPException(status_code=400, detail="频道名称至少 2 个字符")
    c = Channel(name=name, intro=(data.get("intro") or "").strip(),
                image=(data.get("image") or "").strip(),
                owner_id=current.id, status="pending", member_count=1)
    db.add(c); db.commit(); db.refresh(c)
    db.add(ChannelMember(channel_id=c.id, user_id=current.id)); db.commit()
    return _c_out(c, db, current.id)


@router.post("/{cid}/join", summary="加入/退出频道（切换）")
def toggle_join(cid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    c = db.query(Channel).filter(Channel.id == cid).first()
    if not c:
        raise HTTPException(status_code=404, detail="频道不存在")
    ex = (db.query(ChannelMember)
          .filter(ChannelMember.channel_id == cid, ChannelMember.user_id == current.id).first())
    if ex:
        db.delete(ex)
        c.member_count = max(0, (c.member_count or 1) - 1)
        db.commit()
        return {"ok": True, "joined": False}
    else:
        db.add(ChannelMember(channel_id=cid, user_id=current.id))
        c.member_count = (c.member_count or 0) + 1
        db.commit()
        return {"ok": True, "joined": True}


# ---------- 管理员审核 ----------
@router.get("/admin/pending", summary="待审核频道（管理员）")
def pending_channels(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    rows = (db.query(Channel).filter(Channel.status == "pending")
            .order_by(Channel.created_at.asc()).all())
    return [_c_out(c, db, admin.id) for c in rows]


@router.post("/admin/{cid}/review", summary="审核频道：通过/驳回（管理员）")
def review_channel(cid: int, data: dict, db: Session = Depends(get_db),
                   admin: User = Depends(get_current_admin)):
    c = db.query(Channel).filter(Channel.id == cid).first()
    if not c:
        raise HTTPException(status_code=404, detail="频道不存在")
    action = data.get("action", "approve")
    c.status = "approved" if action == "approve" else "rejected"
    db.commit()
    return _c_out(c, db, admin.id)

# ---------- v9.0 新增：频道换头像 ----------
import os
import uuid
from datetime import datetime
from fastapi import UploadFile, File

_BASE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_CH_DIR = os.path.join(_BASE, "uploads", "channels")
os.makedirs(_CH_DIR, exist_ok=True)
_CH_EXT = {"jpg", "jpeg", "png", "gif", "webp", "bmp"}


@router.post("/{cid}/avatar", summary="更换频道头像（频道主/管理员）")
def upload_channel_avatar(cid: int, file: UploadFile = File(...),
                          db: Session = Depends(get_db),
                          current: User = Depends(get_current_user)):
    c = db.query(Channel).filter(Channel.id == cid).first()
    if not c:
        raise HTTPException(status_code=404, detail="频道不存在")
    if c.owner_id != current.id and not getattr(current, "is_admin", False):
        raise HTTPException(status_code=403, detail="只有频道主可以更换头像")
    ext = (file.filename or "").rsplit(".", 1)[-1].lower() if "." in (file.filename or "") else ""
    if ext not in _CH_EXT:
        raise HTTPException(status_code=400, detail="只支持 jpg/png/gif/webp/bmp 图片")
    data = file.file.read()
    if len(data) > 10 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="图片过大，最大 10MB")
    name = "%s_%s.%s" % (datetime.now().strftime("%Y%m%d%H%M%S"), uuid.uuid4().hex[:8], ext)
    with open(os.path.join(_CH_DIR, name), "wb") as f:
        f.write(data)
    c.image = "/uploads/channels/%s" % name
    db.commit()
    db.refresh(c)
    return _c_out(c, db, current.id)


# ---------- v9.0 新增：频道内搜索帖子 ----------
@router.get("/{cid}/search", summary="在指定频道内搜索帖子")
def search_channel_moments(cid: int, q: str = Query("", max_length=60),
                           limit: int = Query(30, ge=1, le=100),
                           offset: int = Query(0, ge=0),
                           db: Session = Depends(get_db),
                           current: Optional[User] = Depends(get_current_user_optional)):
    from ..models import Moment, MomentLike
    from sqlalchemy import or_, desc
    query = db.query(Moment).filter(Moment.channel_id == cid, Moment.status == "approved")
    if q.strip():
        query = query.filter(Moment.content.like("%%%s%%" % q.strip()))
    rows = query.order_by(desc(Moment.created_at)).offset(offset).limit(limit).all()
    uid = current.id if current else None
    out = []
    for m in rows:
        like_cnt = db.query(MomentLike).filter(MomentLike.moment_id == m.id).count()
        liked = bool(uid) and (db.query(MomentLike).filter(
            MomentLike.moment_id == m.id, MomentLike.user_id == uid).first() is not None)
        u = db.query(User).filter(User.id == m.user_id).first()
        out.append({
            "id": m.id, "content": m.content or "",
            "images": _load_json_list(getattr(m, "images", "")),
            "videos": _load_json_list(getattr(m, "videos", "")),
            "status": m.status or "pending",
            "created_at": m.created_at.isoformat() if m.created_at else "",
            "user_id": m.user_id,
            "nickname": (u.nickname or u.username) if u else "",
            "username": u.username if u else "",
            "avatar": u.avatar if u else "",
            "comment_count": 0, "like_count": like_cnt, "liked": liked,
            "level": getattr(u, "level", 1) if u else 1,
            "level_effect": getattr(u, "level_effect", "normal") if u else "normal",
            "channel_id": m.channel_id or 0, "channel_name": "",
            "is_vip": bool(u and getattr(u, "vip_expire", None) and u.vip_expire > datetime.utcnow()),
        })
    return out


def _load_json_list(s):
    import json
    if not s:
        return []
    try:
        v = json.loads(s)
        return v if isinstance(v, list) else []
    except Exception:
        return []
