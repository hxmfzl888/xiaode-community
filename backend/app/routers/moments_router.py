# -*- coding: utf-8 -*-
"""小德日历 API - 广场路由：发动态 / 广场(仅审核通过) / 我的 / 详情 / 删除 / 评论 / 点赞"""
from datetime import datetime
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from sqlalchemy import func
from ..database import get_db
from ..models import User, Moment, Comment, MomentLike, CommentLike, Notification, MomentFavorite, Friendship
from ..schemas import (
    CommentTreeOut,
    MomentCreateIn, MomentOut, MomentDetailOut, CommentOut, CommentCreateIn
)
from ..deps import get_current_user, get_current_user_optional
from .task_router import grant_exp
from ..levels import level_for_exp, level_effect, moment_image_limit, moment_video_limit

router = APIRouter(prefix="/api/moments", tags=["广场"])


def _images_list(raw: str) -> List[str]:
    if not raw:
        return []
    return [x for x in raw.split(",") if x]


def _moment_like_counts(db: Session, moment_ids):
    if not moment_ids:
        return {}
    rows = (db.query(MomentLike.moment_id, func.count(MomentLike.id))
            .filter(MomentLike.moment_id.in_(moment_ids))
            .group_by(MomentLike.moment_id).all())
    return {mid: cnt for mid, cnt in rows}


def _my_moment_likes(db: Session, user_id, moment_ids):
    if not user_id or not moment_ids:
        return set()
    rows = (db.query(MomentLike.moment_id)
            .filter(MomentLike.user_id == user_id, MomentLike.moment_id.in_(moment_ids)).all())
    return {r[0] for r in rows}


def _comment_like_counts(db: Session, comment_ids):
    if not comment_ids:
        return {}
    rows = (db.query(CommentLike.comment_id, func.count(CommentLike.id))
            .filter(CommentLike.comment_id.in_(comment_ids))
            .group_by(CommentLike.comment_id).all())
    return {cid: cnt for cid, cnt in rows}


def _my_comment_likes(db: Session, user_id, comment_ids):
    if not user_id or not comment_ids:
        return set()
    rows = (db.query(CommentLike.comment_id)
            .filter(CommentLike.user_id == user_id, CommentLike.comment_id.in_(comment_ids)).all())
    return {r[0] for r in rows}


def _my_channel_ids(db, uid):
    if not uid:
        return set()
    try:
        from ..models import ChannelMember
        rows = db.query(ChannelMember).filter(ChannelMember.user_id == uid).all()
        return set(r.channel_id for r in rows)
    except Exception:
        return set()


def _to_out(m, comment_count=None, like_count=0, liked=False, favorited=False):
    if comment_count is None:
        comment_count = len(m.comments) if m.comments is not None else 0
    return MomentOut(
        channel_id=(m.channel_id or 0), channel_name="",
        is_vip=(m.author.vip_expire is not None and m.author.vip_expire > __import__("datetime").datetime.utcnow()) if m.author else False,
        id=m.id, title=(getattr(m, "title", "") or ""), content=m.content, images=_images_list(m.images),
        videos=_images_list(getattr(m, "videos", "")),
        lang=(getattr(m, "lang", "") or ""),
        allow_download=bool(getattr(m, "allow_download", 0)),
        status=m.status, created_at=m.created_at, user_id=m.user_id,
        nickname=m.author.nickname if m.author else "",
        username=m.author.username if m.author else "",
        avatar=m.author.avatar if m.author else "",
        comment_count=comment_count, like_count=like_count, liked=liked,
        level=level_for_exp(m.author.exp or 0) if m.author else 1,
        level_effect=level_effect(level_for_exp(m.author.exp or 0)) if m.author else "normal",
        name_color=(getattr(m.author, "name_color", "") or "") if m.author else "",
        text_effect=(getattr(m.author, "text_effect", "") or "") if m.author else "",
        bio=(getattr(m.author, "bio", "") or "") if m.author else "",
        tags=[t for t in (getattr(m, "tags", "") or "").split(",") if t],
        # FAV_VIEW_API_V1
        view_count=int(getattr(m, "view_count", 0) or 0),
        favorited=bool(favorited))


