# -*- coding: utf-8 -*-
"""小德社区 v9.0 - 全局搜索：帖子 / 频道"""
import json
from typing import List, Optional
from datetime import datetime
from fastapi import APIRouter, Depends, Query
from sqlalchemy import or_, desc
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User, Moment, MomentLike, Channel, ChannelMember
from ..deps import get_current_user_optional

router = APIRouter(prefix="/api/search", tags=["搜索"])


def _jlist(s):
    if not s:
        return []
    try:
        v = json.loads(s)
        return v if isinstance(v, list) else []
    except Exception:
        return []


def _vip(u):
    return bool(u and getattr(u, "vip_expire", None) and u.vip_expire > datetime.utcnow())


@router.get("/moments", summary="搜索帖子")
def search_moments(q: str = Query("", max_length=60),
                   limit: int = Query(30, ge=1, le=100),
                   offset: int = Query(0, ge=0),
                   db: Session = Depends(get_db),
                   current=Depends(get_current_user_optional)):
    kw = q.strip()
    query = db.query(Moment).filter(Moment.status == "approved")
    if kw:
        query = query.filter(Moment.content.like("%%%s%%" % kw))
    rows = query.order_by(desc(Moment.created_at)).offset(offset).limit(limit).all()
    uid = current.id if current else None
    out = []
    for m in rows:
        u = db.query(User).filter(User.id == m.user_id).first()
        like_cnt = db.query(MomentLike).filter(MomentLike.moment_id == m.id).count()
        liked = bool(uid) and (db.query(MomentLike).filter(
            MomentLike.moment_id == m.id, MomentLike.user_id == uid).first() is not None)
        ch = None
        if getattr(m, "channel_id", 0):
            ch = db.query(Channel).filter(Channel.id == m.channel_id).first()
        out.append({
            "id": m.id, "title": (getattr(m, "title", "") or ""), "content": m.content or "",
            "images": _jlist(getattr(m, "images", "")),
            "videos": _jlist(getattr(m, "videos", "")),
            "status": m.status or "approved",
            "created_at": m.created_at.isoformat() if m.created_at else "",
            "user_id": m.user_id,
            "nickname": ((u.nickname or u.username or "") if u else ""),
            "username": ((u.username or "") if u else ""),
            "avatar": ((u.avatar or "") if u else ""),
            "comment_count": 0, "like_count": like_cnt, "liked": liked,
            "level": getattr(u, "level", 1) if u else 1,
            "level_effect": getattr(u, "level_effect", "normal") if u else "normal",
            "channel_id": getattr(m, "channel_id", 0) or 0,
            "channel_name": ((ch.name or "") if ch else ""),
            "is_vip": _vip(u),
        })
    return out


# ============================================================
# UID_V1: 用户搜索（支持 UID / 昵称 / 用户名）
# 优先精确匹配 UID，其次模糊匹配昵称和用户名
# ============================================================

@router.get("/users", summary="搜索用户（UID/昵称/用户名）")
def search_users(
    q: str = Query("", description="关键词：UID / 昵称 / 用户名"),
    limit: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
    current: Optional[User] = Depends(get_current_user_optional),
):
    kw = (q or "").strip()
    if not kw:
        return []

    results = []
    seen = set()

    def _push(u, match_type):
        if not u or u.id in seen:
            return
        # 不返回被封禁的账号
        if getattr(u, "is_banned", False):
            return
        seen.add(u.id)
        results.append({
            "id": u.id,
            "uid": (getattr(u, "uid", "") or ""),
            "nickname": u.nickname or u.username,
            "username": u.username,
            "avatar": u.avatar or "",
            "bio": (getattr(u, "bio", "") or ""),
            "level": 1,
            "is_vip": _vip(u),
            "match_type": match_type,
            "follower_count": 0,
        })

    # 1) UID 精确匹配（最高优先级）
    if kw.isdigit():
        try:
            u = db.query(User).filter(User.uid == kw).first()
            if u:
                _push(u, "uid")
        except Exception:
            pass

    # 2) 昵称 / 用户名模糊匹配
    like = "%" + kw + "%"
    try:
        rows = (db.query(User)
                .filter(or_(User.nickname.like(like), User.username.like(like)))
                .filter(User.is_banned == False)
                .order_by(desc(User.exp))
                .limit(limit).all())
        for u in rows:
            if len(results) >= limit:
                break
            _push(u, "name")
    except Exception:
        pass

    return results[:limit]


@router.get("/channels", summary="搜索频道")
def search_channels(q: str = Query("", max_length=60),
                    limit: int = Query(30, ge=1, le=100),
                    offset: int = Query(0, ge=0),
                    db: Session = Depends(get_db),
                    current=Depends(get_current_user_optional)):
    kw = q.strip()
    query = db.query(Channel).filter(Channel.status == "approved")
    if kw:
        query = query.filter(or_(Channel.name.like("%%%s%%" % kw),
                                 Channel.intro.like("%%%s%%" % kw)))
    rows = query.order_by(desc(Channel.created_at)).offset(offset).limit(limit).all()
    uid = current.id if current else None
    out = []
    for c in rows:
        owner = db.query(User).filter(User.id == c.owner_id).first()
        joined = bool(uid) and (db.query(ChannelMember).filter(
            ChannelMember.channel_id == c.id, ChannelMember.user_id == uid).first() is not None)
        out.append({
            "id": c.id, "name": c.name or "", "intro": c.intro or "",
            "image": c.image or "", "owner_id": c.owner_id,
            "owner_name": (owner.nickname or owner.username) if owner else "",
            "owner_username": owner.username if owner else "",
            "status": c.status or "approved", "member_count": c.member_count or 0,
            "joined": joined,
            "created_at": c.created_at.isoformat() if c.created_at else "",
        })
    return out
