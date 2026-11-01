# -*- coding: utf-8 -*-
"""扫码登录 QR_LOGIN_V1

流程：
1. 网页端 POST /api/qr/create  -> 拿到 code + 二维码内容
2. 手机 App 扫码 -> POST /api/qr/confirm {code}  （需登录态）-> 绑定该用户
3. 网页端 GET /api/qr/poll/{code} -> 等待 confirmed 后返回 access_token
"""
import secrets
import time
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User
from ..auth import create_access_token
from ..deps import get_current_user

router = APIRouter(prefix="/api/qr", tags=["扫码登录"])

# code -> {user_id, status, created_at, token}
_STORE = {}
EXPIRE_SECONDS = 180


class QrCreateOut(BaseModel):
    code: str
    content: str
    expires_in: int


class QrConfirmIn(BaseModel):
    code: str


def _cleanup():
    now = time.time()
    dead = [k for k, v in _STORE.items() if now - v["created_at"] > EXPIRE_SECONDS]
    for k in dead:
        _STORE.pop(k, None)


@router.post("/create", response_model=QrCreateOut, summary="生成扫码登录码")
def qr_create():
    _cleanup()
    code = secrets.token_urlsafe(16)
    _STORE[code] = {
        "user_id": None,
        "status": "pending",     # pending -> confirmed -> used
        "created_at": time.time(),
        "token": None,
    }
    return QrCreateOut(
        code=code,
        content="XIAODE:LOGIN:" + code,
        expires_in=EXPIRE_SECONDS,
    )


@router.post("/confirm", summary="手机扫码后确认登录")
def qr_confirm(data: QrConfirmIn,
               db: Session = Depends(get_db),
               current: User = Depends(get_current_user)):
    code = (data.code or "").strip()
    rec = _STORE.get(code)
    if not rec:
        raise HTTPException(status_code=404, detail="二维码不存在或已过期")
    if time.time() - rec["created_at"] > EXPIRE_SECONDS:
        _STORE.pop(code, None)
        raise HTTPException(status_code=410, detail="二维码已过期，请刷新重试")
    if rec["status"] != "pending":
        raise HTTPException(status_code=400, detail="该二维码已被使用")

    rec["user_id"] = current.id
    rec["status"] = "confirmed"
    rec["token"] = create_access_token(
        current.id, current.username, getattr(current, "role", "user") or "user"
    )
    rec["nickname"] = current.nickname or current.username
    rec["avatar"] = current.avatar or ""
    return {"ok": True, "msg": "登录确认成功"}


@router.get("/poll/{code}", summary="网页轮询扫码状态")
def qr_poll(code: str, db: Session = Depends(get_db)):
    rec = _STORE.get(code)
    if not rec:
        raise HTTPException(status_code=404, detail="二维码不存在或已过期")
    if time.time() - rec["created_at"] > EXPIRE_SECONDS:
        _STORE.pop(code, None)
        raise HTTPException(status_code=410, detail="二维码已过期")

    if rec["status"] == "pending":
        return {"status": "pending"}
    if rec["status"] == "confirmed":
        uid = rec["user_id"]
        u = db.query(User).filter(User.id == uid).first() if uid else None
        token = rec["token"]
        rec["status"] = "used"
        return {
            "status": "confirmed",
            "token": token,
            "user": {
                "id": u.id if u else 0,
                "username": u.username if u else "",
                "nickname": (u.nickname or u.username) if u else "",
                "avatar": (u.avatar or "") if u else "",
                "uid": (u.uid or "") if u else "",
            },
        }
    return {"status": "used"}