def _comment_out(c, like_count=0, liked=False):
    return CommentOut(
        id=c.id, content=c.content, created_at=c.created_at, user_id=c.user_id,
        reply_to_id=getattr(c, "reply_to_id", None),
        reply_to_nickname=(getattr(c, "reply_to_nickname", "") or ""),
        nickname=c.author.nickname if c.author else "",
        username=c.author.username if c.author else "",
        avatar=c.author.avatar if c.author else "",
        parent_id=c.parent_id, lang=(getattr(c, "lang", "") or ""),
        like_count=like_count, liked=liked,
        level=level_for_exp(c.author.exp or 0) if c.author else 1,
        level_effect=level_effect(level_for_exp(c.author.exp or 0)) if c.author else "normal",
        name_color=(getattr(c.author, "name_color", "") or "") if c.author else "",
        text_effect=(getattr(c.author, "text_effect", "") or "") if c.author else "")


def _add_notify(db, user_id, actor_id, ntype, moment_id=None, comment_id=None, content=""):
    if user_id == actor_id:
        return
    db.add(Notification(user_id=user_id, actor_id=actor_id, type=ntype,
                        moment_id=moment_id, comment_id=comment_id,
                        content=(content or "")[:255]))


@router.post("", response_model=MomentOut, summary="发布一条动态（默认待审核）")
def create_moment(data: MomentCreateIn, db: Session = Depends(get_db),
                  current: User = Depends(get_current_user)):
    if not (data.content or data.images or data.videos):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="内容、图片或视频至少填一项")
    lv = level_for_exp(current.exp or 0)
    max_img = moment_image_limit(lv)
    if len(data.images) > max_img:
        raise HTTPException(status_code=400,
            detail="当前 Lv.%d 最多可发 %d 张图片（升级可解锁更多）" % (lv, max_img))
    # 视频权限：会员可发 1~2 个，非会员 0
    from datetime import datetime as _dt
    _vip = current.vip_expire is not None and current.vip_expire > _dt.utcnow()
    if len(data.videos) > 0 and not _vip:
        raise HTTPException(status_code=400, detail="发布视频为会员专属功能，开通会员后可用")
    if len(data.videos) > 2:
        raise HTTPException(status_code=400, detail="最多可发 2 个视频")
    cid = getattr(data, "channel_id", 0) or 0
    if cid > 0 and cid not in _my_channel_ids(db, current.id):
        raise HTTPException(status_code=400, detail="请先加入该频道再发帖")
    ttl = (getattr(data, "title", "") or "").strip()
    # 标题可选：不填时自动取正文前 20 字作为标题
    if not ttl:
        _plain = (data.content or "").strip()
        ttl = _plain[:20] if _plain else "无标题"
    ttl = ttl[:100]
    _tags = ",".join([t for t in (getattr(data, "tags", None) or []) if t in ("教程", "求助", "分享", "讨论")][:3])
    m = Moment(user_id=current.id, title=ttl, content=data.content or "",
               images=",".join(data.images), videos=",".join(data.videos),
               channel_id=cid, lang=(getattr(data, "lang", "") or "")[:16], status="pending",
               tags=_tags,
               allow_download=(1 if getattr(data, "allow_download", False) else 0))
    db.add(m)
    grant_exp(db, current, "post")
    db.commit(); db.refresh(m)
    return _to_out(m, 0, 0, False)


# ==================== 推荐机制 RECO_V1 ====================
import random as _random
from datetime import timedelta as _timedelta

# 新鲜度窗口：多少天内算"新帖"
FRESH_DAYS = 10
# 新帖与老帖混排比例：每 N 条新帖插入 1 条老帖
MIX_RATIO = 4


