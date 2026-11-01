# -*- coding: utf-8 -*-
"""小德社区 API - 社交模块：关注 / 个人主页 / 私信"""
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy import or_, and_, func

from ..database import get_db
from ..models import User, Follow, PrivateMessage, Moment, AppItem, Report, Notification, ResourceItem
from ..schemas import SocialUserOut, CardUserOut, PrivateMsgIn, PrivateMsgOut, PrivateConvOut
from ..deps import get_current_user, get_current_user_optional
from ..levels import level_for_exp, level_effect
router = APIRouter(prefix="/api/social", tags=["社交"])


def _notify(db: Session, user_id: int, actor_id: int, ntype: str, content: str = ""):
    """插入一条系统通知；自己触发自己的行为不通知。"""
    if user_id == actor_id:
        return
    db.add(Notification(user_id=user_id, actor_id=actor_id, type=ntype, content=(content or "")[:255]))

def _card(u: User, followed: bool) -> CardUserOut:
    lv = level_for_exp(u.exp or 0)
    return CardUserOut(
        id=u.id, username=u.username, nickname=u.nickname or "",
        avatar=u.avatar or "", level=lv, level_effect=level_effect(lv),
        is_vip=bool(u.vip_expire and u.vip_expire > __import__("datetime").datetime.utcnow()),
        followed=followed,
    )



