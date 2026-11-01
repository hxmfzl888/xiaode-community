# -*- coding: utf-8 -*-
"""小德助手 API - 聊天模块：用户搜索/详情 / 好友申请与同意 / 私聊消息"""
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from sqlalchemy import or_, and_, func
from ..database import get_db
from ..models import User, Friendship, ChatMessage, Notification
from ..schemas import (
    UserLevelOut, FriendOut, FriendReqOut, FriendRequestIn,
    ChatSendIn, ChatMsgOut, ChatConvOut,
)
from ..deps import get_current_user
from ..levels import level_for_exp, level_effect, exp_for_level, can_send_video
from .task_router import grant_exp

router = APIRouter(prefix="/api/chat", tags=["聊天"])


def _uout(u: User, viewer: User, db: Session) -> UserLevelOut:
    lv = level_for_exp(u.exp or 0)
    st = ""
    is_friend = False
    if viewer is not None and viewer.id != u.id:
        f1 = (db.query(Friendship)
              .filter(Friendship.user_id == viewer.id, Friendship.friend_id == u.id).first())
        f2 = (db.query(Friendship)
              .filter(Friendship.user_id == u.id, Friendship.friend_id == viewer.id).first())
        if f1 and f1.status == "accepted":
            is_friend = True; st = "accepted"
        elif f2 and f2.status == "accepted":
            is_friend = True; st = "accepted"
        elif f1 and f1.status == "pending":
            st = "outgoing"
        elif f2 and f2.status == "pending":
            st = "incoming"
    return UserLevelOut(
        id=u.id, uid=(u.uid or ""), username=u.username, nickname=u.nickname or "", avatar=u.avatar or "",
        exp=u.exp or 0, level=lv, level_effect=level_effect(lv),
        name_color=getattr(u, "name_color", "") or "",
        text_effect=getattr(u, "text_effect", "") or "",
        next_exp=exp_for_level(lv + 1), can_video=can_send_video(lv),
        is_friend=is_friend, friend_status=st)


# ---------------- 用户搜索 / 详情 ----------------
@router.get("/user/{username}", response_model=UserLevelOut, summary="按用户名查用户详情")
def get_user(username: str, db: Session = Depends(get_db),
             current: User = Depends(get_current_user)):
    u = db.query(User).filter(User.username == username).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    return _uout(u, current, db)


@router.get("/search", response_model=List[UserLevelOut], summary="搜索用户（用户名/昵称模糊）")
def search_users(q: str = Query("", max_length=50),
                 db: Session = Depends(get_db),
                 current: User = Depends(get_current_user)):
    q = (q or "").strip()
    if not q:
        return []
    like = "%" + q + "%"
    # SEARCH_UID_V1: 支持按 8 位 UID 精确查找
    conds = [User.username.like(like), User.nickname.like(like)]
    if q.isdigit():
        conds.append(User.uid == q)
    rows = (db.query(User)
            .filter(User.id != current.id)
            .filter(or_(*conds))
            .limit(20).all())
    return [_uout(u, current, db) for u in rows]


# ---------------- 好友 ----------------
@router.post("/friend/request", summary="发送好友申请")
def send_friend_request(data: FriendRequestIn, db: Session = Depends(get_db),
                        current: User = Depends(get_current_user)):
    u = db.query(User).filter(User.username == data.username.strip()).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    if u.id == current.id:
        raise HTTPException(status_code=400, detail="不能添加自己为好友")
    # 已存在任一方向的关系？
    f1 = (db.query(Friendship)
          .filter(Friendship.user_id == current.id, Friendship.friend_id == u.id).first())
    f2 = (db.query(Friendship)
          .filter(Friendship.user_id == u.id, Friendship.friend_id == current.id).first())
    if f1 and f1.status == "accepted":
        return {"ok": True, "status": "accepted", "msg": "你们已经是好友"}
    if f2 and f2.status == "accepted":
        return {"ok": True, "status": "accepted", "msg": "你们已经是好友"}
    if f1 and f1.status == "pending":
        return {"ok": True, "status": "pending", "msg": "申请已发送，等待对方同意"}
    if f2 and f2.status == "pending":
        # 对方已向我申请，直接成为好友
        f2.status = "accepted"
        db.commit()
        _notify(db, u.id, current.id, "friend_accept", "成为了好友")
        db.commit()
        return {"ok": True, "status": "accepted", "msg": "你们已成为好友"}
    # 新建（或被拒过则重用）
    if f1:
        f1.status = "pending"
    else:
        db.add(Friendship(user_id=current.id, friend_id=u.id, status="pending"))
    _notify(db, u.id, current.id, "friend_request", "请求加你为好友")
    db.commit()
    return {"ok": True, "status": "pending", "msg": "申请已发送，等待对方同意"}


