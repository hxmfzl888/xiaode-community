# -*- coding: utf-8 -*-
"""小德日历 API - 认证路由：注册 / 登录 / 我的信息"""
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User, EmailCode, CaptchaChallenge
from ..schemas import (RegisterIn, LoginIn, TokenOut, UserOut,
                       SendCodeIn, ResetPasswordIn)
from ..auth import hash_password, verify_password, create_access_token
from ..deps import get_current_user
from .. import mailer

router = APIRouter(prefix="/api/auth", tags=["认证"])



CODE_TTL_MINUTES = 10

def _check_code(db, email, code, purpose):
    """校验邮箱验证码：未使用 + 未过期 + 匹配"""
    if not email or not code:
        return False
    row = (db.query(EmailCode)
           .filter(EmailCode.email == email,
                   EmailCode.purpose == purpose,
                   EmailCode.used == False)
           .order_by(EmailCode.created_at.desc())
           .first())
    if not row or row.code != code:
        return False
    if datetime.utcnow() - row.created_at > timedelta(minutes=CODE_TTL_MINUTES):
        return False
    row.used = True
    db.commit()
    return True


# ============================================================
# CAPTCHA_V1: 人机验证
# 流程：GET /captcha 拿到题目+token → 用户算 → 发验证码时带上 token+答案
# ============================================================

@router.get("/captcha", summary="获取人机验证题目")
def get_captcha(db: Session = Depends(get_db)):
    import random as _r, uuid as _uuid
    a = _r.randint(1, 9)
    b = _r.randint(1, 9)
    op = _r.choice(["+", "-", "×"])
    if op == "+":
        ans = a + b
    elif op == "-":
        # 保证结果非负
        if a < b:
            a, b = b, a
        ans = a - b
    else:
        a = _r.randint(2, 9)
        b = _r.randint(2, 9)
        ans = a * b

    token = _uuid.uuid4().hex
    # 清理 5 分钟前的旧记录
    try:
        _old = datetime.utcnow() - timedelta(minutes=5)
        db.query(CaptchaChallenge).filter(CaptchaChallenge.created_at < _old).delete(synchronize_session=False)
    except Exception:
        pass
    db.add(CaptchaChallenge(token=token, answer=int(ans), used=False))
    db.commit()
    return {
        "token": token,
        "question": "%d %s %d = ?" % (a, op, b),
        "expires_in": 300,
    }


def _verify_captcha(db, token: str, answer) -> bool:
    """校验人机验证；通过后作废该 token（一次性）"""
    token = (token or "").strip()
    if not token:
        return False
    try:
        ans_int = int(str(answer).strip())
    except Exception:
        return False
    try:
        row = db.query(CaptchaChallenge).filter(CaptchaChallenge.token == token).first()
        if not row or row.used:
            return False
        # 5 分钟过期
        if datetime.utcnow() - row.created_at > timedelta(minutes=5):
            return False
        if int(row.answer) != ans_int:
            return False
        row.used = True  # 一次性
        db.commit()
        return True
    except Exception:
        db.rollback()
        return False

@router.post("/send_code", summary="发送邮箱验证码")
def send_code(data: SendCodeIn, db: Session = Depends(get_db)):
    purpose = (data.purpose or "register").strip()

    # CAPTCHA_V1_ENFORCE: 发验证码前必须通过人机验证
    _ct = getattr(data, "captcha_token", None) or ""
    _ca = getattr(data, "captcha_answer", None)
    if not _verify_captcha(db, _ct, _ca):
        raise HTTPException(
            status_code=400,
            detail="人机验证未通过，请重新计算题目",
        )
    if purpose not in ("register", "reset", "bind"):
        raise HTTPException(status_code=400, detail="验证码用途不合法")
    email = (data.email or "").strip()
    if "@" not in email or "." not in email:
        raise HTTPException(status_code=400, detail="请输入正确的邮箱地址")
    if purpose == "register":
        if db.query(User).filter(User.email == email).first():
            raise HTTPException(status_code=400, detail="该邮箱已被注册")
    elif purpose == "reset":
        if not db.query(User).filter(User.email == email).first():
            raise HTTPException(status_code=400, detail="该邮箱未绑定任何账号")
    elif purpose == "bind":
        if db.query(User).filter(User.email == email).first():
            raise HTTPException(status_code=400, detail="该邮箱已被其他账号绑定")
    recent = (db.query(EmailCode)
              .filter(EmailCode.email == email, EmailCode.purpose == purpose)
              .order_by(EmailCode.created_at.desc()).first())
    if recent and datetime.utcnow() - recent.created_at < timedelta(seconds=60):
        raise HTTPException(status_code=429, detail="发送太频繁，请 60 秒后再试")
    if not mailer.is_configured():
        raise HTTPException(status_code=500, detail="邮件服务未配置")
    code = mailer.make_code()
    if not mailer.send_code(email, code, purpose):
        raise HTTPException(status_code=500, detail="邮件发送失败，请稍后重试")
    db.add(EmailCode(email=email, code=code, purpose=purpose, used=False))
    db.commit()
    return {"ok": True, "msg": "验证码已发送至邮箱"}


