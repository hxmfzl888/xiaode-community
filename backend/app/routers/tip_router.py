# -*- coding: utf-8 -*-
"""小德社区 - 帖子打赏 + 金币账户（流水）"""
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import func
from ..database import get_db
from ..models import User, Moment, MomentTip, BalanceLog, Notification, UserDevice
from ..deps import get_current_user

router = APIRouter(prefix="/api/tip", tags=["打赏"])

COIN_PER_YUAN = 10  # 充值比例：1 元 = 10 金币


def _cents(user: User) -> int:
    """余额（分）。以 balance_cents 为准，兼容只有 balance 的老数据。"""
    c = getattr(user, "balance_cents", None)
    if not c:
        c = (user.balance or 0) * 100
    return int(c or 0)


def _add_balance(db: Session, user: User, delta: int, kind: str, detail: str, moment_id: int = 0):
    """delta 单位：金币（整数）。内部按分记账，支持小数余额。"""
    before = _cents(user)
    after = before + int(delta) * 100
    if after < 0:
        after = 0
    user.balance_cents = after
    user.balance = after // 100
    db.add(BalanceLog(
        user_id=user.id, delta=int(delta) * 100, kind=kind, detail=detail,
        moment_id=moment_id or 0, balance_after=after,
    ))


@router.get("/summary/{moment_id}", summary="帖子打赏汇总（总金额 + 打赏人数 + 前3位）")
def tip_summary(moment_id: int, db: Session = Depends(get_db)):
    total = db.query(func.coalesce(func.sum(MomentTip.amount), 0)).filter(MomentTip.moment_id == moment_id).scalar() or 0
    count = db.query(func.count(MomentTip.id)).filter(MomentTip.moment_id == moment_id).scalar() or 0
    rows = (db.query(MomentTip.from_user_id, func.sum(MomentTip.amount).label("amt"))
            .filter(MomentTip.moment_id == moment_id)
            .group_by(MomentTip.from_user_id)
            .order_by(func.sum(MomentTip.amount).desc())
            .limit(3).all())
    top = []
    for uid, amt in rows:
        u = db.query(User).filter(User.id == uid).first()
        if u:
            top.append({"user_id": uid, "nickname": u.nickname or u.username, "avatar": u.avatar or "", "amount": int(amt or 0)})
    return {"moment_id": moment_id, "total": int(total), "count": int(count), "top": top}


@router.post("/{moment_id}", summary="给帖子打赏金币")
def give_tip(moment_id: int, data: dict, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    try:
        amount = int(data.get("amount", 0))
    except Exception:
        amount = 0
    if amount <= 0:
        raise HTTPException(status_code=400, detail="打赏金额必须大于 0")
    if amount > 100000:
        raise HTTPException(status_code=400, detail="打赏金额过大")
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="帖子不存在")
    if m.user_id == current.id:
        raise HTTPException(status_code=400, detail="不能给自己打赏")
    if (current.balance or 0) < amount:
        raise HTTPException(status_code=400, detail="余额不足")

    author = db.query(User).filter(User.id == m.user_id).first()
    if not author:
        raise HTTPException(status_code=404, detail="作者不存在")

    _add_balance(db, current, -amount, "tip_out", "打赏帖子 #%d" % moment_id, moment_id)
    _add_balance(db, author, amount, "tip_in", "收到打赏 帖子 #%d" % moment_id, moment_id)
    db.add(MomentTip(moment_id=moment_id, from_user_id=current.id, to_user_id=author.id, amount=amount))
    try:
        db.add(Notification(
            user_id=author.id, actor_id=current.id, type="tip",
            moment_id=moment_id, content="%s 打赏了你 %d 金币" % (current.nickname or current.username, amount),
        ))
    except Exception:
        pass
    db.commit()
    db.refresh(current)
    _c2 = _cents(current)
    return {"ok": True, "balance": round(_c2 / 100.0, 2), "balance_cents": _c2, "amount": amount}


@router.get("/account", summary="我的金币账户：余额 + 收入/支出流水")
def account(limit: int = 100, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = (db.query(BalanceLog).filter(BalanceLog.user_id == current.id)
            .order_by(BalanceLog.id.desc()).limit(limit).all())
    logs = [{
        "id": r.id, "delta": r.delta, "kind": r.kind, "detail": r.detail,
        "moment_id": r.moment_id or 0, "balance_after": r.balance_after or 0,
        "created_at": r.created_at.isoformat() if r.created_at else "",
    } for r in rows]
    total_in = db.query(func.coalesce(func.sum(BalanceLog.delta), 0)).filter(
        BalanceLog.user_id == current.id, BalanceLog.delta > 0).scalar() or 0
    total_out = db.query(func.coalesce(func.sum(BalanceLog.delta), 0)).filter(
        BalanceLog.user_id == current.id, BalanceLog.delta < 0).scalar() or 0
    # COIN_PRECISION_V1: balance 以前返回整数（current.balance），
    # 会把 0.94 金币截断成 0，导致"签到得了金币但余额没变"。
    # 改为以分为准换算成金币浮点数，保留两位小数。
    _c = _cents(current)
    return {
        "balance": round(_c / 100.0, 2),
        "balance_cents": _c,
        "total_in": round(int(total_in) / 100.0, 2),
        "total_out": round(int(abs(total_out)) / 100.0, 2),
        "total_in_cents": int(total_in),
        "total_out_cents": int(abs(total_out)),
        "coin_per_yuan": COIN_PER_YUAN,
        "logs": [{**lg, "delta_coin": round(lg["delta"] / 100.0, 2),
                  "balance_after_coin": round(lg["balance_after"] / 100.0, 2)} for lg in logs],
    }


@router.get("/online", summary="当前在线人数（最近5分钟有心跳的用户）")
def online_count(db: Session = Depends(get_db)):
    since = datetime.utcnow() - timedelta(minutes=5)
    try:
        from sqlalchemy import text
        cnt = db.execute(text("select count(*) from online_heartbeat where last_seen >= :s"), {"s": since}).scalar() or 0
    except Exception:
        cnt = 0
    return {"online": int(cnt), "window_minutes": 5}


@router.post("/heartbeat", summary="上报在线心跳")
def heartbeat(data: dict = None, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    from sqlalchemy import text
    now = datetime.utcnow()
    nick = current.nickname or current.username or ""
    try:
        db.execute(text("""
            insert into online_heartbeat(user_id, nickname, last_seen) values(:u, :n, :t)
            on conflict(user_id) do update set last_seen=:t, nickname=:n
        """), {"u": current.id, "n": nick, "t": now})
        db.commit()
    except Exception as e:
        db.rollback()
        try:
            db.execute(text("delete from online_heartbeat where user_id=:u"), {"u": current.id})
            db.execute(text("insert into online_heartbeat(user_id, nickname, last_seen) values(:u,:n,:t)"),
                       {"u": current.id, "n": nick, "t": now})
            db.commit()
        except Exception:
            pass
    since = now - timedelta(minutes=5)
    cnt = db.execute(text("select count(*) from online_heartbeat where last_seen >= :s"), {"s": since}).scalar() or 0
    return {"ok": True, "online": int(cnt)}