@router.get("/friend/requests", response_model=List[FriendReqOut], summary="收到的好友申请")
def friend_requests(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = (db.query(Friendship)
            .filter(Friendship.friend_id == current.id, Friendship.status == "pending")
            .order_by(Friendship.created_at.desc()).all())
    out = []
    for f in rows:
        u = db.query(User).filter(User.id == f.user_id).first()
        if not u:
            continue
        lv = level_for_exp(u.exp or 0)
        out.append(FriendReqOut(
            id=f.id, user_id=u.id, username=u.username, nickname=u.nickname or "",
            avatar=u.avatar or "", level=lv, level_effect=level_effect(lv),
            message="请求加你为好友", created_at=f.created_at))
    return out


@router.post("/friend/accept/{friendship_id}", summary="同意好友申请")
def accept_friend(friendship_id: int, db: Session = Depends(get_db),
                  current: User = Depends(get_current_user)):
    f = db.query(Friendship).filter(Friendship.id == friendship_id).first()
    if not f or f.friend_id != current.id:
        raise HTTPException(status_code=404, detail="申请不存在")
    f.status = "accepted"
    _notify(db, f.user_id, current.id, "friend_accept", "同意了你的好友申请")
    db.commit()
    return {"ok": True, "msg": "已添加好友"}


@router.post("/friend/reject/{friendship_id}", summary="拒绝好友申请")
def reject_friend(friendship_id: int, db: Session = Depends(get_db),
                  current: User = Depends(get_current_user)):
    f = db.query(Friendship).filter(Friendship.id == friendship_id).first()
    if not f or f.friend_id != current.id:
        raise HTTPException(status_code=404, detail="申请不存在")
    f.status = "rejected"
    db.commit()
    return {"ok": True, "msg": "已拒绝"}


@router.get("/friends", response_model=List[FriendOut], summary="我的好友列表")
def list_friends(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = (db.query(Friendship)
            .filter(or_(
                and_(Friendship.user_id == current.id, Friendship.status == "accepted"),
                and_(Friendship.friend_id == current.id, Friendship.status == "accepted")))
            .all())
    out = []
    for f in rows:
        peer_id = f.friend_id if f.user_id == current.id else f.user_id
        u = db.query(User).filter(User.id == peer_id).first()
        if not u:
            continue
        lv = level_for_exp(u.exp or 0)
        unread = (db.query(ChatMessage)
                  .filter(ChatMessage.sender_id == u.id,
                          ChatMessage.receiver_id == current.id,
                          ChatMessage.is_read == False).count())  # noqa: E712
        out.append(FriendOut(
            id=u.id, username=u.username, nickname=u.nickname or "",
            avatar=u.avatar or "", level=lv, level_effect=level_effect(lv), unread=unread))
    return out


# ---------------- 私聊 ----------------
def _are_friends(db: Session, a: int, b: int) -> bool:
    f = (db.query(Friendship)
         .filter(Friendship.status == "accepted")
         .filter(or_(
             and_(Friendship.user_id == a, Friendship.friend_id == b),
             and_(Friendship.user_id == b, Friendship.friend_id == a)))
         .first())
    return f is not None


@router.post("/send", response_model=ChatMsgOut, summary="发一条私聊消息（文字/图片/视频）")
def send_message(data: ChatSendIn, db: Session = Depends(get_db),
                 current: User = Depends(get_current_user)):
    # 定位接收者
    peer = None
    if data.receiver_id:
        peer = db.query(User).filter(User.id == data.receiver_id).first()
    elif data.receiver_username:
        peer = db.query(User).filter(User.username == data.receiver_username.strip()).first()
    if not peer:
        raise HTTPException(status_code=404, detail="对方不存在")
    if peer.id == current.id:
        raise HTTPException(status_code=400, detail="不能给自己发消息")
    if not _are_friends(db, current.id, peer.id):
        raise HTTPException(status_code=403, detail="你们还不是好友，先加好友再聊")
    kind = (data.kind or "text").lower()
    if kind not in ("text", "image", "video"):
        raise HTTPException(status_code=400, detail="消息类型不对")
    content = (data.content or "").strip()
    if not content:
        raise HTTPException(status_code=400, detail="消息内容不能为空")
    # 聊天发视频：无等级限制（新版已全面开放）
    m = ChatMessage(sender_id=current.id, receiver_id=peer.id, kind=kind, content=content)
    db.add(m)
    grant_exp(db, current, "chat")
    db.commit()
    db.refresh(m)
    lv = level_for_exp(current.exp or 0)
    return ChatMsgOut(
        id=m.id, sender_id=m.sender_id, receiver_id=m.receiver_id, kind=m.kind,
        content=m.content, is_read=m.is_read, is_recalled=False, created_at=m.created_at,
        sender_name=current.nickname or current.username,
        sender_username=current.username,
        sender_avatar=current.avatar or "", sender_level=lv,
        sender_effect=level_effect(lv))


@router.get("/messages/{peer_id}", response_model=List[ChatMsgOut], summary="与某人的聊天记录")
def get_messages(peer_id: int, limit: int = Query(100, ge=1, le=300),
                 before_id: int = Query(0, ge=0),
                 db: Session = Depends(get_db),
                 current: User = Depends(get_current_user)):
    q = (db.query(ChatMessage)
         .filter(or_(
             and_(ChatMessage.sender_id == current.id, ChatMessage.receiver_id == peer_id),
             and_(ChatMessage.sender_id == peer_id, ChatMessage.receiver_id == current.id)))
         .filter(ChatMessage.id < before_id) if before_id > 0 else
         db.query(ChatMessage)
         .filter(or_(
             and_(ChatMessage.sender_id == current.id, ChatMessage.receiver_id == peer_id),
             and_(ChatMessage.sender_id == peer_id, ChatMessage.receiver_id == current.id))))
    rows = q.order_by(ChatMessage.id.desc()).limit(limit).all()
    rows = list(reversed(rows))
    ids = list({m.sender_id for m in rows})
    users = {u.id: u for u in db.query(User).filter(User.id.in_(ids)).all()} if ids else {}
    # 标记收到的消息已读
    (db.query(ChatMessage)
     .filter(ChatMessage.sender_id == peer_id, ChatMessage.receiver_id == current.id,
             ChatMessage.is_read == False)  # noqa: E712
     .update({ChatMessage.is_read: True}, synchronize_session=False))
    db.commit()
    out = []
    for m in rows:
        su = users.get(m.sender_id)
        lv = level_for_exp(su.exp or 0) if su else 1
        out.append(ChatMsgOut(
            id=m.id, sender_id=m.sender_id, receiver_id=m.receiver_id, kind=m.kind,
            content=("" if m.is_recalled else m.content), is_read=m.is_read,
            is_recalled=bool(m.is_recalled), created_at=m.created_at,
            sender_name=(su.nickname or su.username) if su else "",
            sender_username=(su.username or "") if su else "",
            sender_avatar=(su.avatar or "") if su else "",
            sender_level=lv, sender_effect=level_effect(lv),
            sender_name_color=(getattr(su, "name_color", "") or "") if su else "",
            sender_text_effect=(getattr(su, "text_effect", "") or "") if su else "",
            sender_is_vip=bool(su and su.vip_expire and su.vip_expire > __import__("datetime").datetime.utcnow())))
    return out


@router.get("/conversations", response_model=List[ChatConvOut], summary="会话列表")
def conversations(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    uid = current.id
    msgs = (db.query(ChatMessage)
            .filter(or_(ChatMessage.sender_id == uid, ChatMessage.receiver_id == uid))
            .order_by(ChatMessage.id.desc()).limit(500).all())
    seen = {}
    for m in msgs:
        peer = m.receiver_id if m.sender_id == uid else m.sender_id
        if peer not in seen:
            seen[peer] = m
    out = []
    for peer, m in seen.items():
        u = db.query(User).filter(User.id == peer).first()
        if not u:
            continue
        lv = level_for_exp(u.exp or 0)
        unread = (db.query(ChatMessage)
                  .filter(ChatMessage.sender_id == peer, ChatMessage.receiver_id == uid,
                          ChatMessage.is_read == False).count())  # noqa: E712
        out.append(ChatConvOut(
            peer_id=u.id, peer_username=u.username, peer_nickname=u.nickname or "",
            peer_avatar=u.avatar or "", peer_level=lv, peer_effect=level_effect(lv),
            last_kind=m.kind, last_content=m.content, last_at=m.created_at, unread=unread))
    out.sort(key=lambda c: (c.last_at is not None, c.last_at), reverse=True)
    return out


@router.post("/recall/{msg_id}", summary="撤回一条消息（仅本人）")
def recall_message(msg_id: int, db: Session = Depends(get_db),
                   current: User = Depends(get_current_user)):
    m = db.query(ChatMessage).filter(ChatMessage.id == msg_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="消息不存在")
    if m.sender_id != current.id:
        raise HTTPException(status_code=403, detail="只能撤回自己发的消息")
    if m.is_recalled:
        raise HTTPException(status_code=400, detail="该消息已撤回")
    # 撤回时间限制：仅限发送后 2 分钟内
    import datetime as _dt
    try:
        _sent = m.created_at
        if _sent is not None:
            _delta = (_dt.datetime.utcnow() - _sent).total_seconds()
            if _delta > 120:
                raise HTTPException(status_code=400, detail="超过 2 分钟的消息不能撤回")
    except HTTPException:
        raise
    except Exception:
        pass
    m.is_recalled = True
    db.commit()
    return {"ok": True}


@router.post("/forward/{msg_id}", summary="转发一条消息给好友")
def forward_message(msg_id: int, data: ChatSendIn, db: Session = Depends(get_db),
                    current: User = Depends(get_current_user)):
    src = db.query(ChatMessage).filter(ChatMessage.id == msg_id).first()
    if not src:
        raise HTTPException(status_code=404, detail="消息不存在")
    if src.is_recalled:
        raise HTTPException(status_code=400, detail="已撤回的消息不能转发")
    peer = None
    if data.receiver_id:
        peer = db.query(User).filter(User.id == data.receiver_id).first()
    elif data.receiver_username:
        peer = db.query(User).filter(User.username == data.receiver_username.strip()).first()
    if not peer:
        raise HTTPException(status_code=404, detail="对方不存在")
    if not _are_friends(db, current.id, peer.id):
        raise HTTPException(status_code=403, detail="你们还不是好友")
    if src.kind == "video":
        lv = level_for_exp(current.exp or 0)
        if not can_send_video(lv):
            raise HTTPException(status_code=403, detail="等级不足（需 Lv.3 以上）才能发送视频")
    m = ChatMessage(sender_id=current.id, receiver_id=peer.id, kind=src.kind, content=src.content)
    db.add(m)
    grant_exp(db, current, "chat")
    db.commit(); db.refresh(m)
    lv = level_for_exp(current.exp or 0)
    return ChatMsgOut(
        id=m.id, sender_id=m.sender_id, receiver_id=m.receiver_id, kind=m.kind,
        content=m.content, is_read=False, is_recalled=False, created_at=m.created_at,
        sender_name=current.nickname or current.username,
        sender_username=current.username,
        sender_avatar=current.avatar or "", sender_level=lv,
        sender_effect=level_effect(lv))


@router.get("/unread_total", summary="聊天总未读数")
def unread_total(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    n = (db.query(ChatMessage)
         .filter(ChatMessage.receiver_id == current.id, ChatMessage.is_read == False)  # noqa: E712
         .count())
    return {"unread": n}


def _notify(db: Session, user_id: int, actor_id: int, ntype: str, content: str = ""):
    if user_id == actor_id:
        return
    db.add(Notification(user_id=user_id, actor_id=actor_id, type=ntype, content=content[:255]))


def _dummy():
    pass