# ==================== 关注 ====================
@router.post("/follow/{user_id}", summary="关注/取消关注 切换")
def toggle_follow(user_id: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    if user_id == current.id:
        raise HTTPException(status_code=400, detail="不能关注自己")
    target = db.query(User).filter(User.id == user_id).first()
    if not target:
        raise HTTPException(status_code=404, detail="用户不存在")
    ex = db.query(Follow).filter(Follow.follower_id == current.id, Follow.followee_id == user_id).first()
    if ex:
        db.delete(ex); db.commit(); followed = False
    else:
        db.add(Follow(follower_id=current.id, followee_id=user_id))
        # 关注通知：仅在"新关注"时发，取消关注不发
        _notify(db, user_id, current.id, "follow", "关注了你")
        db.commit(); followed = True
    cnt = db.query(Follow).filter(Follow.followee_id == user_id).count()
    return {"followed": followed, "follower_count": cnt}

@router.get("/following", response_model=List[CardUserOut], summary="我关注的人")
def my_following(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = db.query(Follow).filter(Follow.follower_id == current.id).order_by(Follow.created_at.desc()).all()
    ids = [r.followee_id for r in rows]
    if not ids:
        return []
    users = db.query(User).filter(User.id.in_(ids)).all()
    umap = {u.id: u for u in users}
    return [_card(umap[i], True) for i in ids if i in umap]

@router.get("/followers", response_model=List[CardUserOut], summary="我的粉丝")
def my_followers(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = db.query(Follow).filter(Follow.followee_id == current.id).order_by(Follow.created_at.desc()).all()
    ids = [r.follower_id for r in rows]
    if not ids:
        return []
    users = db.query(User).filter(User.id.in_(ids)).all()
    umap = {u.id: u for u in users}
    mine = set(x.followee_id for x in db.query(Follow).filter(Follow.follower_id == current.id).all())
    return [_card(umap[i], i in mine) for i in ids if i in umap]


# ==================== 用户主页详情 ====================
@router.get("/user/{user_id}", response_model=SocialUserOut, summary="用户主页详情")
def user_profile(user_id: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    lv = level_for_exp(u.exp or 0)
    follower_count = db.query(Follow).filter(Follow.followee_id == user_id).count()
    following_count = db.query(Follow).filter(Follow.follower_id == user_id).count()
    followed = db.query(Follow).filter(Follow.follower_id == current.id, Follow.followee_id == user_id).first() is not None
    moment_count = db.query(Moment).filter(Moment.user_id == user_id, Moment.status == "approved").count()
    app_count = db.query(AppItem).filter(AppItem.uploader_id == user_id, AppItem.status == "approved").count()
    import datetime as _dt
    is_vip = bool(u.vip_expire and u.vip_expire > _dt.datetime.utcnow())
    return SocialUserOut(
        id=u.id, uid=(u.uid or ""), username=u.username, nickname=u.nickname or "", avatar=u.avatar or "",
        exp=u.exp or 0, level=lv, level_effect=level_effect(lv), is_vip=is_vip,
        follower_count=follower_count, following_count=following_count,
        followed=followed, is_self=(u.id == current.id),
        moment_count=moment_count, app_count=app_count,
        page_bg=(u.page_bg or ""), bio=(u.bio or ""),
    )


# ==================== 用户主页内容（公开可见）====================
@router.get("/user/{user_id}/moments", summary="某用户发布的动态（仅审核通过）")
def user_moments(user_id: int,
                 limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0),
                 db: Session = Depends(get_db),
                 current: Optional[User] = Depends(get_current_user_optional)):
    from .moments_router import _to_out, _moment_like_counts, _my_moment_likes
    q = db.query(Moment).filter(Moment.user_id == user_id, Moment.status == "approved")
    total = q.count()
    rows = q.order_by(Moment.created_at.desc()).offset(offset).limit(limit).all()
    ids = [m.id for m in rows]
    counts = _moment_like_counts(db, ids)
    mylikes = _my_moment_likes(db, current.id, ids) if current else set()
    items = [_to_out(m, None, counts.get(m.id, 0), m.id in mylikes) for m in rows]
    return {"items": items, "total": total, "has_more": offset + len(items) < total}


@router.get("/user/{user_id}/resources", summary="某用户上传的资源（仅审核通过）")
def user_resources(user_id: int,
                   limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0),
                   db: Session = Depends(get_db),
                   current: Optional[User] = Depends(get_current_user_optional)):
    from .resources_router import _res_out
    q = db.query(ResourceItem).filter(ResourceItem.uploader_id == user_id,
                                      ResourceItem.status == "approved")
    total = q.count()
    rows = q.order_by(ResourceItem.id.desc()).offset(offset).limit(limit).all()
    items = [_res_out(r, db, current) for r in rows]
    return {"items": items, "total": total, "has_more": offset + len(items) < total}


@router.get("/user/{user_id}/apps", summary="某用户上传的应用（仅审核通过）")
def user_apps(user_id: int,
              limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0),
              db: Session = Depends(get_db),
              current: Optional[User] = Depends(get_current_user_optional)):
    from .apps_router import _app_out
    q = db.query(AppItem).filter(AppItem.uploader_id == user_id,
                                 AppItem.status == "approved")
    total = q.count()
    rows = q.order_by(AppItem.created_at.desc()).offset(offset).limit(limit).all()
    items = [_app_out(a, db) for a in rows]
    return {"items": items, "total": total, "has_more": offset + len(items) < total}


# ==================== 私信（仅文字）====================
def _is_mutual(db: Session, u1: int, u2: int) -> bool:
    ab = db.query(Follow).filter(Follow.follower_id == u1, Follow.followee_id == u2).first()
    ba = db.query(Follow).filter(Follow.follower_id == u2, Follow.followee_id == u1).first()
    return bool(ab and ba)

@router.post("/pm/{user_id}", response_model=PrivateMsgOut, summary="发私信（仅文字）")
def send_pm(user_id: int, data: PrivateMsgIn, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    if user_id == current.id:
        raise HTTPException(status_code=400, detail="不能给自己发私信")
    target = db.query(User).filter(User.id == user_id).first()
    if not target:
        raise HTTPException(status_code=404, detail="用户不存在")
    if not _is_mutual(db, current.id, user_id):
        sent = db.query(PrivateMessage).filter(PrivateMessage.sender_id == current.id, PrivateMessage.receiver_id == user_id).count()
        if sent >= 1:
            raise HTTPException(status_code=403, detail="对方还未关注你，最多只能发送 1 条私信")
    msg = PrivateMessage(sender_id=current.id, receiver_id=user_id, content=data.content, is_read=False)
    db.add(msg); db.commit(); db.refresh(msg)
    return PrivateMsgOut(id=msg.id, sender_id=msg.sender_id, receiver_id=msg.receiver_id, content=msg.content, is_read=msg.is_read, created_at=msg.created_at)

@router.get("/pm/conversations", response_model=List[PrivateConvOut], summary="私信会话列表")
def pm_conversations(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    msgs = db.query(PrivateMessage).filter(or_(PrivateMessage.sender_id == current.id, PrivateMessage.receiver_id == current.id)).order_by(PrivateMessage.created_at.desc()).all()
    seen = {}
    result = []
    for m in msgs:
        other = m.receiver_id if m.sender_id == current.id else m.sender_id
        if other in seen:
            continue
        seen[other] = True
        ou = db.query(User).filter(User.id == other).first()
        if not ou:
            continue
        unread = db.query(PrivateMessage).filter(PrivateMessage.sender_id == other, PrivateMessage.receiver_id == current.id, PrivateMessage.is_read == False).count()
        result.append(PrivateConvOut(user=_card(ou, _is_mutual(db, current.id, other)), last_msg=m.content, last_time=m.created_at, unread=unread))
    return result

@router.get("/pm/{user_id}", response_model=List[PrivateMsgOut], summary="与某人的私信记录")
def pm_history(user_id: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    msgs = db.query(PrivateMessage).filter(
        or_(and_(PrivateMessage.sender_id == current.id, PrivateMessage.receiver_id == user_id),
            and_(PrivateMessage.sender_id == user_id, PrivateMessage.receiver_id == current.id))
    ).order_by(PrivateMessage.created_at.asc()).all()
    for m in msgs:
        if m.receiver_id == current.id and not m.is_read:
            m.is_read = True
    db.commit()
    return [PrivateMsgOut(id=m.id, sender_id=m.sender_id, receiver_id=m.receiver_id, content=m.content, is_read=m.is_read, created_at=m.created_at) for m in msgs]

@router.get("/pm/unread/count", summary="私信未读总数")
def pm_unread(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    n = db.query(PrivateMessage).filter(PrivateMessage.receiver_id == current.id, PrivateMessage.is_read == False).count()
    return {"unread": n}

# ==================== 举报 ====================
@router.post("/report", summary="举报内容（帖子/应用/频道/评论/用户）")
def submit_report(data: dict, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    ttype = (data.get("target_type") or "moment").strip()
    if ttype not in ("moment", "app", "channel", "comment", "user"):
        raise HTTPException(status_code=400, detail="举报类型无效")
    tid = int(data.get("target_id") or 0)
    if tid <= 0:
        raise HTTPException(status_code=400, detail="缺少举报目标")
    reason = (data.get("reason") or "").strip()[:200]
    detail = (data.get("detail") or "").strip()
    if not reason:
        raise HTTPException(status_code=400, detail="请选择或填写举报原因")
    r = Report(reporter_id=current.id, target_type=ttype, target_id=tid,
               reason=reason, detail=detail, status="pending")
    db.add(r); db.flush()
    # 举报回执：给自己一条系统消息（actor 也是自己 → 用 force 写入）
    db.add(Notification(user_id=current.id, actor_id=current.id, type="report",
                        content="你的举报已提交，我们会尽快处理"))
    db.commit(); db.refresh(r)
    return {"ok": True, "id": r.id}


@router.get("/report/reasons", summary="举报原因选项")
def report_reasons():
    return ["色情低俗", "广告垃圾", "违法违规", "人身攻击", "诈骗信息", "侵权抄袭", "其他"]

