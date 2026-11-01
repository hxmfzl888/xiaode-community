# -*- coding: utf-8 -*-
"""小德日历 API - 后台管理路由（仅管理员）：用户管理 / 动态审核 / 统计 / 公告管理"""
from typing import List, Optional
from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from sqlalchemy import or_, func, text

from ..database import get_db
from ..models import User, Moment, MomentLike, Diary, Comment, CommentLike, Announcement, ChatMessage, Friendship, Reviewer, Report, Notification, AppItem, Ad, AppConfig, Channel, Tool, LoginLog, ReviewerAgreement, ResourceItem, ResourceTip, ResourceUnlock
from ..auth import hash_password
from ..schemas import (
    UserOut, MomentOut, AnnouncementIn, AnnouncementOut, ReviewIn
)
from ..deps import get_current_admin

router = APIRouter(prefix="/api/admin", tags=["后台管理"])


# ---------- 概览统计 ----------
@router.get("/stats", summary="后台概览统计")
def stats(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    return {
        "users": db.query(User).count(),
        "moments_total": db.query(Moment).count(),
        "moments_pending": db.query(Moment).filter(Moment.status == "pending").count(),
        "moments_approved": db.query(Moment).filter(Moment.status == "approved").count(),
        "moments_rejected": db.query(Moment).filter(Moment.status == "rejected").count(),
        "comments": db.query(Comment).count(),
        "diaries": db.query(Diary).count(),
        "announcements": db.query(Announcement).count(),
    }



# ---------- 增强概览统计（新） ----------
@router.get("/stats/full", summary="后台概览统计（增强）")
def stats_full(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from datetime import datetime, timedelta
    now = datetime.utcnow()
    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    week_ago = now - timedelta(days=7)
    return {
        "users_total": db.query(User).count(),
        "users_today": db.query(User).filter(User.created_at >= today).count(),
        "users_week": db.query(User).filter(User.created_at >= week_ago).count(),
        "admins": db.query(User).filter(User.is_admin == True).count(),  # noqa: E712
        "vip_active": db.query(User).filter(User.vip_expire != None, User.vip_expire > now).count(),  # noqa: E711
        "balance_sum": int(db.query(AppConfig).count() and 0 or (db.query(User).with_entities(User.balance).all() and sum([r[0] or 0 for r in db.query(User).with_entities(User.balance).all()]) or 0)),
        "moments_total": db.query(Moment).count(),
        "moments_pending": db.query(Moment).filter(Moment.status == "pending").count(),
        "moments_today": db.query(Moment).filter(Moment.created_at >= today).count(),
        "comments_total": db.query(Comment).count(),
        "diaries_total": db.query(Diary).count(),
        "apps_total": db.query(AppItem).count(),
        "apps_pending": db.query(AppItem).filter(AppItem.status == "pending").count(),
        "reports_pending": db.query(Report).filter(Report.status == "pending").count(),
        "reviewers_total": db.query(Reviewer).count(),
        "reviewers_active": db.query(Reviewer).filter(Reviewer.active == True).count(),  # noqa: E712
        "announcements": db.query(Announcement).count(),
        "ads_total": db.query(Ad).count(),
        "ads_active": db.query(Ad).filter(Ad.active == True).count(),  # noqa: E712
    }


# ---------- 用户管理 ----------
@router.get("/users", response_model=List[UserOut], summary="用户列表")
def list_users(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    keyword: Optional[str] = Query(None, description="按用户名/昵称搜索"),
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    q = db.query(User)
    if keyword:
        like = "%%%s%%" % keyword
        q = q.filter((User.username.like(like)) | (User.nickname.like(like)))
    return q.order_by(User.id.desc()).offset(offset).limit(limit).all()


@router.post("/users/{user_id}/reset-password", summary="重置用户密码")
def reset_password(
    user_id: int,
    new_password: str = Query(..., min_length=6, description="新密码"),
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    from ..auth import hash_password
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户不存在")
    u.password_hash = hash_password(new_password)
    db.commit()
    return {"ok": True, "message": "密码已重置"}


@router.post("/users/{user_id}/toggle-admin", response_model=UserOut, summary="设置/取消管理员")
def toggle_admin(
    user_id: int,
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户不存在")
    u.is_admin = not bool(u.is_admin)
    db.commit()
    db.refresh(u)
    return u


@router.delete("/users/{user_id}", summary="删除用户（连带其动态/日记/评论）")
def delete_user(
    user_id: int,
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户不存在")
    if u.id == admin.id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="不能删除自己")
    db.delete(u)
    db.commit()
    return {"ok": True}


# ---------- 动态审核 ----------
@router.get("/moments", response_model=List[MomentOut], summary="动态列表（可按状态筛选，供审核）")
def list_all_moments(
    status_filter: Optional[str] = Query(None, alias="status", description="pending/approved/rejected"),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    q = db.query(Moment)
    if status_filter:
        q = q.filter(Moment.status == status_filter)
    rows = q.order_by(Moment.created_at.desc()).offset(offset).limit(limit).all()
    out = []
    for m in rows:
        out.append(MomentOut(
            id=m.id,
            title=m.title or "",
            content=m.content,
            images=[x for x in (m.images or "").split(",") if x],
            status=m.status,
            created_at=m.created_at,
            user_id=m.user_id,
            nickname=m.author.nickname if m.author else "",
            username=m.author.username if m.author else "",
            avatar=m.author.avatar if m.author else "",
            comment_count=len(m.comments) if m.comments is not None else 0,
        ))
    return out


@router.post("/moments/{moment_id}/review", response_model=MomentOut, summary="审核动态：通过/驳回")
def review_moment(
    moment_id: int,
    data: ReviewIn,
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    if data.status not in ("approved", "rejected"):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="status 只能是 approved 或 rejected")
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="动态不存在")
    m.status = data.status
    db.commit()
    db.refresh(m)
    return MomentOut(
        id=m.id,
        title=m.title or "",
        content=m.content,
        images=[x for x in (m.images or "").split(",") if x],
        status=m.status,
        created_at=m.created_at,
        user_id=m.user_id,
        nickname=m.author.nickname if m.author else "",
        username=m.author.username if m.author else "",
        avatar=m.author.avatar if m.author else "",
        comment_count=len(m.comments) if m.comments is not None else 0,
    )


# ---------- 公告管理（后台内） ----------
@router.post("/announcements", response_model=AnnouncementOut, summary="发布公告")
def create_announcement(
    data: AnnouncementIn,
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    a = Announcement(title=data.title, content=data.content)
    db.add(a)
    db.commit()
    db.refresh(a)
    return a

# ---------- 聊天管理 ----------
@router.get("/chats", summary="查看所有聊天消息（分页）")
def all_chats(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    keyword: str = Query("", max_length=60),
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    """后台查看全部聊天消息，可按内容关键词过滤。"""
    q = db.query(ChatMessage)
    if keyword.strip():
        q = q.filter(ChatMessage.content.like("%" + keyword.strip() + "%"))
    total = q.count()
    rows = q.order_by(ChatMessage.id.desc()).offset(offset).limit(limit).all()
    ids = list({m.sender_id for m in rows} | {m.receiver_id for m in rows})
    users = {u.id: u for u in db.query(User).filter(User.id.in_(ids)).all()} if ids else {}
    items = []
    for m in rows:
        su = users.get(m.sender_id)
        ru = users.get(m.receiver_id)
        items.append({
            "id": m.id,
            "kind": m.kind,
            "content": m.content,
            "is_read": bool(m.is_read),
            "created_at": m.created_at.isoformat() if m.created_at else None,
            "sender_id": m.sender_id,
            "sender_name": (su.nickname or su.username) if su else str(m.sender_id),
            "receiver_id": m.receiver_id,
            "receiver_name": (ru.nickname or ru.username) if ru else str(m.receiver_id),
        })
    return {"total": total, "items": items}


@router.delete("/chats", summary="清空所有聊天消息（不可恢复）")
def clear_chats(
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    n = db.query(ChatMessage).delete()
    db.commit()
    return {"ok": True, "deleted": n}


@router.get("/chats/stats", summary="聊天概览统计")
def chat_stats(
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    total = db.query(ChatMessage).count()
    friends = db.query(Friendship).filter(Friendship.status == "accepted").count()
    pending = db.query(Friendship).filter(Friendship.status == "pending").count()
    return {"messages": total, "friends": friends, "pending_requests": pending}


# ---------- 会员 / 余额管理 ----------
from datetime import datetime as _dt, timedelta as _td


# ==================== 会员套餐管理（VIP_PLAN_ADMIN） ====================
def _iso(v):
    """安全转 ISO 字符串：兼容 datetime / str / None"""
    if v is None:
        return ""
    if isinstance(v, str):
        return v
    try:
        return v.isoformat()
    except AttributeError:
        return str(v)

@router.get("/vip/plans", summary="套餐列表（后台，含停用的）")
def admin_vip_plans(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    rows = db.execute(text(
        "SELECT id,key,name,days,gift_days,price,badge,intro,sort,enabled,is_default,"
        "created_at,updated_at FROM vip_plans ORDER BY sort,id"
    )).fetchall()
    items = []
    for r in rows:
        sold = db.execute(text("SELECT COUNT(*) FROM vip_orders WHERE plan_key=:k"), {"k": r[1]}).scalar() or 0
        income = db.execute(text("SELECT COALESCE(SUM(price),0) FROM vip_orders WHERE plan_key=:k"), {"k": r[1]}).scalar() or 0
        items.append({
            "id": r[0], "key": r[1], "name": r[2],
            "days": r[3], "gift_days": r[4], "total_days": int(r[3]) + int(r[4] or 0),
            "price": float(r[5]), "badge": r[6] or "", "intro": r[7] or "",
            "sort": r[8], "enabled": bool(r[9]), "is_default": bool(r[10]),
            "created_at": _iso(r[11]),
            "updated_at": _iso(r[12]),
            "sold": int(sold), "income": float(income),
        })
    return {"items": items, "total": len(items)}


@router.post("/vip/plans", summary="新增套餐")
def admin_vip_plan_add(data: dict, db: Session = Depends(get_db),
                       admin: User = Depends(get_current_admin)):
    name = str(data.get("name", "")).strip()
    if not name:
        raise HTTPException(status_code=400, detail="套餐名称不能为空")
    try:
        days = int(data.get("days", 0))
        gift = int(data.get("gift_days", 0) or 0)
        price = float(data.get("price", 0))
    except (TypeError, ValueError):
        raise HTTPException(status_code=400, detail="天数或价格格式错误")
    if days <= 0:
        raise HTTPException(status_code=400, detail="天数必须大于 0")
    if price < 0:
        raise HTTPException(status_code=400, detail="价格不能为负")

    # key 自动生成：p + 时间戳后缀，避免冲突
    key = str(data.get("key", "")).strip()
    if not key:
        key = "p%d" % int(datetime.now().timestamp())
    ex = db.execute(text("SELECT id FROM vip_plans WHERE key=:k"), {"k": key}).fetchone()
    if ex:
        raise HTTPException(status_code=400, detail="套餐标识 %s 已存在" % key)

    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    max_sort = db.execute(text("SELECT COALESCE(MAX(sort),0) FROM vip_plans")).scalar() or 0
    db.execute(text(
        "INSERT INTO vip_plans(key,name,days,gift_days,price,badge,intro,sort,enabled,is_default,created_at,updated_at) "
        "VALUES (:k,:n,:d,:g,:p,:b,:i,:s,:e,0,:ca,:ca)"
    ), {
        "k": key, "n": name, "d": days, "g": gift, "p": price,
        "b": str(data.get("badge", ""))[:32], "i": str(data.get("intro", ""))[:128],
        "s": int(data.get("sort", max_sort + 1)),
        "e": 1 if data.get("enabled", True) else 0, "ca": now,
    })
    db.commit()
    return {"ok": True, "key": key, "message": "套餐「%s」已创建" % name}


@router.put("/vip/plans/{plan_id}", summary="修改套餐")
def admin_vip_plan_edit(plan_id: int, data: dict, db: Session = Depends(get_db),
                        admin: User = Depends(get_current_admin)):
    row = db.execute(text("SELECT id,key FROM vip_plans WHERE id=:i"), {"i": plan_id}).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="套餐不存在")

    sets, params = [], {"i": plan_id}
    if "name" in data:
        n = str(data["name"]).strip()
        if not n:
            raise HTTPException(status_code=400, detail="套餐名称不能为空")
        sets.append("name=:n"); params["n"] = n
    if "days" in data:
        try:
            d = int(data["days"])
        except (TypeError, ValueError):
            raise HTTPException(status_code=400, detail="天数格式错误")
        if d <= 0:
            raise HTTPException(status_code=400, detail="天数必须大于 0")
        sets.append("days=:d"); params["d"] = d
    if "gift_days" in data:
        sets.append("gift_days=:g"); params["g"] = max(0, int(data["gift_days"] or 0))
    if "price" in data:
        try:
            p = float(data["price"])
        except (TypeError, ValueError):
            raise HTTPException(status_code=400, detail="价格格式错误")
        if p < 0:
            raise HTTPException(status_code=400, detail="价格不能为负")
        sets.append("price=:p"); params["p"] = p
    if "badge" in data:
        sets.append("badge=:b"); params["b"] = str(data["badge"])[:32]
    if "intro" in data:
        sets.append("intro=:i2"); params["i2"] = str(data["intro"])[:128]
    if "sort" in data:
        sets.append("sort=:s"); params["s"] = int(data["sort"])
    if "enabled" in data:
        sets.append("enabled=:e"); params["e"] = 1 if data["enabled"] else 0

    if not sets:
        raise HTTPException(status_code=400, detail="没有需要修改的字段")

    sets.append("updated_at=:ua")
    params["ua"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    db.execute(text("UPDATE vip_plans SET %s WHERE id=:i" % ", ".join(sets)), params)
    db.commit()
    return {"ok": True, "message": "已保存"}


@router.delete("/vip/plans/{plan_id}", summary="删除套餐")
def admin_vip_plan_del(plan_id: int, db: Session = Depends(get_db),
                       admin: User = Depends(get_current_admin)):
    row = db.execute(text("SELECT id,key,name FROM vip_plans WHERE id=:i"), {"i": plan_id}).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="套餐不存在")
    # 有订单的不能删，改为提示停用
    sold = db.execute(text("SELECT COUNT(*) FROM vip_orders WHERE plan_key=:k"), {"k": row[1]}).scalar() or 0
    if sold > 0:
        raise HTTPException(status_code=400,
                            detail="该套餐已有 %d 笔订单，不能删除。建议改为「停用」" % sold)
    db.execute(text("DELETE FROM vip_plans WHERE id=:i"), {"i": plan_id})
    db.commit()
    return {"ok": True, "message": "套餐「%s」已删除" % row[2]}


@router.post("/vip/plans/{plan_id}/toggle", summary="启用/停用套餐")
def admin_vip_plan_toggle(plan_id: int, db: Session = Depends(get_db),
                          admin: User = Depends(get_current_admin)):
    row = db.execute(text("SELECT id,enabled,name FROM vip_plans WHERE id=:i"), {"i": plan_id}).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="套餐不存在")
    newv = 0 if row[1] else 1
    db.execute(text("UPDATE vip_plans SET enabled=:e,updated_at=:u WHERE id=:i"), {
        "e": newv, "u": datetime.now().strftime("%Y-%m-%d %H:%M:%S"), "i": plan_id})
    db.commit()
    return {"ok": True, "enabled": bool(newv),
            "message": "套餐「%s」已%s" % (row[2], "启用" if newv else "停用")}


@router.get("/vip/orders", summary="会员订单列表")
def admin_vip_orders(page: int = 1, size: int = 50, keyword: str = "",
                     db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    page = max(1, page)
    size = min(200, max(1, size))
    where, params = "WHERE 1=1", {}
    if keyword.strip():
        where += " AND (username LIKE :kw OR plan_name LIKE :kw OR CAST(user_id AS TEXT)=:ex)"
        params["kw"] = "%" + keyword.strip() + "%"
        params["ex"] = keyword.strip()

    total = db.execute(text("SELECT COUNT(*) FROM vip_orders " + where), params).scalar() or 0
    rows = db.execute(text(
        "SELECT id,user_id,username,plan_key,plan_name,days,price,"
        "before_expire,after_expire,status,created_at FROM vip_orders "
        + where + " ORDER BY id DESC LIMIT :lim OFFSET :off"
    ), dict(params, lim=size, off=(page - 1) * size)).fetchall()

    items = [{
        "id": r[0], "user_id": r[1], "username": r[2],
        "plan_key": r[3], "plan_name": r[4], "days": r[5], "price": float(r[6]),
        "before_expire": _iso(r[7]),
        "after_expire": _iso(r[8]),
        "status": r[9], "created_at": _iso(r[10]),
    } for r in rows]

    income = db.execute(text("SELECT COALESCE(SUM(price),0) FROM vip_orders " + where), params).scalar() or 0
    return {"items": items, "total": int(total), "page": page, "size": size,
            "income": float(income), "pages": (int(total) + size - 1) // size}


@router.get("/vip/overview", summary="会员概览统计")
def admin_vip_overview(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    now = _dt.utcnow()
    month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    vip_total = db.execute(text(
        "SELECT COUNT(*) FROM users WHERE vip_expire IS NOT NULL AND vip_expire > :n"), {"n": now}).scalar() or 0
    new_this_month = db.execute(text(
        "SELECT COUNT(*) FROM vip_orders WHERE created_at >= :m"), {"m": month_start.strftime("%Y-%m-%d %H:%M:%S")}).scalar() or 0
    income_total = db.execute(text("SELECT COALESCE(SUM(price),0) FROM vip_orders")).scalar() or 0
    income_month = db.execute(text(
        "SELECT COALESCE(SUM(price),0) FROM vip_orders WHERE created_at >= :m"),
        {"m": month_start.strftime("%Y-%m-%d %H:%M:%S")}).scalar() or 0
    orders_total = db.execute(text("SELECT COUNT(*) FROM vip_orders")).scalar() or 0
    expired = db.execute(text(
        "SELECT COUNT(*) FROM users WHERE vip_expire IS NOT NULL AND vip_expire <= :n"), {"n": now}).scalar() or 0
    return {"vip_total": int(vip_total), "new_this_month": int(new_this_month),
            "income_total": float(income_total), "income_month": float(income_month),
            "orders_total": int(orders_total), "expired": int(expired)}

@router.get("/vip/{user_id}", summary="查看某用户会员/余额")
def vip_get(user_id: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    return {"id": u.id, "username": u.username, "nickname": u.nickname or "",
            "balance": u.balance or 0, "balance_cents": u.balance_cents or 0,
            "vip_expire": u.vip_expire.isoformat() if u.vip_expire else "",
            "is_vip": bool(u.vip_expire and u.vip_expire > _dt.utcnow())}


@router.post("/vip/{user_id}", summary="设置用户余额/开通会员(天)")
def vip_set(user_id: int, data: dict, db: Session = Depends(get_db),
            admin: User = Depends(get_current_admin)):
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    # 余额单位：前端传「元」（可带小数），内部权威存储为「分」
    def _to_cents(v):
        try:
            return int(round(float(v) * 100))
        except (TypeError, ValueError):
            raise HTTPException(status_code=400, detail="余额格式错误")

    if "balance" in data and data["balance"] is not None:
        cents = _to_cents(data["balance"])
        u.balance_cents = cents
        u.balance = round(cents / 100.0, 2)
    if "add_balance" in data and data["add_balance"] is not None:
        cents = (u.balance_cents or 0) + _to_cents(data["add_balance"])
        u.balance_cents = cents
        u.balance = round(cents / 100.0, 2)
    if "add_vip_days" in data:
        base = u.vip_expire if (u.vip_expire and u.vip_expire > _dt.utcnow()) else _dt.utcnow()
        u.vip_expire = base + _td(days=int(data["add_vip_days"]))
    if "clear_vip" in data and data["clear_vip"]:
        u.vip_expire = None
    db.commit()
    return {"ok": True, "balance": u.balance or 0, "balance_cents": u.balance_cents or 0,
            "vip_expire": u.vip_expire.isoformat() if u.vip_expire else ""}


# ==================== 审核员管理（仅主后台管理员） ====================
@router.get("/reviewers", summary="审核员列表")
def list_reviewers(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    rows = db.query(Reviewer).order_by(Reviewer.id.desc()).all()
    return [{"id": r.id, "username": r.username, "nickname": r.nickname or "",
             "active": bool(r.active), "created_at": r.created_at.isoformat() if r.created_at else "",
             "last_login": r.last_login.isoformat() if r.last_login else ""} for r in rows]


@router.post("/reviewers", summary="创建审核员账号")
def create_reviewer(data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    username = (data.get("username") or "").strip()
    password = (data.get("password") or "")
    nickname = (data.get("nickname") or "").strip()
    if not username or not password:
        raise HTTPException(status_code=400, detail="账号和密码必填")
    if len(password) < 6:
        raise HTTPException(status_code=400, detail="密码至少 6 位")
    if db.query(Reviewer).filter(Reviewer.username == username).first():
        raise HTTPException(status_code=400, detail="该账号已存在")
    rv = Reviewer(username=username, password_hash=hash_password(password),
                  nickname=nickname or username, active=True, created_by=admin.id)
    db.add(rv); db.commit(); db.refresh(rv)
    return {"ok": True, "id": rv.id, "username": rv.username}


@router.post("/reviewers/{rid}/toggle", summary="启用/停用审核员")
def toggle_reviewer(rid: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    rv = db.query(Reviewer).filter(Reviewer.id == rid).first()
    if not rv:
        raise HTTPException(status_code=404, detail="审核员不存在")
    rv.active = not rv.active
    db.commit()
    return {"ok": True, "id": rv.id, "active": bool(rv.active)}


@router.post("/reviewers/{rid}/reset-password", summary="重置审核员密码")
def reset_reviewer_pwd(rid: int, new_password: str = Query(...),
                       db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    rv = db.query(Reviewer).filter(Reviewer.id == rid).first()
    if not rv:
        raise HTTPException(status_code=404, detail="审核员不存在")
    if len(new_password) < 6:
        raise HTTPException(status_code=400, detail="密码至少 6 位")
    rv.password_hash = hash_password(new_password)
    db.commit()
    return {"ok": True}


@router.delete("/reviewers/{rid}", summary="删除审核员")
def delete_reviewer(rid: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    rv = db.query(Reviewer).filter(Reviewer.id == rid).first()
    if not rv:
        raise HTTPException(status_code=404, detail="审核员不存在")
    db.delete(rv); db.commit()
    return {"ok": True}


# ==================== 举报管理（主后台也可看） ====================
@router.get("/reports", summary="举报列表")
def admin_reports(st: str = Query("pending"), db: Session = Depends(get_db),
                  admin: User = Depends(get_current_admin)):
    q = db.query(Report)
    if st:
        q = q.filter(Report.status == st)
    rows = q.order_by(Report.created_at.desc()).limit(300).all()
    return [{"id": r.id, "target_type": r.target_type, "target_id": r.target_id,
             "reason": r.reason or "", "detail": r.detail or "", "status": r.status,
             "reporter_id": r.reporter_id, "created_at": r.created_at.isoformat() if r.created_at else ""}
            for r in rows]

# ==================== 举报处理 ====================
@router.post("/reports/{rid}/handle", summary="处理举报（两态：无违规 / 有违规）")
def handle_report(rid: int, verdict: str = Query("clean"), db: Session = Depends(get_db),
                  admin: User = Depends(get_current_admin)):
    """verdict: clean=无违规(保留) / violation=有违规(删除内容)"""
    r = db.query(Report).filter(Report.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="举报不存在")
    v = (verdict or "clean").strip().lower()
    v = "clean" if v in ("clean", "dismiss", "dismissed", "none", "无违规") else "violation"

    author_id = 0
    title = ""
    if r.target_type == "moment":
        m = db.query(Moment).filter(Moment.id == r.target_id).first()
        if m:
            author_id = m.user_id
            title = (getattr(m, "title", "") or (m.content or ""))[:20]
            if v == "violation":
                try:
                    db.query(MomentLike).filter(MomentLike.moment_id == m.id).delete(synchronize_session=False)
                except Exception:
                    pass
                try:
                    cids = [c.id for c in db.query(Comment).filter(Comment.moment_id == m.id).all()]
                    if cids:
                        db.query(CommentLike).filter(CommentLike.comment_id.in_(cids)).delete(synchronize_session=False)
                    db.query(Comment).filter(Comment.moment_id == m.id).delete(synchronize_session=False)
                except Exception:
                    pass
                db.delete(m)
    elif r.target_type == "app":
        a = db.query(AppItem).filter(AppItem.id == r.target_id).first()
        if a:
            author_id = a.uploader_id
            title = (a.name or "")[:20]
            if v == "violation":
                db.delete(a)

    r.status = "dismissed" if v == "clean" else "handled"
    r.handled_by = admin.id or 0

    # 通知举报人
    msg_r = ("你的举报经审核【无违规】，内容已保留，感谢反馈" if v == "clean"
             else "你的举报经审核【有违规】，内容已删除，感谢反馈")
    try:
        db.add(Notification(user_id=r.reporter_id, actor_id=r.reporter_id, type="report", content=msg_r))
    except Exception:
        pass
    # 通知作者（有违规）
    if v == "violation" and author_id:
        try:
            db.add(Notification(user_id=author_id, actor_id=author_id, type="report",
                                content="你的内容《" + title + "》因【有违规】已被删除"))
        except Exception:
            pass

    db.commit()
    return {"ok": True, "status": r.status, "verdict": v}


# ============ 密保（安全问题） ============
@router.get("/security/info", summary="读取当前管理员密保问题")
def sec_info(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    return {"question": getattr(admin, "sec_question", "") or "",
            "has_answer": bool(getattr(admin, "sec_answer_hash", "") or "")}


@router.post("/security/mibao", summary="设置/修改密保问题")
def sec_set(data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    q = (data.get("question") or "").strip()
    a = (data.get("answer") or "").strip()
    if not q or not a:
        raise HTTPException(status_code=400, detail="密保问题和答案不能为空")
    if len(a) < 2:
        raise HTTPException(status_code=400, detail="答案太短")
    admin.sec_question = q[:200]
    admin.sec_answer_hash = hash_password(a.lower())
    db.commit()
    return {"ok": True}


@router.post("/security/password", summary="修改当前管理员登录密码")
def sec_password(data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from ..auth import verify_password
    old = data.get("old_password") or ""
    new = data.get("new_password") or ""
    if not verify_password(old, admin.password_hash):
        raise HTTPException(status_code=400, detail="原密码错误")
    if len(new) < 6:
        raise HTTPException(status_code=400, detail="新密码至少 6 位")
    admin.password_hash = hash_password(new)
    db.commit()
    return {"ok": True}


# ============ 文件管理 ============
import os as _os
from fastapi.responses import FileResponse as _FR

_UPLOAD_ROOT = _os.path.join(_os.path.dirname(_os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))), "uploads")
_ALLOW_SUB = ["ads", "app_files", "app_icons", "app_shots", "avatars", "channels", "images", "videos", "vipbg"]


def _safe_dir(sub: str):
    if sub not in _ALLOW_SUB:
        raise HTTPException(status_code=400, detail="非法目录")
    d = _os.path.join(_UPLOAD_ROOT, sub)
    if not _os.path.isdir(d):
        raise HTTPException(status_code=404, detail="目录不存在")
    return d


@router.get("/files", summary="文件管理：列出文件")
def files_list(sub: str = Query(..., description="子目录名"), limit: int = Query(200, le=1000),
               db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    d = _safe_dir(sub)
    out = []
    try:
        names = sorted(_os.listdir(d), key=lambda n: _os.path.getmtime(_os.path.join(d, n)), reverse=True)
    except Exception:
        names = []
    for n in names[:limit]:
        fp = _os.path.join(d, n)
        if not _os.path.isfile(fp):
            continue
        st = _os.stat(fp)
        out.append({"name": n, "size": st.st_size, "mtime": int(st.st_mtime),
                    "url": "/uploads/" + sub + "/" + n})
    return {"sub": sub, "count": len(out), "files": out}


@router.get("/files/dirs", summary="文件管理：列出可管理目录")
def files_dirs(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    out = []
    for sub in _ALLOW_SUB:
        d = _os.path.join(_UPLOAD_ROOT, sub)
        if _os.path.isdir(d):
            try:
                cnt = len([n for n in _os.listdir(d) if _os.path.isfile(_os.path.join(d, n))])
                sz = sum(_os.path.getsize(_os.path.join(d, n)) for n in _os.listdir(d) if _os.path.isfile(_os.path.join(d, n)))
            except Exception:
                cnt, sz = 0, 0
            out.append({"sub": sub, "count": cnt, "size": sz})
    return out


@router.delete("/files", summary="文件管理：删除文件")
def files_delete(sub: str = Query(...), name: str = Query(...),
                 db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    d = _safe_dir(sub)
    name = _os.path.basename(name)
    fp = _os.path.join(d, name)
    if not _os.path.isfile(fp):
        raise HTTPException(status_code=404, detail="文件不存在")
    _os.remove(fp)
    return {"ok": True, "deleted": name}


# ==================== 🎟️ 卡密管理（后台） ====================
def _gen_codes(count, length, fmt, prefix, charset):
    """按模板生成卡密：fmt 空->纯随机 length 位；支持 {rand} 与 {segN}。"""
    import random, re as _re
    pools = {
        "num": "0123456789",
        "upper": "ABCDEFGHJKLMNPQRSTUVWXYZ23456789",
        "lower": "abcdefghjkmnpqrstuvwxyz23456789",
        "mix": "ABCDEFGHJKLMNPQRSTUVWXYZ23456789",
    }
    pool = pools.get(charset, pools["mix"])

    def rand(n):
        return "".join(random.choice(pool) for _ in range(n))

    out = []
    fmt = (fmt or "").strip()
    for _ in range(count):
        if fmt:
            c = fmt
            if "{rand}" in c:
                c = c.replace("{rand}", rand(length))
            c = _re.sub(r"\{seg(\d+)\}", lambda m: rand(int(m.group(1))), c)
            if "{rand}" not in fmt and not _re.search(r"\{seg\d+\}", fmt):
                c = fmt + rand(length)
        else:
            c = rand(length)
        if prefix:
            c = prefix + c
        out.append(c)
    return out


@router.get("/redeem", summary="卡密列表")
def redeem_list(
    status: Optional[int] = Query(None, description="0未用 1已用 2作废"),
    batch: Optional[str] = Query(None),
    keyword: Optional[str] = Query(None),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    from ..models import RedeemCode
    q = db.query(RedeemCode)
    if status is not None:
        q = q.filter(RedeemCode.status == status)
    if batch:
        q = q.filter(RedeemCode.batch == batch)
    if keyword:
        q = q.filter(RedeemCode.code.like("%%%s%%" % keyword))
    total = q.count()
    rows = q.order_by(RedeemCode.id.desc()).offset(offset).limit(limit).all()
    users = {u.id: (u.nickname or u.username) for u in db.query(User).all()}
    return {
        "total": total,
        "items": [{
            "id": c.id, "code": c.code, "kind": c.kind, "amount": c.amount,
            "batch": c.batch or "", "remark": c.remark or "", "status": c.status,
            "expire_at": c.expire_at.isoformat() if c.expire_at else "",
            "used_by": c.used_by or 0, "used_by_name": users.get(c.used_by, "") if c.used_by else "",
            "used_at": c.used_at.isoformat() if c.used_at else "",
            "created_at": c.created_at.isoformat() if c.created_at else "",
        } for c in rows],
    }


@router.post("/redeem/generate", summary="生成卡密")
def redeem_generate(data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from ..models import RedeemCode
    import random, string
    count = int(data.get("count", 1) or 1)
    count = max(1, min(count, 2000))
    kind = data.get("kind", "balance")
    if kind not in ("balance", "vip"):
        raise HTTPException(status_code=400, detail="kind 只能是 balance 或 vip")
    amount = int(data.get("amount", 0) or 0)
    if amount <= 0:
        raise HTTPException(status_code=400, detail="额度必须大于 0")
    length = int(data.get("length", 12) or 12)
    length = max(4, min(length, 40))
    fmt = str(data.get("format", "") or "").strip()
    prefix = str(data.get("prefix", "") or "")
    charset = str(data.get("charset", "mix") or "mix")
    batch = str(data.get("batch", "") or "")
    remark = str(data.get("remark", "") or "")
    expire_days = data.get("expire_days", None)
    expire_at = None
    if expire_days not in (None, "", 0, "0"):
        expire_at = _dt.utcnow() + _td(days=int(expire_days))

    codes = _gen_codes(count, length, fmt, prefix, charset)
    # 去重（库内 + 本批）
    made, seen = [], set()
    for c in codes:
        if c in seen:
            continue
        if db.query(RedeemCode).filter(RedeemCode.code == c).first():
            continue
        seen.add(c); made.append(c)
    objs = [RedeemCode(code=c, kind=kind, amount=amount, batch=batch, remark=remark,
                       expire_at=expire_at, status=0) for c in made]
    db.add_all(objs)
    db.commit()
    return {"ok": True, "created": len(objs), "codes": made,
            "kind": kind, "amount": amount,
            "expire_at": expire_at.isoformat() if expire_at else ""}


@router.post("/redeem/{cid}/disable", summary="作废单张卡密")
def redeem_disable(cid: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from ..models import RedeemCode
    c = db.query(RedeemCode).filter(RedeemCode.id == cid).first()
    if not c:
        raise HTTPException(status_code=404, detail="卡密不存在")
    if c.status == 1:
        raise HTTPException(status_code=400, detail="已使用的卡密不能作废")
    c.status = 2
    db.commit()
    return {"ok": True}


@router.delete("/redeem/{cid}", summary="删除单张卡密")
def redeem_delete(cid: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from ..models import RedeemCode
    c = db.query(RedeemCode).filter(RedeemCode.id == cid).first()
    if not c:
        raise HTTPException(status_code=404, detail="卡密不存在")
    db.delete(c); db.commit()
    return {"ok": True}


@router.post("/redeem/delete_batch", summary="批量删除卡密（按 ids 或按批次）")
def redeem_delete_batch(data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from ..models import RedeemCode
    ids = data.get("ids") or []
    batch = data.get("batch", "")
    q = db.query(RedeemCode)
    if ids:
        q = q.filter(RedeemCode.id.in_([int(i) for i in ids]))
    elif batch:
        q = q.filter(RedeemCode.batch == batch)
    else:
        raise HTTPException(status_code=400, detail="请提供 ids 或 batch")
    n = q.delete(synchronize_session=False)
    db.commit()
    return {"ok": True, "deleted": n}


@router.get("/redeem/batches", summary="卡密批次列表")
def redeem_batches(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from ..models import RedeemCode
    from sqlalchemy import func
    rows = db.query(RedeemCode.batch, func.count(RedeemCode.id)).group_by(RedeemCode.batch).all()
    return [{"batch": (b or "(未命名)"), "batch_key": b or "", "count": n} for b, n in rows]


# ==================== 📢 定向消息（后台 -> 指定账号） ====================
@router.get("/messages", summary="定向消息列表")
def dm_list(
    keyword: Optional[str] = Query(None),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    from ..models import DirectMessage
    q = db.query(DirectMessage)
    if keyword:
        q = q.filter((DirectMessage.title.like("%%%s%%" % keyword)) | (DirectMessage.content.like("%%%s%%" % keyword)))
    total = q.count()
    rows = q.order_by(DirectMessage.id.desc()).offset(offset).limit(limit).all()
    users = {u.id: (u.nickname or u.username) for u in db.query(User).all()}
    return {
        "total": total,
        "items": [{
            "id": m.id, "user_id": m.user_id, "to": users.get(m.user_id, "用户#%d" % m.user_id),
            "title": m.title, "content": m.content, "sender": m.sender,
            "is_read": bool(m.is_read), "created_at": m.created_at.isoformat() if m.created_at else "",
        } for m in rows],
    }


@router.post("/messages/send", summary="向指定账号发送自定义消息（标题+内容）")
def dm_send(data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from ..models import DirectMessage
    title = str(data.get("title", "") or "").strip()
    content = str(data.get("content", "") or "").strip()
    sender = str(data.get("sender", "") or "系统").strip() or "系统"
    if not content and not title:
        raise HTTPException(status_code=400, detail="标题和内容不能同时为空")
    # 支持按 username 或 user_id 定位
    target = None
    uid = data.get("user_id")
    uname = str(data.get("username", "") or "").strip()
    if uid:
        target = db.query(User).filter(User.id == int(uid)).first()
    elif uname:
        target = db.query(User).filter((User.username == uname) | (User.nickname == uname)).first()
    if not target:
        raise HTTPException(status_code=404, detail="目标账号不存在")
    m = DirectMessage(user_id=target.id, title=title, content=content, sender=sender)
    db.add(m); db.commit(); db.refresh(m)
    return {"ok": True, "id": m.id, "to": target.nickname or target.username}


@router.post("/messages/send_bulk", summary="向多个账号发送同一消息")
def dm_send_bulk(data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from ..models import DirectMessage
    title = str(data.get("title", "") or "").strip()
    content = str(data.get("content", "") or "").strip()
    sender = str(data.get("sender", "") or "系统").strip() or "系统"
    uids = data.get("user_ids") or []
    alluser = bool(data.get("all", False))
    if not content and not title:
        raise HTTPException(status_code=400, detail="标题和内容不能同时为空")
    targets = []
    if alluser:
        targets = db.query(User).all()
    elif uids:
        targets = db.query(User).filter(User.id.in_([int(i) for i in uids])).all()
    if not targets:
        raise HTTPException(status_code=400, detail="没有有效的接收账号")
    objs = [DirectMessage(user_id=t.id, title=title, content=content, sender=sender) for t in targets]
    db.add_all(objs); db.commit()
    return {"ok": True, "sent": len(objs)}


@router.delete("/messages/{mid}", summary="删除定向消息")
def dm_delete(mid: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from ..models import DirectMessage
    m = db.query(DirectMessage).filter(DirectMessage.id == mid).first()
    if not m:
        raise HTTPException(status_code=404, detail="消息不存在")
    db.delete(m); db.commit()
    return {"ok": True}


# ==================== 🐛 修复：管理员删除动态 ====================
@router.delete("/moments/{moment_id}", summary="管理员删除动态（连带评论/点赞/通知）")
def admin_delete_moment(moment_id: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="动态不存在")
    db.query(CommentLike).filter(CommentLike.comment_id.in_(
        [c.id for c in db.query(Comment.id).filter(Comment.moment_id == moment_id).all()] or [0]
    )).delete(synchronize_session=False)
    db.query(Comment).filter(Comment.moment_id == moment_id).delete(synchronize_session=False)
    db.query(MomentLike).filter(MomentLike.moment_id == moment_id).delete(synchronize_session=False)
    try:
        db.query(Notification).filter(Notification.moment_id == moment_id).delete(synchronize_session=False)
    except Exception:
        pass
    db.delete(m)
    db.commit()
    return {"ok": True, "deleted": moment_id}

# ==================== 🆕 管理员应用审核（不依赖审核员账号） ====================
@router.get("/apps", summary="应用列表（管理员，可按状态筛选）")
def admin_list_apps(
    status_filter: Optional[str] = Query(None, alias="st", description="pending/approved/rejected"),
    keyword: Optional[str] = Query(None, description="按名称/包名搜索"),
    limit: int = Query(200, le=500),
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    q = db.query(AppItem)
    if status_filter:
        q = q.filter(AppItem.status == status_filter)
    if keyword:
        kw = "%" + keyword + "%"
        q = q.filter(or_(AppItem.name.like(kw), AppItem.package_name.like(kw)))
    rows = q.order_by(AppItem.created_at.desc()).limit(limit).all()
    out = []
    for a in rows:
        up = db.query(User).filter(User.id == a.uploader_id).first()
        out.append({
            "id": a.id, "name": a.name or "", "package_name": a.package_name or "",
            "version": a.version or "", "summary": a.summary or "",
            "description": a.description or "", "icon": a.icon or "",
            "screenshots": [x for x in (a.screenshots or "").split(",") if x],
            "category": a.category or "", "status": a.status,
            "reject_reason": a.reject_reason or "",
            "apk_url": a.apk_url or "", "apk_size": a.apk_size or 0,
            "ext_link": a.ext_link or "", "upload_type": a.upload_type or "external",
            "download_count": a.download_count or 0, "view_count": a.view_count or 0,
            "uploader_id": a.uploader_id,
            "uploader_name": (up.nickname or up.username) if up else "",
            "created_at": a.created_at.isoformat() if a.created_at else "",
        })
    return out


@router.post("/apps/{app_id}/review", summary="审核应用：通过/驳回（管理员）")
def admin_review_app(
    app_id: int, data: dict,
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="应用不存在")
    act = (data.get("action") or data.get("status") or "").strip().lower()
    if act in ("approved", "approve", "pass", "通过", "1", "true"):
        approve = True
    elif act in ("rejected", "reject", "refuse", "驳回", "0", "false"):
        approve = False
    else:
        # 兼容 {"approve": true} 写法
        approve = bool(data.get("approve", True))
    a.status = "approved" if approve else "rejected"
    if not approve:
        a.reject_reason = (data.get("reason") or "")[:200]
    name = (a.name or "")[:20]
    try:
        db.add(Notification(user_id=a.uploader_id, actor_id=a.uploader_id, type="audit",
                            content=("你的应用《" + name + "》已通过审核" if approve
                                     else "你的应用《" + name + "》未通过审核" +
                                     ("：" + a.reject_reason if a.reject_reason else ""))))
    except Exception:
        pass
    db.commit()
    return {"ok": True, "id": a.id, "status": a.status, "approve": approve}


@router.delete("/apps/{app_id}", summary="删除应用（管理员）")
def admin_delete_app(
    app_id: int,
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="应用不存在")
    db.delete(a)
    db.commit()
    return {"ok": True, "deleted": app_id}


@router.get("/tools", summary="tool list for admin")
def admin_list_tools(
    status_filter: Optional[str] = Query(None, alias="st"),
    limit: int = Query(200, le=500),
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    q = db.query(Tool)
    if status_filter:
        q = q.filter(Tool.status == status_filter)
    rows = q.order_by(Tool.created_at.desc()).limit(limit).all()
    out = []
    for t in rows:
        up = db.query(User).filter(User.id == t.uploader_id).first()
        out.append({
            "id": t.id, "name": t.name or "",
            "cover": t.cover or "", "intro": t.intro or "",
            "url": t.url or "", "status": t.status,
            "reject_reason": t.reject_reason or "",
            "use_count": t.use_count or 0,
            "uploader_id": t.uploader_id,
            "uploader_name": (up.nickname or up.username) if up else "",
            "created_at": t.created_at.isoformat() if t.created_at else "",
        })
    return out


@router.post("/tools/{tool_id}/review", summary="review tool (admin)")
def admin_review_tool(
    tool_id: int, data: dict,
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    t = db.query(Tool).filter(Tool.id == tool_id).first()
    if not t:
        raise HTTPException(status_code=404, detail="tool not found")
    act = (data.get("action") or data.get("status") or "").strip().lower()
    if act in ("approved", "approve", "pass", "1", "true"):
        approve = True
    elif act in ("rejected", "reject", "0", "false"):
        approve = False
    else:
        approve = bool(data.get("approve", True))
    t.status = "approved" if approve else "rejected"
    if not approve:
        t.reject_reason = (data.get("reason") or "")[:200]
    nm = (t.name or "")[:20]
    try:
        db.add(Notification(user_id=t.uploader_id, actor_id=t.uploader_id, type="audit",
                            content=("your tool " + nm + " approved" if approve
                                     else "your tool " + nm + " rejected")))
    except Exception:
        pass
    db.commit()
    return {"ok": True, "id": t.id, "status": t.status}


@router.delete("/tools/{tool_id}", summary="delete tool (admin)")
def admin_delete_tool(
    tool_id: int,
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    t = db.query(Tool).filter(Tool.id == tool_id).first()
    if not t:
        raise HTTPException(status_code=404, detail="tool not found")
    db.delete(t)
    db.commit()
    return {"ok": True, "deleted": tool_id}



# ==================== 新增管理接口（评论/封禁/公告/通知/看板/总览/管理员） ====================

# ---------- 评论管理 ----------
@router.get("/comments", summary="评论列表（管理员）")
def admin_list_comments(
    keyword: Optional[str] = Query(None, description="按内容搜索"),
    moment_id: Optional[int] = Query(None),
    limit: int = Query(200, le=500),
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    q = db.query(Comment)
    if moment_id:
        q = q.filter(Comment.moment_id == moment_id)
    if keyword:
        q = q.filter(Comment.content.like("%" + keyword + "%"))
    rows = q.order_by(Comment.created_at.desc()).limit(limit).all()
    # 批量取用户昵称
    uids = list({c.user_id for c in rows})
    umap = {}
    if uids:
        for u in db.query(User).filter(User.id.in_(uids)).all():
            umap[u.id] = u.nickname or u.username
    out = []
    for c in rows:
        out.append({
            "id": c.id, "moment_id": c.moment_id, "user_id": c.user_id,
            "username": umap.get(c.user_id, ""),
            "content": c.content or "",
            "parent_id": c.parent_id,
            "created_at": c.created_at.isoformat() if c.created_at else "",
        })
    return out


@router.delete("/comments/{comment_id}", summary="删除评论（管理员）")
def admin_delete_comment(comment_id: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    c = db.query(Comment).filter(Comment.id == comment_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="评论不存在")
    # 清点赞 + 子回复
    try:
        db.query(CommentLike).filter(CommentLike.comment_id == comment_id).delete(synchronize_session=False)
    except Exception:
        pass
    try:
        db.query(Comment).filter(Comment.parent_id == comment_id).delete(synchronize_session=False)
    except Exception:
        pass
    db.delete(c)
    db.commit()
    return {"ok": True, "deleted": comment_id}


# ---------- 用户封禁/禁言 ----------
@router.post("/users/{user_id}/ban", summary="封禁/解封用户")
def admin_ban_user(user_id: int, data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    if u.is_admin:
        raise HTTPException(status_code=400, detail="不能封禁管理员")
    ban = bool(data.get("ban", True))
    u.is_banned = ban
    u.ban_reason = (data.get("reason") or "")[:200] if ban else ""
    # v9.7: 支持限时封禁（days 天），days<=0 或未传 = 永久
    from datetime import datetime as _dtn, timedelta as _tdt
    if ban:
        try:
            days = int(data.get("days") or 0)
        except Exception:
            days = 0
        u.ban_until = (_dtn.utcnow() + _tdt(days=days)) if days > 0 else None
    else:
        u.ban_until = None
    try:
        if ban:
            db.add(Notification(user_id=u.id, actor_id=u.id, type="system",
                                content="你的账号已被封禁" + ("：" + u.ban_reason if u.ban_reason else "")))
    except Exception:
        pass
    db.commit()
    return {"ok": True, "id": u.id, "is_banned": u.is_banned}


# ---------- 公告管理（编辑/删除） ----------
@router.put("/announcements/{aid}", summary="编辑公告")
def admin_edit_announcement(aid: int, data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    a = db.query(Announcement).filter(Announcement.id == aid).first()
    if not a:
        raise HTTPException(status_code=404, detail="公告不存在")
    if "title" in data:
        a.title = (data.get("title") or "")[:120]
    if "content" in data:
        a.content = data.get("content") or ""
    if "target_version" in data:
        a.target_version = (data.get("target_version") or "")[:20]
    db.commit()
    return {"ok": True, "id": a.id}


@router.delete("/announcements/{aid}", summary="删除公告")
def admin_delete_announcement(aid: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    a = db.query(Announcement).filter(Announcement.id == aid).first()
    if not a:
        raise HTTPException(status_code=404, detail="公告不存在")
    db.delete(a)
    db.commit()
    return {"ok": True, "deleted": aid}


# ---------- 通知管理 ----------
@router.get("/notifications", summary="通知记录（管理员）")
def admin_list_notifications(
    keyword: Optional[str] = Query(None),
    limit: int = Query(200, le=500),
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    q = db.query(Notification)
    if keyword:
        q = q.filter(Notification.content.like("%" + keyword + "%"))
    rows = q.order_by(Notification.created_at.desc()).limit(limit).all()
    uids = list({n.user_id for n in rows} | {n.actor_id for n in rows})
    umap = {}
    if uids:
        for u in db.query(User).filter(User.id.in_(uids)).all():
            umap[u.id] = u.nickname or u.username
    out = []
    for n in rows:
        out.append({
            "id": n.id, "user_id": n.user_id, "user_name": umap.get(n.user_id, ""),
            "actor_id": n.actor_id, "actor_name": umap.get(n.actor_id, ""),
            "type": n.type, "content": n.content or "",
            "is_read": bool(n.is_read), "created_at": n.created_at.isoformat() if n.created_at else "",
        })
    return out


@router.delete("/notifications/{nid}", summary="删除通知")
def admin_delete_notification(nid: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    n = db.query(Notification).filter(Notification.id == nid).first()
    if not n:
        raise HTTPException(status_code=404, detail="通知不存在")
    db.delete(n)
    db.commit()
    return {"ok": True, "deleted": nid}


# ---------- 数据看板 ----------
@router.get("/dashboard", summary="数据看板（趋势）")
def admin_dashboard(days: int = Query(14, le=60), db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    from datetime import datetime, timedelta
    today = datetime.utcnow().date()
    labels, users_series, moments_series = [], [], []
    for i in range(days - 1, -1, -1):
        d = today - timedelta(days=i)
        start = datetime(d.year, d.month, d.day)
        end = start + timedelta(days=1)
        labels.append(d.strftime("%m-%d"))
        users_series.append(db.query(User).filter(User.created_at >= start, User.created_at < end).count())
        moments_series.append(db.query(Moment).filter(Moment.created_at >= start, Moment.created_at < end).count())
    return {
        "labels": labels,
        "users": users_series,
        "moments": moments_series,
        "total_users": db.query(User).count(),
        "total_moments": db.query(Moment).count(),
        "total_apps": db.query(AppItem).count(),
        "banned_users": db.query(User).filter(User.is_banned == True).count(),
    }


# ---------- 内容总览（帖子/评论/应用 统一搜索） ----------
@router.get("/content/search", summary="内容总览搜索（帖子/评论/应用）")
def admin_content_search(q: str = Query(..., description="关键词"), db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    kw = "%" + q + "%"
    moments = db.query(Moment).filter(or_(Moment.title.like(kw), Moment.content.like(kw))).order_by(Moment.id.desc()).limit(50).all()
    comments = db.query(Comment).filter(Comment.content.like(kw)).order_by(Comment.id.desc()).limit(50).all()
    apps = db.query(AppItem).filter(or_(AppItem.name.like(kw), AppItem.summary.like(kw))).order_by(AppItem.id.desc()).limit(50).all()
    return {
        "moments": [{"id": m.id, "title": m.title or "", "content": (m.content or "")[:100],
                     "status": m.status, "user_id": m.user_id,
                     "created_at": m.created_at.isoformat() if m.created_at else ""} for m in moments],
        "comments": [{"id": c.id, "moment_id": c.moment_id, "user_id": c.user_id,
                      "content": (c.content or "")[:120],
                      "created_at": c.created_at.isoformat() if c.created_at else ""} for c in comments],
        "apps": [{"id": a.id, "name": a.name or "", "summary": (a.summary or "")[:100],
                  "status": a.status, "uploader_id": a.uploader_id,
                  "created_at": a.created_at.isoformat() if a.created_at else ""} for a in apps],
    }


# ---------- 管理员账号管理 ----------
@router.get("/admins", summary="管理员列表")
def admin_list_admins(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    rows = db.query(User).filter(User.is_admin == True).all()
    return [{"id": u.id, "username": u.username, "nickname": u.nickname,
             "created_at": u.created_at.isoformat() if u.created_at else ""} for u in rows]


@router.post("/admins/{user_id}/password", summary="重置某管理员密码")
def admin_reset_other_password(user_id: int, data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    pwd = (data.get("password") or "").strip()
    if len(pwd) < 6:
        raise HTTPException(status_code=400, detail="密码至少 6 位")
    u.password_hash = hash_password(pwd)
    db.commit()
    return {"ok": True, "id": u.id}


@router.post("/admins/{user_id}/revoke", summary="取消管理员")
def admin_revoke_admin(user_id: int, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    if user_id == admin.id:
        raise HTTPException(status_code=400, detail="不能取消自己")
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    u.is_admin = False
    db.commit()
    return {"ok": True, "id": u.id}


# ---------- 应用编辑（补标题/分类/图标/截图） ----------
@router.put("/apps/{app_id}", summary="编辑应用信息（管理员）")
def admin_edit_app(app_id: int, data: dict, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="应用不存在")
    for k in ("name", "package_name", "version", "summary", "description", "icon", "category", "ext_link", "apk_url"):
        if k in data:
            setattr(a, k, (data.get(k) or ""))
    if "screenshots" in data:
        arr = data.get("screenshots") or []
        if isinstance(arr, list):
            a.screenshots = ",".join([x for x in arr if x])
        elif isinstance(arr, str):
            a.screenshots = arr
    db.commit()
    return {"ok": True, "id": a.id}


# ============ 频道审核（管理员） ============
@router.get("/channels", summary="频道列表（可按状态筛选）")
def admin_channels(st: Optional[str] = Query(None), limit: int = Query(200, le=500),
                   db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    q = db.query(Channel)
    if st:
        q = q.filter(Channel.status == st)
    rows = q.order_by(Channel.id.desc()).limit(limit).all()
    out = []
    for c in rows:
        out.append({
            "id": c.id, "name": c.name or "", "intro": c.intro or "",
            "image": c.image or "", "owner_id": c.owner_id, "status": c.status,
            "member_count": c.member_count or 0,
            "created_at": c.created_at.isoformat() if c.created_at else "",
        })
    return out


@router.post("/channels/{channel_id}/review", summary="审核频道：通过/驳回（管理员）")
def admin_review_channel(
    channel_id: int, data: dict,
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    c = db.query(Channel).filter(Channel.id == channel_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="频道不存在")
    act = (data.get("action") or data.get("status") or "").strip().lower()
    if act in ("approved", "approve", "pass", "通过", "1", "true"):
        approve = True
    elif act in ("rejected", "reject", "refuse", "驳回", "0", "false"):
        approve = False
    else:
        approve = bool(data.get("approve", True))
    c.status = "approved" if approve else "rejected"
    nm = (c.name or "")[:20]
    try:
        db.add(Notification(user_id=c.owner_id, actor_id=c.owner_id, type="audit",
                            content=("你的频道《" + nm + "》已通过审核" if approve
                                     else "你的频道《" + nm + "》未通过审核")))
    except Exception:
        pass
    db.commit()
    return {"ok": True, "id": c.id, "status": c.status, "approve": approve}


@router.delete("/channels/{channel_id}", summary="删除频道（管理员）")
def admin_del_channel(
    channel_id: int,
    db: Session = Depends(get_db), admin: User = Depends(get_current_admin),
):
    c = db.query(Channel).filter(Channel.id == channel_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="频道不存在")
    db.delete(c); db.commit()
    return {"ok": True}


# ============ admin_loginlog_v1 登录日志 & 协议留档 ============
@router.get("/login-logs", summary="登录日志查询")
def admin_login_logs(role: Optional[str] = Query(None), limit: int = Query(200, le=1000),
                     offset: int = Query(0, ge=0), keyword: Optional[str] = Query(None),
                     db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    q = db.query(LoginLog)
    if role:
        q = q.filter(LoginLog.role == role)
    if keyword:
        kw = "%" + keyword.strip() + "%"
        q = q.filter((LoginLog.username.like(kw)) | (LoginLog.ip.like(kw)))
    total = q.count()
    rows = q.order_by(LoginLog.id.desc()).offset(offset).limit(limit).all()
    items = [{
        "id": r.id, "role": r.role, "user_id": r.user_id, "username": r.username or "",
        "ip": r.ip or "", "ok": bool(r.ok), "fail_reason": r.fail_reason or "",
        "platform": r.platform or "",
        "user_agent": r.user_agent or "",
        "created_at": r.created_at.isoformat() if r.created_at else "",
    } for r in rows]
    return {"total": total, "items": items}


@router.get("/login-logs/stats", summary="登录日志统计")
def admin_login_log_stats(days: int = Query(7, ge=1, le=90),
                          db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    since = datetime.utcnow() - datetime.timedelta(days=days)
    rows = db.query(LoginLog).filter(LoginLog.created_at >= since).all()
    by_role = {}
    by_day = {}
    fail = 0
    for r in rows:
        by_role[r.role] = by_role.get(r.role, 0) + 1
        d = r.created_at.strftime("%Y-%m-%d") if r.created_at else ""
        by_day[d] = by_day.get(d, 0) + 1
        if not r.ok:
            fail += 1
    return {"days": days, "total": len(rows), "fail": fail,
            "by_role": by_role, "by_day": by_day}


@router.get("/reviewer-agreements", summary="审核员协议签署记录")
def admin_reviewer_agreements(limit: int = Query(200, le=1000),
                              db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    rows = db.query(ReviewerAgreement).order_by(ReviewerAgreement.id.desc()).limit(limit).all()
    out = []
    for r in rows:
        rv = db.query(Reviewer).filter(Reviewer.id == r.reviewer_id).first()
        out.append({
            "id": r.id, "reviewer_id": r.reviewer_id,
            "reviewer_username": rv.username if rv else "",
            "reviewer_nickname": (rv.nickname or rv.username) if rv else "",
            "version": r.version, "agreed": bool(r.agreed),
            "ip": r.ip or "", "user_agent": r.user_agent or "",
            "signed_at": r.signed_at.isoformat() if r.signed_at else "",
        })
    return out


@router.get("/login-logs/export", summary="导出登录日志CSV")
def admin_login_logs_export(role: Optional[str] = Query(None),
                            db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    from fastapi.responses import PlainTextResponse
    q = db.query(LoginLog)
    if role:
        q = q.filter(LoginLog.role == role)
    rows = q.order_by(LoginLog.id.desc()).limit(5000).all()
    lines = ["id,role,user_id,username,ip,ok,fail_reason,platform,created_at"]
    for r in rows:
        lines.append(",".join([
            str(r.id), r.role or "", str(r.user_id or 0),
            '"' + (r.username or "").replace('"', "'") + '"',
            r.ip or "", "1" if r.ok else "0",
            '"' + (r.fail_reason or "").replace('"', "'") + '"',
            r.platform or "",
            r.created_at.strftime("%Y-%m-%d %H:%M:%S") if r.created_at else "",
        ]))
    return PlainTextResponse("\n".join(lines), media_type="text/csv",
                             headers={"Content-Disposition": "attachment; filename=login_logs.csv"})


# ============ admin_resmgmt_v1 资源管理 ============
@router.get("/resources", summary="资源列表")
def admin_resources(st: Optional[str] = Query(None), category: Optional[str] = Query(None),
                    keyword: Optional[str] = Query(None),
                    limit: int = Query(100, le=500), offset: int = Query(0, ge=0),
                    db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    q = db.query(ResourceItem)
    if st:
        q = q.filter(ResourceItem.status == st)
    if category:
        q = q.filter(ResourceItem.category == category)
    if keyword:
        kw = "%" + keyword.strip() + "%"
        q = q.filter((ResourceItem.name.like(kw)) | (ResourceItem.summary.like(kw)))
    total = q.count()
    rows = q.order_by(ResourceItem.id.desc()).offset(offset).limit(limit).all()
    items = []
    for r in rows:
        u = db.query(User).filter(User.id == r.uploader_id).first()
        items.append({
            "id": r.id, "name": r.name or "", "summary": r.summary or "",
            "description": r.description or "", "category": r.category or "",
            "avatar": r.avatar or "", "cover": r.cover or "",
            "screenshots": r.screenshots or "",
            "res_type": r.res_type or "link", "download_url": r.download_url or "",
            "file_size": r.file_size or 0, "is_paid": bool(r.is_paid),
            "price": r.price or 0, "status": r.status,
            "reject_reason": r.reject_reason or "",
            "download_count": r.download_count or 0,
            "view_count": r.view_count or 0,
            "tip_total": r.tip_total or 0,
            "uploader_id": r.uploader_id,
            "uploader_name": (u.nickname or u.username) if u else "",
            "created_at": r.created_at.isoformat() if r.created_at else "",
        })
    return {"total": total, "items": items}


@router.get("/resources/stats", summary="资源统计")
def admin_resources_stats(db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    q = db.query(ResourceItem)
    return {
        "total": q.count(),
        "pending": q.filter(ResourceItem.status == "pending").count(),
        "approved": q.filter(ResourceItem.status == "approved").count(),
        "rejected": q.filter(ResourceItem.status == "rejected").count(),
        "paid": q.filter(ResourceItem.is_paid == True).count(),
    }


@router.get("/resources/categories", summary="资源分类列表")
def admin_resources_categories(db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    rows = db.query(ResourceItem.category).distinct().all()
    return [r[0] for r in rows if r[0]]


@router.post("/resources/{rid}/review", summary="审核资源")
def admin_review_resource(rid: int, data: dict,
                          db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    action = (data.get("action") or "").strip()
    if action in ("approved", "approve"):
        r.status = "approved"
        r.reject_reason = ""
        txt = "你的资源《" + (r.name or "")[:20] + "》已通过审核"
    elif action in ("rejected", "reject"):
        r.status = "rejected"
        reason = (data.get("reason") or "")[:300]
        r.reject_reason = reason
        txt = "你的资源《" + (r.name or "")[:20] + "》未通过审核" + ("：" + reason if reason else "")
    else:
        raise HTTPException(status_code=400, detail="action 必须是 approved 或 rejected")
    db.add(Notification(user_id=r.uploader_id, actor_id=r.uploader_id,
                        type="audit", content=txt))
    db.commit()
    return {"ok": True, "id": r.id, "status": r.status}


@router.put("/resources/{rid}", summary="编辑资源")
def admin_edit_resource(rid: int, data: dict,
                        db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    fields = ["name", "summary", "description", "category", "cover", "download_url",
              "res_type", "status", "reject_reason"]
    for f in fields:
        if f in data and data[f] is not None:
            setattr(r, f, data[f])
    if "is_paid" in data:
        r.is_paid = bool(data["is_paid"])
    if "price" in data:
        try:
            r.price = int(data["price"])
        except Exception:
            pass
    db.commit()
    return {"ok": True, "id": r.id}


@router.delete("/resources/{rid}", summary="删除资源")
def admin_delete_resource(rid: int,
                          db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    db.query(ResourceTip).filter(ResourceTip.resource_id == rid).delete()
    db.query(ResourceUnlock).filter(ResourceUnlock.resource_id == rid).delete()
    db.delete(r)
    db.commit()
    return {"ok": True}


@router.get("/resources/{rid}", summary="资源详情")
def admin_resource_detail(rid: int,
                          db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    u = db.query(User).filter(User.id == r.uploader_id).first()
    tips = db.query(ResourceTip).filter(ResourceTip.resource_id == rid).count()
    unlocks = db.query(ResourceUnlock).filter(ResourceUnlock.resource_id == rid).count()
    return {
        "id": r.id, "name": r.name or "", "summary": r.summary or "",
        "description": r.description or "", "category": r.category or "",
        "avatar": r.avatar or "", "cover": r.cover or "",
        "screenshots": r.screenshots or "",
        "res_type": r.res_type or "link", "download_url": r.download_url or "",
        "file_size": r.file_size or 0, "is_paid": bool(r.is_paid),
        "price": r.price or 0, "status": r.status,
        "reject_reason": r.reject_reason or "",
        "download_count": r.download_count or 0, "view_count": r.view_count or 0,
        "tip_total": r.tip_total or 0,
        "uploader_id": r.uploader_id,
        "uploader_name": (u.nickname or u.username) if u else "",
        "tip_count": tips, "unlock_count": unlocks,
        "created_at": r.created_at.isoformat() if r.created_at else "",
    }