def _reco_order(db: Session, base_q, current_id, offset: int, limit: int,
                page_seed: int = 0):
    # 注意：page_seed 保留只为向后兼容，不参与排序（否则分页会错乱）
    """
    推荐排序：新帖优先 + 混入老帖 + 轮转曝光

    规则：
      1. 10 天内帖子按时间倒序（最新在前）
      2. 每 MIX_RATIO 条新帖后插 1 条老帖
      3. 老帖顺序按 (用户ID + 日期 + 页码) 打乱，每天/每页换一批
      4. 新+老总量不够时，剩余的自动补满
      5. 保证翻到底能看到全部帖子（轮转而非永久沉底）
    """
    now = datetime.utcnow()
    cutoff = now - _timedelta(days=FRESH_DAYS)

    # RECO_V2: 关注的人优先 + 热度加权
    # 1) 查出我关注的人
    follow_ids = set()
    if current_id:
        try:
            rows = (db.query(Friendship.friend_id)
                    .filter(Friendship.user_id == current_id,
                            Friendship.status == "accepted").all())
            follow_ids = {r[0] for r in rows}
        except Exception:
            follow_ids = set()

    # 2) 热度 = 点赞数*3 + 评论数*5 + 浏览数*0.5，24h 内加成
    from sqlalchemy import func as _f
    like_cnt = dict(db.query(MomentLike.moment_id, _f.count(MomentLike.id))
                    .group_by(MomentLike.moment_id).all())
    cmt_cnt = dict(db.query(Comment.moment_id, _f.count(Comment.id))
                   .group_by(Comment.moment_id).all())

    def _hot(mid, created_at):
        h = like_cnt.get(mid, 0) * 3.0 + cmt_cnt.get(mid, 0) * 5.0
        if created_at:
            age_h = max(0.0, (now - created_at).total_seconds() / 3600.0)
            # 24 小时内 2 倍加成，之后指数衰减
            h *= 2.0 if age_h <= 24 else max(0.3, 1.0 / (1.0 + (age_h - 24) / 72.0))
        return h

    # 分别取新帖 / 老帖
    fresh_q = base_q.filter(Moment.created_at >= cutoff)
    old_q = base_q.filter(Moment.created_at < cutoff)

    fresh_rows = fresh_q.with_entities(Moment.id, Moment.user_id, Moment.created_at).all()
    old_rows = old_q.with_entities(Moment.id, Moment.user_id, Moment.created_at).all()

    # 3) 新帖内部：关注的人优先，其次按热度，再次按时间
    fresh_scored = []
    for mid, uid_, c_at in fresh_rows:
        is_follow = 1 if uid_ in follow_ids else 0
        fresh_scored.append((is_follow, _hot(mid, c_at), -(mid), mid))
    fresh_scored.sort(reverse=True)
    fresh_ids = [x[3] for x in fresh_scored]

    old_ids = [r[0] for r in old_rows]

    # 老帖轮转打乱：种子只用 用户ID + 日期（不含页码！）
    # 这样整个列表顺序在一天内固定，分页才不会重复/遗漏；换天自动换一批
    seed = (current_id or 0) * 31 + (now.toordinal()) * 7919
    rnd = _random.Random(seed)
    rnd.shuffle(old_ids)

    # 混合编排
    merged = []
    fi, oi = 0, 0
    need = offset + limit          # 需要生成到第几条
    while len(merged) < need:
        got = False
        # 每轮先放 MIX_RATIO 条新帖
        for _ in range(MIX_RATIO):
            if fi < len(fresh_ids) and len(merged) < need:
                merged.append(fresh_ids[fi]); fi += 1; got = True
        # 再插 1 条老帖
        if oi < len(old_ids) and len(merged) < need:
            merged.append(old_ids[oi]); oi += 1; got = True
        if not got:
            break

    page_ids = merged[offset:offset + limit]
    if not page_ids:
        return []

    # 按 page_ids 顺序取回完整对象
    rows = db.query(Moment).filter(Moment.id.in_(page_ids)).all()
    order = {mid: i for i, mid in enumerate(page_ids)}
    rows.sort(key=lambda m: order.get(m.id, 99999))
    return rows


@router.get("", response_model=List[MomentOut], summary="广场（仅审核通过的，所有人可见，倒序）")
def list_moments(limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0),
                 tag: str = Query("", description="按标签筛选（教程/求助/分享/讨论），空=全部"),
                 db: Session = Depends(get_db),
                 current: Optional[User] = Depends(get_current_user_optional)):
    mych = _my_channel_ids(db, current.id if current else None)
    from sqlalchemy import or_ as _or, and_ as _and
    # REJECTFIX_V1: 自己的「待审」帖子可见，但「已驳回」的不可见
    # 之前是「自己的帖子不论状态都可见」，导致被驳回的帖子仍出现在列表里，
    # 而详情接口只认 approved，点开就报「不存在或未通过审核」。
    if current:
        q = db.query(Moment).filter(
            _or(
                Moment.status == "approved",
                _and(Moment.user_id == current.id, Moment.status == "pending"),
            )
        )
    else:
        q = db.query(Moment).filter(Moment.status == "approved")
    if mych:
        q = q.filter(_or(Moment.channel_id == 0, Moment.channel_id.in_(list(mych))))
    else:
        q = q.filter(Moment.channel_id == 0)
    # v2.1: 按标签筛选
    _tag = (tag or "").strip()
    if _tag:
        q = q.filter(Moment.tags.like("%" + _tag + "%"))
    # RECO_V1: 推荐排序（新帖优先 + 混老帖 + 轮转曝光）
    page_seed = offset // max(limit, 1)
    rows = _reco_order(db, q, current.id if current else None, offset, limit, page_seed)
    ids = [m.id for m in rows]
    counts = _moment_like_counts(db, ids)
    mylikes = _my_moment_likes(db, current.id if current else None, ids)
    return [_to_out(m, None, counts.get(m.id, 0), m.id in mylikes) for m in rows]


