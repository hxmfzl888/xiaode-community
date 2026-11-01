# -*- coding: utf-8 -*-
"""小德社区 v8 - 会员系统：购买/状态/整页背景/气泡"""
import os, uuid
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File
from sqlalchemy.orm import Session
from sqlalchemy import text
from ..database import get_db
from ..models import User
from ..deps import get_current_user

router = APIRouter(prefix="/api/vip", tags=["会员"])

BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")
BG_DIR = os.path.join(UPLOAD_DIR, "vipbg")
os.makedirs(BG_DIR, exist_ok=True)

# 价格表：从数据库 vip_plans 表读取（后台可管理）
# 兼容旧代码：保留 PLANS 名称，改为函数动态获取
def _load_plans(db: Session):
    """从数据库读取启用的套餐"""
    rows = db.execute(text(
        "SELECT key, name, days, gift_days, price, badge, intro FROM vip_plans "
        "WHERE enabled=1 ORDER BY sort, id"
    )).fetchall()
    out = {}
    for r in rows:
        out[r[0]] = {
            "name": r[1], "days": int(r[2]) + int(r[3] or 0),
            "price": float(r[4]), "badge": r[5] or "", "intro": r[6] or "",
            "base_days": int(r[2]), "gift_days": int(r[3] or 0),
        }
    return out


def is_vip(u: User) -> bool:
    return u.vip_expire is not None and u.vip_expire > datetime.utcnow()


def _vip_out(u: User, db: Session = None):
    plans = []
    if db is not None:
        try:
            rows = db.execute(text(
                "SELECT key, name, days, gift_days, price, badge, intro FROM vip_plans "
                "WHERE enabled=1 ORDER BY sort, id"
            )).fetchall()
            plans = [{
                "key": r[0], "name": r[1],
                "days": int(r[2]) + int(r[3] or 0),
                "base_days": int(r[2]), "gift_days": int(r[3] or 0),
                "price": float(r[4]), "badge": r[5] or "", "intro": r[6] or "",
            } for r in rows]
        except Exception:
            plans = []
    return {
        "is_vip": is_vip(u),
        "vip_expire": u.vip_expire.isoformat() if u.vip_expire else "",
        "balance": u.balance or 0,
        "page_bg": u.page_bg or "",
        "chat_bubble": u.chat_bubble or "",
        "plans": plans,
    }


@router.get("/me", summary="我的会员信息")
def my_vip(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    return _vip_out(current, db)


@router.post("/buy", summary="购买会员（扣余额）")
def buy_vip(data: dict, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    key = data.get("plan", "")
    plans = _load_plans(db)
    if key not in plans:
        raise HTTPException(status_code=400, detail="无效的套餐或套餐已下架")
    plan = plans[key]
    price = float(plan["price"])
    cur_balance = float(current.balance or 0)
    if cur_balance + 1e-6 < price:
        raise HTTPException(status_code=400, detail="余额不足，当前 %.2f 金币，需要 %.2f 金币" % (cur_balance, price))

    before = current.vip_expire
    # 扣余额（同时同步 balance_cents）
    new_bal = round(cur_balance - price, 2)
    current.balance = new_bal
    current.balance_cents = int(round(new_bal * 100))
    # 续费从较晚的时间起算
    base = current.vip_expire if (current.vip_expire and current.vip_expire > datetime.utcnow()) else datetime.utcnow()
    current.vip_expire = base + timedelta(days=plan["days"])

    # 写订单
    try:
        db.execute(text(
            "INSERT INTO vip_orders(user_id,username,plan_key,plan_name,days,price,"
            "before_expire,after_expire,status,created_at) "
            "VALUES (:uid,:un,:pk,:pn,:d,:p,:be,:ae,'paid',:ca)"
        ), {
            "uid": current.id, "un": current.username or "",
            "pk": key, "pn": plan["name"], "d": plan["days"], "p": price,
            "be": before.isoformat() if before else None,
            "ae": current.vip_expire.isoformat() if current.vip_expire else None,
            "ca": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        })
    except Exception as e:
        print("[vip buy] 写订单失败:", e)

    db.commit(); db.refresh(current)
    return _vip_out(current, db)


@router.post("/upload_bg", summary="上传整页背景图（不压缩，会员专属）")
def upload_bg(file: UploadFile = File(...), db: Session = Depends(get_db),
              current: User = Depends(get_current_user)):
    if not is_vip(current):
        raise HTTPException(status_code=403, detail="会员专属功能")
    ext = (file.filename or "").rsplit(".", 1)[-1].lower() if "." in (file.filename or "") else "jpg"
    if ext not in {"jpg", "jpeg", "png", "gif", "webp", "bmp"}:
        raise HTTPException(status_code=400, detail="不支持的图片格式")
    data = file.file.read()
    if len(data) > 30 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="图片过大，最大 30MB")
    name = "bg_%s_%s.%s" % (datetime.now().strftime("%Y%m%d%H%M%S"), uuid.uuid4().hex[:8], ext)
    path = os.path.join(BG_DIR, name)
    with open(path, "wb") as f:
        f.write(data)   # 原图保存，不压缩
    url = "/uploads/vipbg/%s" % name
    current.page_bg = url
    db.commit()
    return {"ok": True, "url": url}


@router.delete("/bg", summary="移除整页背景")
def del_bg(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    current.page_bg = ""
    db.commit()
    return {"ok": True}


@router.post("/bubble", summary="设置聊天气泡样式（会员专属）")
def set_bubble(data: dict, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    if not is_vip(current):
        raise HTTPException(status_code=403, detail="会员专属功能")
    style = str(data.get("style", ""))[:30]
    current.chat_bubble = style
    db.commit()
    return {"ok": True, "chat_bubble": style}


@router.get("/bubbles", summary="可用气泡样式列表")
def bubbles():
    return [
        {"key": "", "name": "默认"},
        {"key": "gradient", "name": "渐变流光"},
        {"key": "neon", "name": "霓虹"},
        {"key": "gold", "name": "奢华金"},
        {"key": "ocean", "name": "深海蓝"},
        {"key": "sunset", "name": "日落橙"},
        {"key": "forest", "name": "森林绿"},
        {"key": "purple", "name": "梦幻紫"},
    ]