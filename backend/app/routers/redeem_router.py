# -*- coding: utf-8 -*-
"""小德社区 - 卡密兑换（用户端）+ 我的定向消息"""
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import User, RedeemCode, RedeemLog, DirectMessage
from ..deps import get_current_user

router = APIRouter(prefix="/api/redeem", tags=["卡密兑换"])


def _is_vip(u):
    return u.vip_expire is not None and u.vip_expire > datetime.utcnow()


@router.post("", summary="使用卡密兑换（加余额/加会员）")
def redeem(data: dict, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    code = str(data.get("code", "")).strip()
    if not code:
        raise HTTPException(status_code=400, detail="请输入卡密")
    rc = db.query(RedeemCode).filter(RedeemCode.code == code).first()
    if not rc:
        raise HTTPException(status_code=404, detail="卡密无效")
    if rc.status == 1:
        raise HTTPException(status_code=400, detail="该卡密已被使用")
    if rc.status == 2:
        raise HTTPException(status_code=400, detail="该卡密已作废")
    if rc.expire_at and rc.expire_at < datetime.utcnow():
        raise HTTPException(status_code=400, detail="该卡密已过期")
    # 发放
    detail = ""
    if rc.kind == "balance":
        current.balance = (current.balance or 0) + int(rc.amount or 0)
        detail = "充值余额 +%d，当前余额 %d" % (rc.amount or 0, current.balance)
    elif rc.kind == "vip":
        base = current.vip_expire if (current.vip_expire and current.vip_expire > datetime.utcnow()) else datetime.utcnow()
        current.vip_expire = base + timedelta(days=int(rc.amount or 0))
        detail = "会员 +%d 天，到期 %s" % (rc.amount or 0, current.vip_expire.strftime("%Y-%m-%d"))
    else:
        raise HTTPException(status_code=400, detail="卡密类型异常")
    rc.status = 1
    rc.used_by = current.id
    rc.used_at = datetime.utcnow()
    db.add(RedeemLog(user_id=current.id, code=rc.code, kind=rc.kind, amount=rc.amount or 0, detail=detail))
    db.commit()
    db.refresh(current)
    return {
        "ok": True, "kind": rc.kind, "amount": rc.amount or 0, "detail": detail,
        "balance": current.balance or 0,
        "vip_expire": current.vip_expire.isoformat() if current.vip_expire else "",
        "is_vip": _is_vip(current),
    }


@router.get("/history", summary="我的兑换记录")
def history(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = db.query(RedeemLog).filter(RedeemLog.user_id == current.id).order_by(RedeemLog.id.desc()).limit(100).all()
    return [{
        "id": r.id, "code": r.code, "kind": r.kind, "amount": r.amount,
        "detail": r.detail, "created_at": r.created_at.isoformat() if r.created_at else "",
    } for r in rows]


# ==================== 我的定向消息 ====================
@router.get("/messages", summary="我的定向消息列表")
def my_messages(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = db.query(DirectMessage).filter(DirectMessage.user_id == current.id).order_by(DirectMessage.id.desc()).limit(100).all()
    return [{
        "id": m.id, "title": m.title, "content": m.content, "sender": m.sender,
        "is_read": bool(m.is_read), "created_at": m.created_at.isoformat() if m.created_at else "",
    } for m in rows]


@router.get("/messages/unread", summary="我的未读定向消息数")
def unread_count(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    n = db.query(DirectMessage).filter(DirectMessage.user_id == current.id, DirectMessage.is_read == False).count()  # noqa: E712
    return {"unread": n}


@router.post("/messages/{mid}/read", summary="标记定向消息已读")
def read_message(mid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    m = db.query(DirectMessage).filter(DirectMessage.id == mid, DirectMessage.user_id == current.id).first()
    if not m:
        raise HTTPException(status_code=404, detail="消息不存在")
    m.is_read = True
    db.commit()
    return {"ok": True}