@router.get("/mine", response_model=List[MomentOut], summary="我发布的动态")
def my_moments(limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0),
               db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = (db.query(Moment).filter(Moment.user_id == current.id)
            .order_by(Moment.created_at.desc()).offset(offset).limit(limit).all())
    ids = [m.id for m in rows]
    counts = _moment_like_counts(db, ids)
    mylikes = _my_moment_likes(db, current.id, ids)
    return [_to_out(m, None, counts.get(m.id, 0), m.id in mylikes) for m in rows]


@router.get("/liked", response_model=List[MomentOut], summary="我点赞过的动态")
def liked_moments(limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0),
                  db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    likes = (db.query(MomentLike).filter(MomentLike.user_id == current.id)
             .order_by(MomentLike.created_at.desc()).offset(offset).limit(limit).all())
    ids = [lk.moment_id for lk in likes]
    rows = db.query(Moment).filter(Moment.id.in_(ids)).all() if ids else []
    order = {mid: i for i, mid in enumerate(ids)}
    rows.sort(key=lambda m: order.get(m.id, 9999))
    counts = _moment_like_counts(db, ids)
    mylikes = _my_moment_likes(db, current.id, ids)
    return [_to_out(m, None, counts.get(m.id, 0), m.id in mylikes) for m in rows]



# ============================================================
# FAV_VIEW_API_V1: 收藏接口
# 注意：/favorites 必须定义在 /{moment_id} 之前，否则会被当作 moment_id 解析
# ============================================================

@router.get("/favorites", response_model=List[MomentOut], summary="我收藏的帖子")
def my_favorites(limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0),
                 db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    favs = (db.query(MomentFavorite)
            .filter(MomentFavorite.user_id == current.id)
            .order_by(MomentFavorite.created_at.desc())
            .offset(offset).limit(limit).all())
    ids = [f.moment_id for f in favs]
    if not ids:
        return []
    rows = db.query(Moment).filter(Moment.id.in_(ids)).all()
    byid = {m.id: m for m in rows}
    ordered = []
    for mid in ids:
        m = byid.get(mid)
        if not m:
            continue
        if m.status != "approved" and m.user_id != current.id:
            continue
        ordered.append(m)
    oids = [m.id for m in ordered]
    counts = _moment_like_counts(db, oids) if oids else {}
    mylikes = _my_moment_likes(db, current.id, oids) if oids else set()
    return [_to_out(m, None, counts.get(m.id, 0), m.id in mylikes, favorited=True)
            for m in ordered]


@router.post("/{moment_id}/favorite", summary="收藏帖子（幂等）")
def fav_moment(moment_id: int, db: Session = Depends(get_db),
               current: User = Depends(get_current_user)):
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="动态不存在")
    if m.status != "approved" and m.user_id != current.id:
        raise HTTPException(status_code=404, detail="动态不存在或未通过审核")
    exists = db.query(MomentFavorite).filter(
        MomentFavorite.moment_id == moment_id,
        MomentFavorite.user_id == current.id).first()
    if exists:
        return {"ok": True, "favorited": True, "changed": False}
    try:
        db.add(MomentFavorite(moment_id=moment_id, user_id=current.id))
        db.commit()
    except Exception:
        db.rollback()
        return {"ok": True, "favorited": True, "changed": False}
    return {"ok": True, "favorited": True, "changed": True}


@router.delete("/{moment_id}/favorite", summary="取消收藏（幂等）")
def unfav_moment(moment_id: int, db: Session = Depends(get_db),
                 current: User = Depends(get_current_user)):
    try:
        n = db.query(MomentFavorite).filter(
            MomentFavorite.moment_id == moment_id,
            MomentFavorite.user_id == current.id
        ).delete(synchronize_session=False)
        db.commit()
    except Exception:
        db.rollback()
        n = 0
    return {"ok": True, "favorited": False, "changed": n > 0}


@router.get("/{moment_id}/favorite", summary="查询是否已收藏")
def get_fav_moment(moment_id: int, db: Session = Depends(get_db),
                   current: User = Depends(get_current_user)):
    exists = db.query(MomentFavorite).filter(
        MomentFavorite.moment_id == moment_id,
        MomentFavorite.user_id == current.id).first() is not None
    return {"favorited": bool(exists)}


