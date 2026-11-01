# -*- coding: utf-8 -*-
"""小德社区 v7 - 聊天扩展：黑名单 / 会话置顶"""
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import User, ChatBlacklist, ChatPin
from ..deps import get_current_user

router = APIRouter(prefix="/api/chat", tags=["聊天扩展"])


# ---------------- 黑名单 ----------------
@router.get("/blacklist", summary="我的黑名单列表")
def list_blacklist(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = db.query(ChatBlacklist).filter(ChatBlacklist.owner_id == current.id).all()
    out = []
    for r in rows:
        u = db.query(User).filter(User.id == r.target_id).first()
        if u:
            out.append({"id": u.id, "username": u.username, "nickname": u.nickname or "",
                        "avatar": u.avatar or ""})
    return out


@router.post("/blacklist/{uid}", summary="拉黑某人")
def add_blacklist(uid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    if uid == current.id:
        raise HTTPException(status_code=400, detail="不能拉黑自己")
    u = db.query(User).filter(User.id == uid).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    ex = (db.query(ChatBlacklist)
          .filter(ChatBlacklist.owner_id == current.id, ChatBlacklist.target_id == uid).first())
    if not ex:
        db.add(ChatBlacklist(owner_id=current.id, target_id=uid))
        db.commit()
    return {"ok": True, "blocked": True}


@router.delete("/blacklist/{uid}", summary="取消拉黑")
def del_blacklist(uid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    ex = (db.query(ChatBlacklist)
          .filter(ChatBlacklist.owner_id == current.id, ChatBlacklist.target_id == uid).first())
    if ex:
        db.delete(ex)
        db.commit()
    return {"ok": True, "blocked": False}


@router.get("/blacklist/check/{uid}", summary="某人是否被我拉黑")
def check_blacklist(uid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    ex = (db.query(ChatBlacklist)
          .filter(ChatBlacklist.owner_id == current.id, ChatBlacklist.target_id == uid).first())
    # 是否双向任一拉黑
    ex2 = (db.query(ChatBlacklist)
           .filter(ChatBlacklist.owner_id == uid, ChatBlacklist.target_id == current.id).first())
    return {"i_blocked": bool(ex), "blocked_me": bool(ex2)}


# ---------------- 会话置顶 ----------------
@router.get("/pins", summary="我的置顶会话ID列表")
def list_pins(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = db.query(ChatPin).filter(ChatPin.owner_id == current.id).all()
    return {"pins": [r.target_id for r in rows]}


@router.post("/pins/{uid}", summary="置顶会话")
def add_pin(uid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    ex = (db.query(ChatPin)
          .filter(ChatPin.owner_id == current.id, ChatPin.target_id == uid).first())
    if not ex:
        db.add(ChatPin(owner_id=current.id, target_id=uid))
        db.commit()
    return {"ok": True, "pinned": True}


@router.delete("/pins/{uid}", summary="取消置顶")
def del_pin(uid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    ex = (db.query(ChatPin)
          .filter(ChatPin.owner_id == current.id, ChatPin.target_id == uid).first())
    if ex:
        db.delete(ex)
        db.commit()
    return {"ok": True, "pinned": False}