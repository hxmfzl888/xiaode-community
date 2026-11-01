# -*- coding: utf-8 -*-
"""小德日历 API - 安全认证（密码加密 + JWT 令牌）"""
import os
from datetime import datetime, timedelta

from jose import JWTError, jwt
from passlib.context import CryptContext

# ⚠️ 正式部署务必改成随机的环境变量密钥，不要用默认值
SECRET_KEY = os.environ.get("XIAODE_SECRET_KEY", "change-me-please-xiaode-calendar-secret")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_DAYS = 30

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain: str, hashed: str) -> bool:
    try:
        return pwd_context.verify(plain, hashed)
    except Exception:
        return False


def create_access_token(user_id: int, username: str, role: str = "user") -> str:
    expire = datetime.utcnow() + timedelta(days=ACCESS_TOKEN_EXPIRE_DAYS)
    payload = {"sub": str(user_id), "username": username, "role": role, "exp": expire}
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)


def decode_token(token: str):
    """返回 payload dict，失败返回 None"""
    try:
        return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    except JWTError:
        return None