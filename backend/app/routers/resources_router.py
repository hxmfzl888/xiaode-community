# -*- coding: utf-8 -*-
"""小德社区 - 资源中心：上传 / 列表 / 搜索 / 详情 / 审核 / 打赏 / 购买"""
import os
import json
import uuid
from datetime import datetime
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File
from sqlalchemy import or_, desc
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import User, ResourceItem, ResourceTip, ResourceUnlock, BalanceLog, ResourceComment
from ..deps import get_current_user, get_current_admin, get_current_user_optional
from ..schemas import BaseModel

router = APIRouter(prefix="/api/resources", tags=["资源中心"])

BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")
RES_COVER_DIR = os.path.join(UPLOAD_DIR, "res_covers")
RES_SHOT_DIR = os.path.join(UPLOAD_DIR, "res_shots")
RES_FILE_DIR = os.path.join(UPLOAD_DIR, "res_files")
for _d in (RES_COVER_DIR, RES_SHOT_DIR, RES_FILE_DIR):
    os.makedirs(_d, exist_ok=True)

ALLOWED_IMG_EXT = {"jpg", "jpeg", "png", "gif", "webp", "bmp"}
MAX_COVER_BYTES = 5 * 1024 * 1024
MAX_SHOT_BYTES = 10 * 1024 * 1024
MAX_FILE_BYTES = 200 * 1024 * 1024

RES_CATEGORIES = ["软件工具", "游戏", "素材模板", "学习资料", "影视音源", "源码脚本", "电子书", "其他"]


def _is_vip(u):
    if not u:
        return False
    if getattr(u, "is_admin", False):
        return True
    exp = getattr(u, "vip_expire", None)
    if not exp:
        return False
    try:
        return exp > datetime.utcnow()
    except Exception:
        return False