@router.post("/reset_password", summary="忘记密码")
def reset_password(data: ResetPasswordIn, db: Session = Depends(get_db)):
    email = (data.email or "").strip()
    user = db.query(User).filter(User.email == email).first()
    if not user:
        raise HTTPException(status_code=400, detail="该邮箱未绑定任何账号")
    if not _check_code(db, email, data.code, "reset"):
        raise HTTPException(status_code=400, detail="验证码错误或已过期")
    user.password_hash = hash_password(data.new_password)
    db.commit()
    return {"ok": True, "msg": "密码已重置"}

@router.post("/register", response_model=TokenOut, summary="注册账号")
def register(data: RegisterIn, db: Session = Depends(get_db)):
    if db.query(User).filter(User.username == data.username).first():
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="用户名已被占用")
    email = (data.email or "").strip()
    if not email:
        raise HTTPException(status_code=400, detail="请填写邮箱")
    if db.query(User).filter(User.email == email).first():
        raise HTTPException(status_code=400, detail="该邮箱已被注册")
    if (data.code or "").strip() == "":
        raise HTTPException(status_code=400, detail="请填写邮箱验证码")
    if not _check_code(db, email, (data.code or "").strip(), "register"):
        raise HTTPException(status_code=400, detail="验证码错误或已过期")

    # UID_V1: 注册时自动分配 8 位唯一 UID
    from ..models import gen_unique_uid
    _uid = gen_unique_uid(db, User)
    user = User(
        username=data.username,
        nickname=data.nickname or data.username,
        password_hash=hash_password(data.password),
        email=email,
        uid=_uid,
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    token = create_access_token(user.id, user.username)
    return TokenOut(access_token=token, user=UserOut.model_validate(user))


@router.post("/login", response_model=TokenOut, summary="登录")
def login(data: LoginIn, db: Session = Depends(get_db)):
    uname = (data.username or "").strip()
    user = db.query(User).filter(User.username == uname).first()
    if not user and "@" in uname:
        user = db.query(User).filter(User.email == uname).first()
    if not user or not verify_password(data.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名或密码错误")

    # v9.7: 封禁检查（支持限时封禁自动过期）
    banned = getattr(user, "is_banned", False)
    ban_until = getattr(user, "ban_until", None)
    if banned and ban_until is not None:
        from datetime import datetime as _dtn
        if _dtn.utcnow() >= ban_until:
            user.is_banned = False; user.ban_reason = ""; user.ban_until = None
            db.commit(); banned = False
    if banned:
        from datetime import datetime as _dtn
        if ban_until is None:
            msg = "您的账号已被永久封禁"
        else:
            secs = (ban_until - _dtn.utcnow()).total_seconds()
            days = (int(secs // 86400) + 1) if secs > 0 else 0
            msg = "您的账号已被封禁，还需 " + str(days) + " 天解封"
        reason = getattr(user, "ban_reason", "") or ""
        if reason:
            msg = msg + "（原因：" + reason + "）"
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=msg)
    token = create_access_token(user.id, user.username)
    return TokenOut(access_token=token, user=UserOut.model_validate(user))


@router.get("/me", response_model=UserOut, summary="获取当前登录用户")
def me(current: User = Depends(get_current_user)):
    return current