@router.get("/channel/{cid}", response_model=List[MomentOut], summary="频道帖子")
def channel_moments(cid: int, limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0),
                    db: Session = Depends(get_db),
                    current: Optional[User] = Depends(get_current_user_optional)):
    if cid not in _my_channel_ids(db, current.id if current else None):
        raise HTTPException(status_code=403, detail="请先加入该频道")
    rows = (db.query(Moment).filter(Moment.status == "approved", Moment.channel_id == cid)
            .order_by(Moment.created_at.desc()).offset(offset).limit(limit).all())
    ids = [m.id for m in rows]
    counts = _moment_like_counts(db, ids)
    mylikes = _my_moment_likes(db, current.id if current else None, ids)
    return [_to_out(m, None, counts.get(m.id, 0), m.id in mylikes) for m in rows]


@router.get("/{moment_id}", response_model=MomentDetailOut, summary="动态详情（含评论）")
def get_moment(moment_id: int, db: Session = Depends(get_db),
               current: Optional[User] = Depends(get_current_user_optional)):
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="动态不存在")
    if m.status != "approved":
        # REJECTFIX_V1:
        # - 作者本人看自己的「待审」帖子：允许，返回带 status 的详情（前端可显示"审核中"）
        # - 作者本人看自己的「已驳回」帖子：明确告知已驳回，而不是含糊的"不存在"
        # - 其他任何人：一律 404，避免泄露
        if current and current.id == m.user_id:
            if m.status == "rejected":
                raise HTTPException(status_code=403, detail="该帖子未通过审核，已不予展示")
            # pending：放行给作者自己看
        else:
            raise HTTPException(status_code=404, detail="动态不存在或未通过审核")
    # FAV_VIEW_API_V1: 浏览量 +1（作者本人查看不计数，避免自己刷量）
    try:
        if not (current and current.id == m.user_id):
            m.view_count = int(getattr(m, "view_count", 0) or 0) + 1
            db.commit()
    except Exception:
        db.rollback()
    cs = (db.query(Comment).filter(Comment.moment_id == moment_id)
          .order_by(Comment.created_at.asc()).all())
    cids = [c.id for c in cs]
    likecounts = _moment_like_counts(db, [moment_id])
    mylike = _my_moment_likes(db, current.id if current else None, [moment_id])
    # FAV_VIEW_API_V1: 当前用户是否已收藏
    _isfav = False
    if current:
        try:
            _isfav = db.query(MomentFavorite).filter(
                MomentFavorite.moment_id == m.id,
                MomentFavorite.user_id == current.id).first() is not None
        except Exception:
            _isfav = False
    ccnt = _comment_like_counts(db, cids)
    cmine = _my_comment_likes(db, current.id if current else None, cids)
    base = _to_out(m, len(cs), likecounts.get(moment_id, 0), moment_id in mylike, favorited=_isfav)
    outs = {c.id: _comment_out(c, ccnt.get(c.id, 0), c.id in cmine) for c in cs}
    tree = {c.id: CommentTreeOut(**outs[c.id].model_dump(), replies=[]) for c in cs if not c.parent_id}
    for c in cs:
        if c.parent_id and c.parent_id in tree:
            tree[c.parent_id].replies.append(CommentTreeOut(**outs[c.id].model_dump(), replies=[]))
    return MomentDetailOut(**base.model_dump(), comments=[tree[k] for k in tree])


@router.delete("/{moment_id}", summary="删除我发的一条动态")
def delete_moment(moment_id: int, db: Session = Depends(get_db),
                  current: User = Depends(get_current_user)):
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="动态不存在")
    if m.user_id != current.id and not current.is_admin:
        raise HTTPException(status_code=403, detail="只能删除自己的动态")
    db.delete(m); db.commit()
    return {"ok": True}