def _save_image(file, folder, sub, max_bytes):
    ext = (file.filename or "").rsplit(".", 1)[-1].lower() if "." in (file.filename or "") else ""
    if ext not in ALLOWED_IMG_EXT:
        raise HTTPException(status_code=400, detail="只支持 jpg/png/gif/webp/bmp 图片")
    data = file.file.read()
    if len(data) > max_bytes:
        raise HTTPException(status_code=400, detail="图片过大，最大 %d MB" % (max_bytes // 1024 // 1024))
    name = "%s_%s.%s" % (datetime.now().strftime("%Y%m%d%H%M%S"), uuid.uuid4().hex[:8], ext)
    with open(os.path.join(folder, name), "wb") as f:
        f.write(data)
    return "/uploads/%s/%s" % (sub, name)


def _load_shots(s):
    if not s:
        return []
    try:
        v = json.loads(s)
        return v if isinstance(v, list) else []
    except Exception:
        return []


def _res_out(r: ResourceItem, db: Session, current=None):
    up = r.uploader
    unlocked = False
    if current is not None:
        if r.uploader_id == current.id or _is_vip(current):
            unlocked = True
        else:
            unlocked = db.query(ResourceUnlock).filter(
                ResourceUnlock.resource_id == r.id,
                ResourceUnlock.user_id == current.id,
            ).first() is not None
    if not r.is_paid:
        unlocked = True
    return {
        "id": r.id,
        "name": r.name,
        "summary": r.summary,
        "description": r.description,
        "category": r.category,
        "avatar": r.avatar or (up.avatar if up else ""),
        "cover": r.cover,
        "screenshots": _load_shots(r.screenshots),
        "res_type": r.res_type,
        "download_url": r.download_url if unlocked else "",
        "file_size": r.file_size,
        "is_paid": bool(r.is_paid),
        "price": r.price,
        "status": r.status,
        "reject_reason": r.reject_reason,
        "download_count": r.download_count,
        "view_count": r.view_count,
        "tip_total": r.tip_total,
        "uploader_id": r.uploader_id,
        "uploader_name": (up.nickname or up.username) if up else "",
        "unlocked": unlocked,
        "created_at": r.created_at.isoformat() if r.created_at else None,
    }


# ========== 输入模型 ==========
class ResourceCreateIn(BaseModel):
    name: str = ""
    summary: str = ""
    description: str = ""
    category: str = ""
    cover: str = ""
    screenshots: List[str] = []
    res_type: str = "link"
    download_url: str = ""
    file_size: int = 0
    is_paid: bool = False
    price: int = 0


class TipIn(BaseModel):
    amount: int = 0


# ========== 上传 ==========
@router.post("/upload_cover", summary="上传资源封面")
def upload_cover(file: UploadFile = File(...), current: User = Depends(get_current_user)):
    return {"ok": True, "url": _save_image(file, RES_COVER_DIR, "res_covers", MAX_COVER_BYTES)}


@router.post("/upload_shot", summary="上传演示截图（一次一张）")
def upload_shot(file: UploadFile = File(...), current: User = Depends(get_current_user)):
    return {"ok": True, "url": _save_image(file, RES_SHOT_DIR, "res_shots", MAX_SHOT_BYTES)}


@router.post("/upload_file", summary="直传资源文件（大会员）")
def upload_file(file: UploadFile = File(...), db: Session = Depends(get_db),
                current: User = Depends(get_current_user)):
    if not _is_vip(current):
        raise HTTPException(status_code=403, detail="上传文件到服务器需要开通大会员")
    ext = (file.filename or "").rsplit(".", 1)[-1].lower() if "." in (file.filename or "") else ""
    if not ext:
        raise HTTPException(status_code=400, detail="文件缺少扩展名")
    data = file.file.read()
    if len(data) > MAX_FILE_BYTES:
        raise HTTPException(status_code=400, detail="文件过大，最大 200 MB")
    name = "%s_%s.%s" % (datetime.now().strftime("%Y%m%d%H%M%S"), uuid.uuid4().hex[:8], ext)
    with open(os.path.join(RES_FILE_DIR, name), "wb") as f:
        f.write(data)
    return {"ok": True, "url": "/uploads/res_files/%s" % name, "size": len(data)}


# ========== 分类 ==========
@router.get("/categories", summary="资源分类列表")
def categories():
    return {"items": RES_CATEGORIES}


# ========== 列表 / 搜索 ==========
@router.get("", summary="资源列表（支持搜索 / 分类 / 排序）")
def list_resources(
    q: str = Query("", description="关键词"),
    category: str = Query("", description="分类"),
    sort: str = Query("new", description="new/hot/download"),
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
    current: Optional[User] = Depends(get_current_user_optional),
):
    qs = db.query(ResourceItem).filter(ResourceItem.status == "approved")
    if q:
        kw = "%%%s%%" % q.strip()
        qs = qs.filter(or_(ResourceItem.name.ilike(kw), ResourceItem.summary.ilike(kw)))
    if category:
        qs = qs.filter(ResourceItem.category == category)
    if sort == "hot":
        qs = qs.order_by(desc(ResourceItem.view_count), desc(ResourceItem.id))
    elif sort == "download":
        qs = qs.order_by(desc(ResourceItem.download_count), desc(ResourceItem.id))
    else:
        qs = qs.order_by(desc(ResourceItem.created_at), desc(ResourceItem.id))
    total = qs.count()
    rows = qs.offset((page - 1) * size).limit(size).all()
    return {
        "total": total,
        "page": page,
        "has_more": page * size < total,
        "items": [_res_out(r, db, current) for r in rows],
    }


@router.get("/mine", summary="我上传的资源")
def my_resources(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = db.query(ResourceItem).filter(ResourceItem.uploader_id == current.id) \
        .order_by(desc(ResourceItem.id)).all()
    return {"items": [_res_out(r, db, current) for r in rows]}


@router.post("", summary="上传资源（进入审核）")
def create_resource(data: ResourceCreateIn, db: Session = Depends(get_db),
                    current: User = Depends(get_current_user)):
    name = (data.name or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="请填写资源名称")
    if len(name) < 2:
        raise HTTPException(status_code=400, detail="资源名称至少 2 个字")
    rtype = (data.res_type or "link").strip()
    if rtype not in ("direct", "link"):
        rtype = "link"
    if rtype == "link" and not (data.download_url or "").strip():
        raise HTTPException(status_code=400, detail="请填写下载链接")
    if rtype == "direct" and not (data.download_url or "").strip():
        raise HTTPException(status_code=400, detail="请先上传资源文件")
    is_paid = bool(data.is_paid)
    price = max(0, int(data.price or 0))
    if is_paid and price <= 0:
        raise HTTPException(status_code=400, detail="付费资源请设置价格")
    r = ResourceItem(
        name=name,
        summary=(data.summary or "").strip(),
        description=(data.description or "").strip(),
        category=(data.category or "").strip(),
        avatar=(current.avatar or ""),
        cover=(data.cover or "").strip(),
        screenshots=json.dumps(data.screenshots or [], ensure_ascii=False),
        res_type=rtype,
        download_url=(data.download_url or "").strip(),
        file_size=int(data.file_size or 0),
        is_paid=is_paid,
        price=price,
        status="pending",
        uploader_id=current.id,
    )
    db.add(r)
    db.commit()
    db.refresh(r)
    return _res_out(r, db, current)



# ============================================================
# RES_COMMENT_V1: 资源评论
# 结构对齐应用评论，但不带星级评分（资源更偏"文件/链接"，打分意义不大）
# 注意：/comments/{cid} 必须定义在 /{rid} 之前，避免路径参数冲突
# ============================================================

@router.get("/comments/{rid}", summary="资源评论列表")
def resource_comments(rid: int, limit: int = Query(200, le=500),
                      db: Session = Depends(get_db)):
    rows = (db.query(ResourceComment)
            .filter(ResourceComment.resource_id == rid)
            .order_by(desc(ResourceComment.created_at)).limit(limit).all())
    out = []
    for c in rows:
        u = db.query(User).filter(User.id == c.user_id).first()
        out.append({
            "id": c.id,
            "resource_id": c.resource_id,
            "user_id": c.user_id,
            "nickname": (u.nickname or u.username) if u else "",
            "avatar": (u.avatar or "") if u else "",
            "content": c.content or "",
            "created_at": c.created_at,
        })
    return out


@router.post("/{rid}/comments", summary="发表资源评论")
def add_resource_comment(rid: int, data: dict,
                         db: Session = Depends(get_db),
                         current: User = Depends(get_current_user)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    txt = (data.get("content") or "").strip()
    if not txt:
        raise HTTPException(status_code=400, detail="评论内容不能为空")
    c = ResourceComment(resource_id=rid, user_id=current.id, content=txt[:500])
    db.add(c)
    db.commit()
    db.refresh(c)
    return {
        "id": c.id,
        "resource_id": c.resource_id,
        "user_id": c.user_id,
        "nickname": current.nickname or current.username,
        "avatar": current.avatar or "",
        "content": c.content,
        "created_at": c.created_at,
    }


@router.delete("/comments/{cid}", summary="删除我的一条资源评论")
def delete_resource_comment(cid: int, db: Session = Depends(get_db),
                            current: User = Depends(get_current_user)):
    c = db.query(ResourceComment).filter(ResourceComment.id == cid).first()
    if not c:
        raise HTTPException(status_code=404, detail="评论不存在")
    if c.user_id != current.id and not getattr(current, "is_admin", False):
        raise HTTPException(status_code=403, detail="只能删除自己的评论")
    db.delete(c)
    db.commit()
    return {"ok": True}


@router.get("/{rid}", summary="资源详情")
def resource_detail(rid: int, db: Session = Depends(get_db),
                    current: Optional[User] = Depends(get_current_user_optional)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    if r.status != "approved" and (current is None or (r.uploader_id != current.id and not getattr(current, "is_admin", False))):
        raise HTTPException(status_code=403, detail="资源审核中")
    r.view_count = (r.view_count or 0) + 1
    db.commit()
    return _res_out(r, db, current)


@router.post("/{rid}/download", summary="记录下载 + 返回下载地址")
def download(rid: int, db: Session = Depends(get_db),
             current: Optional[User] = Depends(get_current_user_optional)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    if r.is_paid:
        if current is None:
            raise HTTPException(status_code=401, detail="请先登录")
        if r.uploader_id != current.id and not _is_vip(current):
            ok = db.query(ResourceUnlock).filter(
                ResourceUnlock.resource_id == rid,
                ResourceUnlock.user_id == current.id,
            ).first()
            if not ok:
                raise HTTPException(status_code=403, detail="请先购买该资源")
    r.download_count = (r.download_count or 0) + 1
    db.commit()
    return {"ok": True, "url": r.download_url}


@router.post("/{rid}/unlock", summary="购买付费资源")
def unlock(rid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    if not r.is_paid:
        return {"ok": True, "url": r.download_url, "msg": "免费资源"}
    if r.uploader_id == current.id:
        return {"ok": True, "url": r.download_url, "msg": "自己的资源"}
    exists = db.query(ResourceUnlock).filter(
        ResourceUnlock.resource_id == rid,
        ResourceUnlock.user_id == current.id,
    ).first()
    if exists:
        return {"ok": True, "url": r.download_url, "msg": "已购买"}
    price = int(r.price or 0)
    bal = int(getattr(current, "coins", 0) or 0)
    if bal < price:
        raise HTTPException(status_code=400, detail="余额不足，需要 %d 金币" % price)
    current.coins = bal - price
    db.add(ResourceUnlock(resource_id=rid, user_id=current.id, price=price))
    db.add(BalanceLog(user_id=current.id, amount=-price, reason="购买资源：%s" % r.name))
    up = r.uploader
    if up:
        up.coins = int(getattr(up, "coins", 0) or 0) + price
        db.add(BalanceLog(user_id=up.id, amount=price, reason="资源售出：%s" % r.name))
    db.commit()
    return {"ok": True, "url": r.download_url, "msg": "购买成功"}


@router.post("/{rid}/tip", summary="打赏资源")
def tip(rid: int, data: TipIn, db: Session = Depends(get_db),
        current: User = Depends(get_current_user)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    amount = int(data.amount or 0)
    if amount <= 0:
        raise HTTPException(status_code=400, detail="打赏金额不正确")
    if r.uploader_id == current.id:
        raise HTTPException(status_code=400, detail="不能打赏自己的资源")
    bal = int(getattr(current, "coins", 0) or 0)
    if bal < amount:
        raise HTTPException(status_code=400, detail="余额不足")
    current.coins = bal - amount
    up = r.uploader
    if up:
        up.coins = int(getattr(up, "coins", 0) or 0) + amount
    r.tip_total = int(r.tip_total or 0) + amount
    db.add(ResourceTip(resource_id=rid, from_user_id=current.id,
                       to_user_id=r.uploader_id, amount=amount))
    db.add(BalanceLog(user_id=current.id, amount=-amount, reason="打赏资源：%s" % r.name))
    if up:
        db.add(BalanceLog(user_id=up.id, amount=amount, reason="收到打赏：%s" % r.name))
    db.commit()
    return {"ok": True, "tip_total": r.tip_total}


@router.delete("/{rid}", summary="删除自己的资源")
def delete_resource(rid: int, db: Session = Depends(get_db),
                    current: User = Depends(get_current_user)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    if r.uploader_id != current.id and not getattr(current, "is_admin", False):
        raise HTTPException(status_code=403, detail="无权删除")
    db.delete(r)
    db.commit()
    return {"ok": True}
