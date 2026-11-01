# -*- coding: utf-8 -*-
"""小德日历 API - 上传路由：头像上传 / 动态图片上传 / 个人资料更新"""
import os
import io as _io
import uuid
from PIL import Image
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User
from ..schemas import (UserOut, ProfileUpdateIn, ChangePasswordIn,
                       ChangeUsernameIn, ChangeEmailIn, ChatSettingIn)
from ..deps import get_current_user
from ..auth import hash_password, verify_password
from ..models import EmailCode
from datetime import datetime, timedelta
from ..routers.auth_router import _check_code

router = APIRouter(prefix="/api/users", tags=["用户"])

# 上传根目录：<项目>/uploads，对外通过 /uploads/xxx 访问
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")
AVATAR_DIR = os.path.join(UPLOAD_DIR, "avatars")
IMAGE_DIR = os.path.join(UPLOAD_DIR, "images")

os.makedirs(AVATAR_DIR, exist_ok=True)
os.makedirs(IMAGE_DIR, exist_ok=True)

ALLOWED_EXT = {"jpg", "jpeg", "png", "gif", "webp", "bmp"}
MAX_AVATAR_BYTES = 10 * 1024 * 1024   # 头像最大 10MB(超过2MB自动压缩)
MAX_IMAGE_BYTES = 20 * 1024 * 1024    # 动态图/日记图最大 20MB(超过2MB自动压缩)
MAX_VIDEO_BYTES = 10 * 1024 * 1024    # 日记视频最大 10MB

VIDEO_DIR = os.path.join(UPLOAD_DIR, "videos")
os.makedirs(VIDEO_DIR, exist_ok=True)

ALLOWED_VIDEO_EXT = {"mp4", "mov", "m4v", "3gp", "avi", "mkv", "webm"}


TARGET_BYTES = 2 * 1024 * 1024   # 目标：压到 2MB 以下

def _compress_image(data: bytes, ext: str):
    """若图片 > 2MB，用 Pillow 压缩到 2MB 以下；返回 (新数据, 新扩展名)"""
    try:
        if len(data) <= TARGET_BYTES:
            return data, ext
        im = Image.open(_io.BytesIO(data))
        im.load()
        # 有透明通道的保留 png，其余转 jpg
        has_alpha = im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info)
        out_ext = "png" if has_alpha else "jpg"
        if not has_alpha and im.mode not in ("RGB", "L"):
            im = im.convert("RGB")
        # 限制最长边 1600
        max_side = 1600
        if max(im.size) > max_side:
            ratio = max_side / float(max(im.size))
            im = im.resize((max(1, int(im.size[0]*ratio)), max(1, int(im.size[1]*ratio))))
        quality = 85
        for _ in range(8):
            buf = _io.BytesIO()
            if out_ext == "jpg":
                im.save(buf, format="JPEG", quality=quality, optimize=True)
            else:
                im.save(buf, format="PNG", optimize=True)
            d = buf.getvalue()
            if len(d) <= TARGET_BYTES or quality <= 40:
                return d, out_ext
            quality -= 10
        return d, out_ext
    except Exception as e:
        # 压缩失败就返回原图（避免丢图）
        return data, ext


def _save_upload(file: UploadFile, folder: str, sub: str, max_bytes: int) -> str:
    ext = (file.filename or "").rsplit(".", 1)[-1].lower() if "." in (file.filename or "") else ""
    if ext not in ALLOWED_EXT:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="只支持 jpg/png/gif/webp/bmp 图片")

    data = file.file.read()
    if len(data) > max_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="图片过大，最大 %d MB" % (max_bytes // 1024 // 1024),
        )

    # >2MB 自动压缩
    data, ext = _compress_image(data, ext)
    name = "%s_%s.%s" % (datetime.now().strftime("%Y%m%d%H%M%S"), uuid.uuid4().hex[:8], ext)
    path = os.path.join(folder, name)
    with open(path, "wb") as f:
        f.write(data)

    # 对外可访问的相对路径（App 拼接服务地址前缀）
    return "/uploads/%s/%s" % (sub, name)


@router.get("/me", response_model=UserOut, summary="获取个人资料")
def get_profile(current: User = Depends(get_current_user)):
    return current


@router.put("/me", response_model=UserOut, summary="修改个人资料（昵称）")
def update_profile(
    data: ProfileUpdateIn,
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    if data.nickname is not None and data.nickname != "":
        current.nickname = data.nickname
    if data.bio is not None:
        current.bio = data.bio.strip()[:80]
    db.commit()
    db.refresh(current)
    return current


@router.post("/avatar", response_model=UserOut, summary="上传/更换头像")
def upload_avatar(
    file: UploadFile = File(..., description="头像图片"),
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    url = _save_upload(file, AVATAR_DIR, "avatars", MAX_AVATAR_BYTES)
    current.avatar = url
    db.commit()
    db.refresh(current)
    return current


@router.post("/images", summary="上传动态图片（一次一张，最多传3张后拼成列表）")
def upload_image(
    file: UploadFile = File(..., description="动态图片"),
    current: User = Depends(get_current_user),
):
    url = _save_upload(file, IMAGE_DIR, "images", MAX_IMAGE_BYTES)
    return {"ok": True, "url": url}


@router.post("/videos", summary="上传日记视频（单个，最大 10MB）")
def upload_video(
    file: UploadFile = File(..., description="日记视频"),
    current: User = Depends(get_current_user),
):
    ext = (file.filename or "").rsplit(".", 1)[-1].lower() if "." in (file.filename or "") else ""
    if ext not in ALLOWED_VIDEO_EXT:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="只支持 mp4/mov/m4v/3gp/avi/mkv/webm 视频",
        )

    # 先读入内存校验大小，避免超限文件落盘
    data = file.file.read()
    if len(data) > MAX_VIDEO_BYTES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="视频过大，最大 10 MB",
        )

    name = "%s_%s.%s" % (datetime.now().strftime("%Y%m%d%H%M%S"), uuid.uuid4().hex[:8], ext)
    path = os.path.join(VIDEO_DIR, name)
    with open(path, "wb") as f:
        f.write(data)

    return {"ok": True, "url": "/uploads/videos/%s" % name}

