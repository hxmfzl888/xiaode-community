# -*- coding: utf-8 -*-
"""小德社区 v9.0 - 应用商店：上传 / 列表 / 搜索 / 详情 / 审核 / 评论 / 下载"""
import os
import json
import uuid
from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File
from sqlalchemy import or_, desc
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User, AppItem, AppComment, AppPurchase, AppTip, BalanceLog
from ..deps import get_current_user, get_current_admin, get_current_user_optional
from ..schemas import AppCreateIn, AppOut, AppCommentIn, AppCommentOut, AppTipIn, AppTipSummary, AppPurchaseOut

router = APIRouter(prefix="/api/apps", tags=["应用商店"])

BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")
APP_ICON_DIR = os.path.join(UPLOAD_DIR, "app_icons")
APP_SHOT_DIR = os.path.join(UPLOAD_DIR, "app_shots")
APP_FILE_DIR = os.path.join(UPLOAD_DIR, "app_files")
for _d in (APP_ICON_DIR, APP_SHOT_DIR, APP_FILE_DIR):
    os.makedirs(_d, exist_ok=True)

ALLOWED_IMG_EXT = {"jpg", "jpeg", "png", "gif", "webp", "bmp"}
MAX_ICON_BYTES = 5 * 1024 * 1024
MAX_SHOT_BYTES = 10 * 1024 * 1024
MAX_APK_BYTES = 500 * 1024 * 1024


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



def _parse_apk_info(path):
    """从 APK 里轻量解析 包名/版本名"""
    import zipfile, re
    info = {"package_name": "", "version": ""}
    try:
        with zipfile.ZipFile(path) as z:
            data = z.read("AndroidManifest.xml")
    except Exception:
        return info
    try:
        txt = data.decode("utf-16-le", errors="ignore")
        m = re.search(r"\b([a-zA-Z][a-zA-Z0-9_]+(?:\.[a-zA-Z][a-zA-Z0-9_]+){2,})", txt)
        if m:
            info["package_name"] = m.group(1)
        vm = re.findall(r"\b\d+\.\d+\.\d+(?:\.\d+)?\b", txt)
        if vm:
            info["version"] = vm[0]
    except Exception:
        pass
    return info

def _tip_total(db, app_id):
    try:
        from sqlalchemy import func
        v = db.query(func.coalesce(func.sum(AppTip.amount), 0)).filter(AppTip.app_id == app_id).scalar()
        return int(v or 0)
    except Exception:
        return 0


def _app_out(a, db):
    up = db.query(User).filter(User.id == a.uploader_id).first()
    return {
        "id": a.id, "name": a.name or "", "package_name": a.package_name or "",
        "version": a.version or "", "summary": a.summary or "", "description": a.description or "",
        "icon": a.icon or "", "screenshots": _load_shots(a.screenshots),
        "category": a.category or "", "upload_type": a.upload_type or "external",
        "apk_url": a.apk_url or "", "apk_size": a.apk_size or 0, "ext_link": a.ext_link or "",
        "status": a.status or "pending", "reject_reason": a.reject_reason or "",
        "download_count": a.download_count or 0, "view_count": a.view_count or 0,
        "uploader_id": a.uploader_id,
        "uploader_name": (up.nickname or up.username) if up else "",
        "is_paid": bool(getattr(a, "is_paid", False)),
        "price": int(getattr(a, "price", 0) or 0),
        "tip_total": _tip_total(db, a.id),
        "created_at": a.created_at,
    }


@router.get("", response_model=List[AppOut], summary="应用列表（仅审核通过，可搜索）")
def list_apps(q: str = Query("", max_length=60), category: str = Query("", max_length=40),
              limit: int = Query(30, ge=1, le=100), offset: int = Query(0, ge=0),
              db: Session = Depends(get_db),
              current: Optional[User] = Depends(get_current_user_optional)):
    # v9.7: 自己上传的应用不论审核状态都可见（别人只看 approved）
    if current:
        query = db.query(AppItem).filter(or_(AppItem.status == "approved", AppItem.uploader_id == current.id))
    else:
        query = db.query(AppItem).filter(AppItem.status == "approved")
    if q.strip():
        kw = "%%%s%%" % q.strip()
        query = query.filter(or_(AppItem.name.like(kw), AppItem.summary.like(kw),
                                 AppItem.description.like(kw), AppItem.package_name.like(kw)))
    if category.strip():
        query = query.filter(AppItem.category == category.strip())
    rows = query.order_by(desc(AppItem.created_at)).offset(offset).limit(limit).all()
    return [_app_out(a, db) for a in rows]


