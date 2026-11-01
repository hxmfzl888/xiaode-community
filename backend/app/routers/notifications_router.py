# -*- coding: utf-8 -*-
"""小德日历 API - 消息通知路由：谁点赞/评论了我"""
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import User, Notification
from ..schemas import NotificationOut, NotificationListOut
from ..deps import get_current_user

router = APIRouter(prefix="/api/notifications", tags=["消息"])


def _out(n, actor):
    return NotificationOut(
        id=n.id, type=n.type, moment_id=n.moment_id, comment_id=n.comment_id,
        content=n.content, is_read=n.is_read, is_pinned=n.is_pinned, created_at=n.created_at,
        actor_id=n.actor_id,
        actor_nickname=actor.nickname if actor else "",
        actor_username=actor.username if actor else "",
        actor_avatar=actor.avatar if actor else "")


@router.get("", response_model=NotificationListOut, summary="我的消息列表")
def list_notifications(limit: int = Query(50, ge=1, le=200), offset: int = Query(0, ge=0),
                       db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    q = db.query(Notification).filter(Notification.user_id == current.id)
    total = q.count()
    unread = q.filter(Notification.is_read == False).count()  # noqa: E712
    rows = q.order_by(Notification.is_pinned.desc(), Notification.created_at.desc()).offset(offset).limit(limit).all()
    actor_ids = list({n.actor_id for n in rows})
    actors = {}
    if actor_ids:
        for u in db.query(User).filter(User.id.in_(actor_ids)).all():
            actors[u.id] = u
    return NotificationListOut(total=total, unread=unread,
                               items=[_out(n, actors.get(n.actor_id)) for n in rows])


@router.get("/unread_count", summary="未读消息数（用于红点）")
def unread_count(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    cnt = (db.query(Notification)
           .filter(Notification.user_id == current.id, Notification.is_read == False)  # noqa: E712
           .count())
    return {"unread": cnt}


@router.post("/read_all", summary="全部标为已读")
def mark_all_read(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    (db.query(Notification)
     .filter(Notification.user_id == current.id, Notification.is_read == False)  # noqa: E712
     .update({Notification.is_read: True}, synchronize_session=False))
    db.commit()
    return {"ok": True}


@router.post("/{nid}/read", summary="单条标为已读")
def mark_read(nid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    n = (db.query(Notification)
         .filter(Notification.id == nid, Notification.user_id == current.id).first())
    if not n:
        raise HTTPException(status_code=404, detail="消息不存在")
    n.is_read = True
    db.commit()
    return {"ok": True}

@router.delete("/{nid}", summary="删除单条消息")
def delete_notification(nid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    n = (db.query(Notification)
         .filter(Notification.id == nid, Notification.user_id == current.id).first())
    if not n:
        raise HTTPException(status_code=404, detail="消息不存在")
    db.delete(n); db.commit()
    return {"ok": True}

@router.post("/clear", summary="清空我的所有消息")
def clear_notifications(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    (db.query(Notification)
     .filter(Notification.user_id == current.id)
     .delete(synchronize_session=False))
    db.commit()
    return {"ok": True}

@router.post("/{nid}/pin", summary="置顶/取消置顶 一条消息")
def toggle_pin(nid: int, db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    n = (db.query(Notification)
         .filter(Notification.id == nid, Notification.user_id == current.id).first())
    if not n:
        raise HTTPException(status_code=404, detail="消息不存在")
    n.is_pinned = not bool(n.is_pinned)
    db.commit()
    return {"ok": True, "is_pinned": bool(n.is_pinned)}
