# -*- coding: utf-8 -*-
"""
战壕风云1917 —— 游戏登录接口
复用「小德社区」的 users 表与 bcrypt 密码，签发带 scope=game 的独立 JWT。

统一返回格式：
    { "code": 0, "msg": "ok", "data": {...} }

code 约定：
    0    成功
    1001 账号或密码错误
    1002 账号已存在
    1003 参数不合法
    1004 token 无效或过期
    1005 用户不存在
    2000 服务器错误
"""
import re
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session
from jose import jwt, JWTError

from ..database import get_db
from ..models import User
from ..auth import hash_password, verify_password, SECRET_KEY, ALGORITHM

router = APIRouter(prefix="/api", tags=["game"])

GAME_TOKEN_EXPIRE_DAYS = 30
GAME_SCOPE = "game"
USERNAME_RE = re.compile(r"^[A-Za-z0-9_]{3,20}$")


# ---------------- 统一响应 ----------------
def ok(data=None, msg="ok"):
    return {"code": 0, "msg": msg, "data": data if data is not None else {}}


def fail(code, msg):
    return {"code": code, "msg": msg, "data": {}}


# ---------------- 出入参模型 ----------------
class LoginIn(BaseModel):
    username: str = Field(..., min_length=1, max_length=50)
    password: str = Field(..., min_length=1, max_length=128)


class RegisterIn(BaseModel):
    username: str = Field(..., min_length=3, max_length=20)
    password: str = Field(..., min_length=6, max_length=128)
    nickname: str = Field(..., min_length=1, max_length=20)


# ---------------- 工具 ----------------
def _level(u: User) -> int:
    """等级：优先用 users.level，没有则按 exp 估算"""
    try:
        if (u.level or 0) > 0:
            return u.level
        return max(1, (u.exp or 0) // 100 + 1)
    except Exception:
        return 1


def _player_dict(u: User) -> dict:
    return {
        "uid": u.id,
        "username": u.username,
        "nickname": u.nickname or u.username,
        "level": _level(u),
        "exp": u.exp or 0,
        "avatar": u.avatar or "",
        "is_vip": bool(u.vip_expire and u.vip_expire > datetime.utcnow()),
        "vip_expire": u.vip_expire.isoformat() if u.vip_expire else "",
    }


def _make_game_token(u: User) -> str:
    expire = datetime.utcnow() + timedelta(days=GAME_TOKEN_EXPIRE_DAYS)
    payload = {
        "sub": str(u.id),
        "username": u.username,
        "scope": GAME_SCOPE,   # 与社区 App 的 token 隔离
        "exp": expire,
    }
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)


def _decode_game_token(token: str):
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        if payload.get("scope") != GAME_SCOPE:
            return None
        return payload
    except JWTError:
        return None


def _bearer(authorization: str = Header(default="")) -> str:
    if not authorization:
        return ""
    parts = authorization.split()
    if len(parts) == 2 and parts[0].lower() == "bearer":
        return parts[1]
    return authorization.strip()


# ---------------- 接口 ----------------
@router.post("/login", summary="游戏登录")
def game_login(data: LoginIn, db: Session = Depends(get_db)):
    u = db.query(User).filter(User.username == data.username).first()
    if not u or not verify_password(data.password, u.password_hash):
        return fail(1001, "账号或密码错误")
    token = _make_game_token(u)
    return ok({"token": token, "player": _player_dict(u)}, "登录成功")


@router.post("/register", summary="游戏注册（同时创建小德社区账号）")
def game_register(data: RegisterIn, db: Session = Depends(get_db)):
    if not USERNAME_RE.match(data.username):
        return fail(1003, "账号需为 3-20 位字母/数字/下划线")
    if db.query(User).filter(User.username == data.username).first():
        return fail(1002, "该账号已存在，请直接登录")
    try:
        u = User(
            username=data.username,
            nickname=data.nickname,
            password_hash=hash_password(data.password),
            avatar="",
        )
        db.add(u)
        db.commit()
        db.refresh(u)
    except Exception as e:
        db.rollback()
        return fail(2000, f"注册失败：{e}")
    token = _make_game_token(u)
    return ok({"token": token, "player": _player_dict(u)}, "注册成功")


@router.get("/userinfo", summary="查询玩家信息（带 token）")
def game_userinfo(authorization: str = Header(default=""),
                  db: Session = Depends(get_db)):
    token = _bearer(authorization)
    payload = _decode_game_token(token)
    if not payload:
        return fail(1004, "登录状态无效或已过期，请重新登录")
    try:
        uid = int(payload.get("sub", "0"))
    except Exception:
        return fail(1004, "登录状态无效")
    u = db.query(User).filter(User.id == uid).first()
    if not u:
        return fail(1005, "用户不存在")
    return ok(_player_dict(u), "ok")

@router.post("/game/issue_token", summary="用小德社区App登录态换取游戏token（供App唤起登录用）")
def issue_game_token(authorization: str = Header(default=""),
                     db: Session = Depends(get_db)):
    """
    App 已登录（带社区 token）时，换取一个 scope=game 的 token，
    用于回跳游戏 App。
    """
    from ..auth import decode_token
    tok = _bearer(authorization)
    payload = decode_token(tok) if tok else None
    if not payload:
        return fail(1004, "小德社区登录态无效，请先登录")
    try:
        uid = int(payload.get("sub", "0"))
    except Exception:
        return fail(1004, "登录态无效")
    u = db.query(User).filter(User.id == uid).first()
    if not u:
        return fail(1005, "用户不存在")
    gt = _make_game_token(u)
    return ok({"token": gt, "player": _player_dict(u)}, "ok")


@router.get("/game/avatar", summary="获取玩家头像（带token）")
def game_avatar(authorization: str = Header(default=""),
                db: Session = Depends(get_db)):
    tok = _bearer(authorization)
    payload = _decode_game_token(tok)
    if not payload:
        return fail(1004, "登录状态无效或已过期")
    try:
        uid = int(payload.get("sub", "0"))
    except Exception:
        return fail(1004, "登录状态无效")
    u = db.query(User).filter(User.id == uid).first()
    if not u:
        return fail(1005, "用户不存在")
    return ok({"avatar": u.avatar or "", "nickname": u.nickname or u.username}, "ok")