@router.get("/mine", response_model=List[AppOut], summary="我上传的应用（含待审核）")
def my_apps(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = (db.query(AppItem).filter(AppItem.uploader_id == current.id)
            .order_by(desc(AppItem.created_at)).all())
    return [_app_out(a, db) for a in rows]


@router.get("/pending", response_model=List[AppOut], summary="待审核应用（管理员）")
def pending_apps(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    rows = (db.query(AppItem).filter(AppItem.status == "pending")
            .order_by(AppItem.created_at.asc()).all())
    return [_app_out(a, db) for a in rows]


@router.get("/{app_id}", response_model=AppOut, summary="应用详情")
def app_detail(app_id: int, db: Session = Depends(get_db),
               current=Depends(get_current_user_optional)):
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="应用不存在")
    if a.status != "approved":
        if not current or (current.id != a.uploader_id and not getattr(current, "is_admin", False)):
            raise HTTPException(status_code=404, detail="应用不存在或未过审")
    a.view_count = (a.view_count or 0) + 1
    db.commit()
    return _app_out(a, db)


@router.post("", response_model=AppOut, summary="上传应用（待审核）")
def create_app(data: AppCreateIn, db: Session = Depends(get_db),
               current: User = Depends(get_current_user)):
    name = (data.name or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="请填写应用名称")
    utype = (data.upload_type or "external").strip()
    if utype not in ("direct", "external"):
        utype = "external"
    if utype == "direct":
        if not _is_vip(current):
            raise HTTPException(status_code=403, detail="上传安装包到服务器需要开通大会员")
        if not (data.apk_url or "").strip():
            raise HTTPException(status_code=400, detail="请先上传安装包")
    else:
        if not (data.ext_link or "").strip():
            raise HTTPException(status_code=400, detail="请填写外链下载地址")
    a = AppItem(
        name=name, package_name=(data.package_name or "").strip(),
        version=(data.version or "").strip(), summary=(data.summary or "").strip(),
        description=(data.description or "").strip(), icon=(data.icon or "").strip(),
        screenshots=json.dumps(data.screenshots or [], ensure_ascii=False),
        category=(data.category or "").strip(), upload_type=utype,
        apk_url=(data.apk_url or "").strip(), apk_size=int(data.apk_size or 0),
        ext_link=(data.ext_link or "").strip(), status="pending", uploader_id=current.id,
        is_paid=bool(data.is_paid), price=max(0, int(data.price or 0)),
    )
    db.add(a)
    db.commit()
    db.refresh(a)
    return _app_out(a, db)


@router.post("/upload_icon", summary="上传应用图标")
def upload_icon(file: UploadFile = File(...), current: User = Depends(get_current_user)):
    return {"ok": True, "url": _save_image(file, APP_ICON_DIR, "app_icons", MAX_ICON_BYTES)}


@router.post("/upload_shot", summary="上传应用截图（一次一张）")
def upload_shot(file: UploadFile = File(...), current: User = Depends(get_current_user)):
    return {"ok": True, "url": _save_image(file, APP_SHOT_DIR, "app_shots", MAX_SHOT_BYTES)}


@router.post("/upload_apk", summary="直传安装包到服务器（大会员）")
def upload_apk(file: UploadFile = File(...), db: Session = Depends(get_db),
               current: User = Depends(get_current_user)):
    if not _is_vip(current):
        raise HTTPException(status_code=403, detail="上传安装包到服务器需要开通大会员")
    fname = file.filename or "app.apk"
    ext = fname.rsplit(".", 1)[-1].lower() if "." in fname else "apk"
    if ext not in ("apk", "aab", "zip", "xapk"):
        raise HTTPException(status_code=400, detail="只支持 apk/aab/zip/xapk 文件")
    data = file.file.read()
    if len(data) > MAX_APK_BYTES:
        raise HTTPException(status_code=400, detail="安装包过大，最大 500MB")
    name = "%s_%s.%s" % (datetime.now().strftime("%Y%m%d%H%M%S"), uuid.uuid4().hex[:8], ext)
    with open(os.path.join(APP_FILE_DIR, name), "wb") as f:
        f.write(data)
    info = _parse_apk_info(os.path.join(APP_FILE_DIR, name))
    return {"ok": True, "url": "/uploads/app_files/%s" % name, "size": len(data),
            "package_name": info["package_name"], "version": info["version"]}


@router.post("/{app_id}/review", summary="审核应用：通过/驳回（管理员）")
def review_app(app_id: int, approve: bool = Query(True), reason: str = Query("", max_length=200),
               db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="应用不存在")
    a.status = "approved" if approve else "rejected"
    a.reject_reason = "" if approve else (reason or "不符合规范")
    db.commit()
    return {"ok": True, "status": a.status}


@router.post("/{app_id}/download", summary="记录一次下载")
def record_download(app_id: int, db: Session = Depends(get_db)):
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a or a.status != "approved":
        raise HTTPException(status_code=404, detail="应用不存在")
    a.download_count = (a.download_count or 0) + 1
    db.commit()
    return {"ok": True, "download_count": a.download_count,
            "url": a.apk_url if a.upload_type == "direct" else a.ext_link}


@router.get("/{app_id}/comments", response_model=List[AppCommentOut], summary="应用评论列表")
def app_comments(app_id: int, db: Session = Depends(get_db)):
    rows = (db.query(AppComment).filter(AppComment.app_id == app_id)
            .order_by(desc(AppComment.created_at)).limit(200).all())
    out = []
    for c in rows:
        u = db.query(User).filter(User.id == c.user_id).first()
        out.append({"id": c.id, "app_id": c.app_id, "user_id": c.user_id,
                    "nickname": (u.nickname or u.username) if u else "",
                    "avatar": u.avatar if u else "", "content": c.content or "",
                    "rating": c.rating or 5, "created_at": c.created_at})
    return out


@router.post("/{app_id}/comments", response_model=AppCommentOut, summary="发表应用评论")
def add_app_comment(app_id: int, data: AppCommentIn, db: Session = Depends(get_db),
                    current: User = Depends(get_current_user)):
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="应用不存在")
    txt = (data.content or "").strip()
    if not txt:
        raise HTTPException(status_code=400, detail="评论内容不能为空")
    c = AppComment(app_id=app_id, user_id=current.id, content=txt[:500],
                   rating=max(1, min(5, int(data.rating or 5))))
    db.add(c)
    db.commit()
    db.refresh(c)
    return {"id": c.id, "app_id": c.app_id, "user_id": c.user_id,
            "nickname": current.nickname or current.username, "avatar": current.avatar or "",
            "content": c.content, "rating": c.rating, "created_at": c.created_at}