@router.post("/change_password", summary="修改密码（需原密码）")
def change_password(data: ChangePasswordIn, db: Session = Depends(get_db),
                    current: User = Depends(get_current_user)):
    if not verify_password(data.old_password, current.password_hash):
        raise HTTPException(status_code=400, detail="原密码错误")
    current.password_hash = hash_password(data.new_password)
    db.commit()
    return {"ok": True, "msg": "密码已修改"}


@router.post("/change_username", summary="修改用户名（需密码）")
def change_username(data: ChangeUsernameIn, db: Session = Depends(get_db),
                    current: User = Depends(get_current_user)):
    new_u = (data.new_username or "").strip()
    if len(new_u) < 3:
        raise HTTPException(status_code=400, detail="用户名至少 3 个字符")
    if not verify_password(data.password, current.password_hash):
        raise HTTPException(status_code=400, detail="密码错误")
    if new_u == current.username:
        raise HTTPException(status_code=400, detail="新用户名与当前相同")
    if db.query(User).filter(User.username == new_u).first():
        raise HTTPException(status_code=400, detail="该用户名已被占用")
    current.username = new_u
    db.commit()
    db.refresh(current)
    return {"ok": True, "msg": "用户名已修改", "user": UserOut.model_validate(current)}


@router.post("/change_email", summary="换绑邮箱（需新邮箱验证码）")
def change_email(data: ChangeEmailIn, db: Session = Depends(get_db),
                 current: User = Depends(get_current_user)):
    new_e = (data.new_email or "").strip()
    if "@" not in new_e or "." not in new_e:
        raise HTTPException(status_code=400, detail="请输入正确的邮箱")
    if db.query(User).filter(User.email == new_e, User.id != current.id).first():
        raise HTTPException(status_code=400, detail="该邮箱已被其他账号绑定")
    if not _check_code(db, new_e, (data.code or "").strip(), "bind"):
        raise HTTPException(status_code=400, detail="验证码错误或已过期")
    current.email = new_e
    db.commit()
    db.refresh(current)
    return {"ok": True, "msg": "邮箱已更换", "user": UserOut.model_validate(current)}


@router.put("/chat_setting", summary="修改聊天个性化（气泡主题/背景）")
def chat_setting(data: ChatSettingIn, db: Session = Depends(get_db),
                 current: User = Depends(get_current_user)):
    if data.chat_theme is not None:
        current.chat_theme = data.chat_theme
    if data.chat_bg is not None:
        current.chat_bg = data.chat_bg
    if getattr(data, "name_color", None) is not None:
        current.name_color = data.name_color
    if getattr(data, "text_effect", None) is not None:
        current.text_effect = data.text_effect
    db.commit()
    db.refresh(current)
    return {"ok": True, "chat_theme": current.chat_theme, "chat_bg": current.chat_bg,
            "name_color": current.name_color, "text_effect": current.text_effect}


class DeleteAccountIn(BaseModel):
    password: str


@router.post("/delete_account", summary="注销账号（需密码，不可恢复）")
def delete_account(data: DeleteAccountIn, db: Session = Depends(get_db),
                   current: User = Depends(get_current_user)):
    from ..models import (Moment, Comment, Diary, MomentLike, CommentLike,
                          Notification, Friendship, ChatMessage, TaskLog,
                          ChatBlacklist, ChatPin, ChannelMember, UserDevice,
                          AppItem, AppComment, EmailCode)
    if not verify_password(data.password, current.password_hash):
        raise HTTPException(status_code=400, detail="密码错误")
    if getattr(current, "is_admin", False):
        raise HTTPException(status_code=400, detail="管理员账号不可自助注销")
    uid = current.id

    def _del(model, **kw):
        q = db.query(model)
        for k, v in kw.items():
            q = q.filter(getattr(model, k) == v)
        q.delete(synchronize_session=False)

    try:
        _del(Moment, user_id=uid)
        _del(Comment, user_id=uid)
        _del(Diary, user_id=uid)
        _del(MomentLike, user_id=uid)
        _del(CommentLike, user_id=uid)
        _del(Notification, user_id=uid)
        _del(Friendship, user_id=uid)
        _del(Friendship, friend_id=uid)
        _del(ChatMessage, sender_id=uid)
        _del(TaskLog, user_id=uid)
        _del(ChatBlacklist, user_id=uid)
        _del(ChatPin, user_id=uid)
        _del(ChannelMember, user_id=uid)
        _del(UserDevice, user_id=uid)
        _del(AppItem, user_id=uid)
        _del(AppComment, user_id=uid)
        if current.email:
            _del(EmailCode, email=current.email)
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail="注销失败：%s" % e)

    db.delete(current)
    db.commit()
    return {"ok": True, "msg": "账号已注销"}
