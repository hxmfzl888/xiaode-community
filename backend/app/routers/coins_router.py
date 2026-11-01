# -*- coding: utf-8 -*-
"""小德社区 - 小德币双向兑换专用接口（内部调用）"""
from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import User, BalanceLog
from ..deps import get_current_user

router = APIRouter(prefix="/api/coins", tags=["金币兑换"])

# 内部共享密钥（只允许小德币服务调用）
INTERNAL_KEY = "xd_int_9f3a7c2e5b8d4106"


def _cents(user: User) -> int:
    c = getattr(user, "balance_cents", None)
    if not c:
        c = (user.balance or 0) * 100
    return int(c or 0)


def _apply(db: Session, user: User, delta_cents: int, kind: str, detail: str):
    before = _cents(user)
    after = before + int(delta_cents)
    if after < 0:
        after = 0
    user.balance_cents = after
    user.balance = after / 100.0
    db.add(BalanceLog(
        user_id=user.id, delta=int(delta_cents), kind=kind, detail=detail,
        moment_id=0, balance_after=after,
    ))
    return before, after


@router.post("/exchange-credit", summary="小德币兑换为金币（给小德币服务内部调用）")
def exchange_credit(data: dict, x_internal_key: str = Header(default="", alias="X-Internal-Key"),
                    db: Session = Depends(get_db)):
    if x_internal_key != INTERNAL_KEY:
        raise HTTPException(status_code=403, detail="无权调用")
    uid = int(data.get("uid") or 0)
    amount = float(data.get("amount") or 0)
    ex_id = int(data.get("exchange_id") or 0)
    note = str(data.get("note") or "")
    if uid <= 0 or amount <= 0:
        raise HTTPException(status_code=400, detail="参数不合法")

    user = db.query(User).filter(User.id == uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="社区用户不存在")
    if getattr(user, "is_banned", 0):
        raise HTTPException(status_code=403, detail="社区账号已被封禁")

    # 幂等：同一 exchange_id 已处理过就直接返回
    tag = "小德币兑换#%d" % ex_id
    done = db.query(BalanceLog).filter(
        BalanceLog.user_id == uid, BalanceLog.kind == "exchange_in",
        BalanceLog.detail.like("小德币兑换#%d%%" % ex_id)
    ).first()
    if done:
        return {"ok": True, "duplicated": True, "balance": _cents(user) / 100.0}

    delta = int(round(amount * 100))
    before, after = _apply(db, user, delta, "exchange_in", tag + " +%.2f 金币" % amount)
    db.commit()
    return {"ok": True, "uid": uid, "before": before / 100.0,
            "after": after / 100.0, "amount": amount}


@router.post("/exchange-debit", summary="金币兑换为小德币（扣社区金币，内部调用）")
def exchange_debit(data: dict, x_internal_key: str = Header(default="", alias="X-Internal-Key"),
                   db: Session = Depends(get_db)):
    if x_internal_key != INTERNAL_KEY:
        raise HTTPException(status_code=403, detail="无权调用")
    uid = int(data.get("uid") or 0)
    amount = float(data.get("amount") or 0)
    ex_id = int(data.get("exchange_id") or 0)
    if uid <= 0 or amount <= 0:
        raise HTTPException(status_code=400, detail="参数不合法")

    user = db.query(User).filter(User.id == uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="社区用户不存在")
    if getattr(user, "is_banned", 0):
        raise HTTPException(status_code=403, detail="社区账号已被封禁")

    done = db.query(BalanceLog).filter(
        BalanceLog.user_id == uid, BalanceLog.kind == "exchange_out",
        BalanceLog.detail.like("小德币兑换#%d%%" % ex_id)
    ).first()
    if done:
        return {"ok": True, "duplicated": True, "balance": _cents(user) / 100.0}

    delta = -int(round(amount * 100))
    if _cents(user) + delta < 0:
        raise HTTPException(status_code=400, detail="社区金币不足")

    tag = "小德币兑换#%d" % ex_id
    before, after = _apply(db, user, delta, "exchange_out", tag + " -%.2f 金币" % amount)
    db.commit()
    return {"ok": True, "uid": uid, "before": before / 100.0,
            "after": after / 100.0, "amount": amount}


@router.get("/balance/{uid}", summary="查询社区金币余额（内部调用）")
def balance_of(uid: int, x_internal_key: str = Header(default="", alias="X-Internal-Key"),
               db: Session = Depends(get_db)):
    if x_internal_key != INTERNAL_KEY:
        raise HTTPException(status_code=403, detail="无权调用")
    user = db.query(User).filter(User.id == uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="社区用户不存在")
    return {"ok": True, "uid": uid, "balance": _cents(user) / 100.0,
            "username": user.username, "nickname": user.nickname or ""}

# ============ WALLET_AGG_V1: 我的钱包（小德币 + 金币） ============
BANK_BASE = "http://127.0.0.1:3000"
BANK_WEB = "https://yh.delai.fun"


def _bank_lookup(cuid: int) -> dict:
    """查小德银行：是否绑定 + XD 余额。失败时返回未绑定。"""
    try:
        import httpx
        with httpx.Client(timeout=3.0) as cli:
            r = cli.get(
                BANK_BASE + "/api/internal/community-lookup/%d" % cuid,
                headers={"X-Internal-Key": INTERNAL_KEY},
            )
            if r.status_code == 200:
                return r.json()
    except Exception:
        pass
    return {"bound": False, "username": "", "xd_balance": 0.0, "nickname": ""}


@router.get("/wallet", summary="我的钱包：小德币 + 金币")
def my_wallet(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    """WALLET_AGG_V1

    返回：
    - coin: 金币（社区本地）
    - xd:   小德币（小德银行，实时查询）
    """
    cents = _cents(current)
    bk = _bank_lookup(current.id)

    return {
        "coin": {
            "balance_cents": cents,
            "balance": round(cents / 100.0, 2),
            "coin_per_yuan": 10,
        },
        "xd": {
            "bound": bool(bk.get("bound")),
            "balance": float(bk.get("xd_balance") or 0),
            "bank_username": bk.get("username") or "",
            "rate_to_coin": 10.0,
            "bank_bind_url": BANK_WEB + "/me?cuid=%d" % current.id,
            "bank_home_url": BANK_WEB,
        },
        "uid": current.uid or "",
    }