@router.post("/{moment_id}/comments", response_model=CommentOut, summary="评论一条动态")
def create_comment(moment_id: int, data: CommentCreateIn, db: Session = Depends(get_db),
                   current: User = Depends(get_current_user)):
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="动态不存在")
    if m.status != "approved":
        raise HTTPException(status_code=400, detail="该动态不可评论")
    parent_id = getattr(data, "parent_id", None)
    reply_to_id = getattr(data, "reply_to_id", None)
    # 保持两级树：parent_id 始终归到顶层评论；reply_to_id 记录真正被回复的那条
    root_id = parent_id
    reply_nick = ""
    if reply_to_id:
        rc = db.query(Comment).filter(Comment.id == reply_to_id).first()
        if rc:
            # 若被回复的是楼中楼，则 root 取其 parent_id
            root_id = rc.parent_id or rc.id
            reply_nick = (rc.author.nickname or rc.author.username or "") if rc.author else ""
    c = Comment(moment_id=moment_id, user_id=current.id, content=data.content, parent_id=root_id,
                reply_to_id=reply_to_id, reply_to_nickname=reply_nick,
                lang=(getattr(data, "lang", "") or "")[:16])
    db.add(c); db.commit(); db.refresh(c)
    _add_notify(db, m.user_id, current.id, "comment", moment_id=moment_id,
                comment_id=c.id, content=data.content)
    if reply_to_id:
        rc2 = db.query(Comment).filter(Comment.id == reply_to_id).first()
        if rc2:
            _add_notify(db, rc2.user_id, current.id, "reply", moment_id=moment_id,
                        comment_id=c.id, content=data.content)
    grant_exp(db, current, "comment")
    db.commit()
    return _comment_out(c, 0, False)


@router.get("/{moment_id}/comments", summary="查看评论（默认两级树；flat=1 平铺）")
def list_comments(moment_id: int, flat: bool = Query(False),
                  db: Session = Depends(get_db),
                  current: Optional[User] = Depends(get_current_user_optional)):
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="动态不存在")
    cs = (db.query(Comment).filter(Comment.moment_id == moment_id)
          .order_by(Comment.created_at.asc()).all())
    cids = [c.id for c in cs]
    ccnt = _comment_like_counts(db, cids)
    cmine = _my_comment_likes(db, current.id if current else None, cids)
    outs = {c.id: _comment_out(c, ccnt.get(c.id, 0), c.id in cmine) for c in cs}
    if flat:
        return [outs[c.id] for c in cs]
    # 组两级树：顶层 + replies
    tree = {c.id: CommentTreeOut(**outs[c.id].model_dump(), replies=[]) for c in cs if not c.parent_id}
    for c in cs:
        if c.parent_id and c.parent_id in tree:
            tree[c.parent_id].replies.append(CommentTreeOut(**outs[c.id].model_dump(), replies=[]))
    return [tree[k] for k in tree]


@router.delete("/comments/{comment_id}", summary="删除一条评论")
def delete_comment(comment_id: int, db: Session = Depends(get_db),
                   current: User = Depends(get_current_user)):
    c = db.query(Comment).filter(Comment.id == comment_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="评论不存在")
    if c.user_id != current.id and not current.is_admin:
        raise HTTPException(status_code=403, detail="只能删除自己的评论")
    db.delete(c); db.commit()
    return {"ok": True}


@router.post("/{moment_id}/like", summary="点赞/取消点赞 一条动态（切换）")
def toggle_moment_like(moment_id: int, db: Session = Depends(get_db),
                       current: User = Depends(get_current_user)):
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="动态不存在")
    exist = (db.query(MomentLike).filter(MomentLike.moment_id == moment_id,
             MomentLike.user_id == current.id).first())
    if exist:
        db.delete(exist); db.commit(); liked = False
    else:
        db.add(MomentLike(moment_id=moment_id, user_id=current.id))
        _add_notify(db, m.user_id, current.id, "moment_like",
                    moment_id=moment_id, content="赞了你的动态")
        grant_exp(db, current, "like")
        db.commit(); liked = True
    cnt = db.query(MomentLike).filter(MomentLike.moment_id == moment_id).count()
    return {"liked": liked, "like_count": cnt}


@router.post("/comments/{comment_id}/like", summary="点赞/取消点赞 一条评论（切换）")
def toggle_comment_like(comment_id: int, db: Session = Depends(get_db),
                        current: User = Depends(get_current_user)):
    c = db.query(Comment).filter(Comment.id == comment_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="评论不存在")
    exist = (db.query(CommentLike).filter(CommentLike.comment_id == comment_id,
             CommentLike.user_id == current.id).first())
    if exist:
        db.delete(exist); db.commit(); liked = False
    else:
        db.add(CommentLike(comment_id=comment_id, user_id=current.id))
        _add_notify(db, c.user_id, current.id, "comment_like", moment_id=c.moment_id,
                    comment_id=c.id, content="赞了你的评论")
        db.commit(); liked = True
    cnt = db.query(CommentLike).filter(CommentLike.comment_id == comment_id).count()
    return {"liked": liked, "like_count": cnt}