@router.delete("/comments/{cid}", summary="删除我的一条应用评论")
def delete_app_comment(cid: int, db: Session = Depends(get_db),
                       current: User = Depends(get_current_user)):
    c = db.query(AppComment).filter(AppComment.id == cid).first()
    if not c:
        raise HTTPException(status_code=404, detail="评论不存在")
    if c.user_id != current.id and not getattr(current, "is_admin", False):
        raise HTTPException(status_code=403, detail="只能删除自己的评论")
    db.delete(c)
    db.commit()
    return {"ok": True}




@router.post("/{app_id}/purchase", response_model=AppPurchaseOut)
def purchase_app(app_id: int, db: Session = Depends(get_db),
                current: User = Depends(get_current_user)):
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="app not found")
    nm = a.name or ""
    bal0 = int(getattr(current, "balance", 0) or 0)
    if not bool(getattr(a, "is_paid", False)) or int(getattr(a, "price", 0) or 0) <= 0:
        return {"ok": True, "balance": bal0, "message": "free"}
    price = int(a.price or 0)
    if current.id == a.uploader_id:
        return {"ok": True, "balance": bal0, "message": "owner"}
    if bal0 < price:
        raise HTTPException(status_code=400, detail="insufficient balance")
    current.balance = bal0 - price
    db.add(BalanceLog(user_id=current.id, delta=-price, kind="app_buy_out", detail="buy: " + nm, balance_after=current.balance))
    seller = db.query(User).filter(User.id == a.uploader_id).first()
    if seller:
        seller.balance = int(getattr(seller, "balance", 0) or 0) + price
        db.add(BalanceLog(user_id=seller.id, delta=price, kind="app_buy_in", detail="sold: " + nm, balance_after=seller.balance))
    db.add(AppPurchase(app_id=a.id, buyer_id=current.id, seller_id=a.uploader_id, price=price))
    db.commit()
    return {"ok": True, "balance": current.balance, "message": "paid"}




@router.post("/{app_id}/tip")
def tip_app(app_id: int, data: AppTipIn, db: Session = Depends(get_db),
            current: User = Depends(get_current_user)):
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="app not found")
    nm = a.name or ""
    amount = int(data.amount or 0)
    if amount <= 0:
        raise HTTPException(status_code=400, detail="invalid amount")
    bal0 = int(getattr(current, "balance", 0) or 0)
    if bal0 < amount:
        raise HTTPException(status_code=400, detail="insufficient balance")
    current.balance = bal0 - amount
    db.add(BalanceLog(user_id=current.id, delta=-amount, kind="app_tip_out", detail="tip: " + nm, balance_after=current.balance))
    seller = db.query(User).filter(User.id == a.uploader_id).first()
    if seller:
        seller.balance = int(getattr(seller, "balance", 0) or 0) + amount
        db.add(BalanceLog(user_id=seller.id, delta=amount, kind="app_tip_in", detail="tip_recv: " + nm, balance_after=seller.balance))
    db.add(AppTip(app_id=a.id, from_user_id=current.id, to_user_id=a.uploader_id, amount=amount))
    db.commit()
    return {"ok": True, "balance": current.balance, "total": _tip_total(db, a.id)}




@router.get("/{app_id}/tip_summary", response_model=AppTipSummary)
def app_tip_summary(app_id: int, db: Session = Depends(get_db)):
    from sqlalchemy import func
    total = db.query(func.coalesce(func.sum(AppTip.amount), 0)).filter(AppTip.app_id == app_id).scalar() or 0
    count = db.query(func.count(AppTip.id)).filter(AppTip.app_id == app_id).scalar() or 0
    rows = (db.query(AppTip.from_user_id, func.sum(AppTip.amount).label("amt"))
            .filter(AppTip.app_id == app_id).group_by(AppTip.from_user_id)
            .order_by(func.sum(AppTip.amount).desc()).limit(3).all())
    top = []
    for uid, amt in rows:
        u = db.query(User).filter(User.id == uid).first()
        if u:
            top.append({"user_id": uid, "nickname": u.nickname or u.username, "amount": int(amt or 0)})
    return {"app_id": app_id, "total": int(total), "count": int(count), "top": top}

