# -*- coding: utf-8 -*-
"""小德助手 API - 等级/任务模块：签到、任务列表、发经验、排行榜"""
import datetime
import random
from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from sqlalchemy import func
from ..database import get_db
from ..models import User, TaskLog, Follow
from ..schemas import TaskListOut, TaskItemOut, SignResultOut, UserLevelOut, RankOut, RankItemOut
from ..deps import get_current_user, get_current_user_optional
from ..levels import TASKS, level_for_exp, level_effect, exp_for_level, can_send_video

router = APIRouter(prefix="/api/tasks", tags=["等级任务"])


def _today():
    return datetime.date.today().strftime("%Y-%m-%d")


def _sync_level(db: Session, u: User):
    """根据 exp 同步 level 字段"""
    lv = level_for_exp(u.exp or 0)
    if u.level != lv:
        u.level = lv
    return lv


def grant_exp(db: Session, u: User, task_key: str, force_exp=None, force_limit=None):
    """给用户发放任务经验（受每日上限控制）。返回实际获得经验。"""
    cfg = TASKS.get(task_key)
    if not cfg and force_exp is None:
        return 0
    days_limit = force_limit if force_limit is not None else (cfg[2] if cfg else 0)
    exp_gain = force_exp if force_exp is not None else (cfg[1] if cfg else 0)
    today = _today()
    if days_limit and days_limit > 0:
        cnt = (db.query(TaskLog)
               .filter(TaskLog.user_id == u.id, TaskLog.task_key == task_key)
               .filter(func.date(TaskLog.created_at) == today).count())
        if cnt >= days_limit:
            return 0
    u.exp = (u.exp or 0) + exp_gain
    _sync_level(db, u)
    db.add(TaskLog(user_id=u.id, task_key=task_key, exp=exp_gain))
    return exp_gain


def user_level_out(u: User, db: Session) -> UserLevelOut:
    lv = level_for_exp(u.exp or 0)
    return UserLevelOut(
        id=u.id, username=u.username, nickname=u.nickname or "", avatar=u.avatar or "",
        exp=u.exp or 0, level=lv, level_effect=level_effect(lv),
        name_color=getattr(u, "name_color", "") or "",
        text_effect=getattr(u, "text_effect", "") or "",
        next_exp=exp_for_level(lv + 1), can_video=can_send_video(lv))


@router.get("/me", response_model=TaskListOut, summary="我的等级与任务列表")
def my_tasks(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    lv = _sync_level(db, current)
    db.commit()
    today = _today()
    items = []
    for key, cfg in TASKS.items():
        name, exp, limit, desc = cfg
        done = (db.query(TaskLog)
                .filter(TaskLog.user_id == current.id, TaskLog.task_key == key)
                .filter(func.date(TaskLog.created_at) == today).count())
        items.append(TaskItemOut(key=key, name=name, exp=exp, limit=limit,
                                 done=done, desc=desc))
    return TaskListOut(
        exp=current.exp or 0, level=lv, level_effect=level_effect(lv),
        next_exp=exp_for_level(lv + 1), cur_level_exp=exp_for_level(lv),
        signed_today=(current.last_sign_date == today),
        can_video=can_send_video(lv), tasks=items)


def _cents_of(u: User) -> int:
    cents = getattr(u, "balance_cents", None)
    if not cents:
        cents = (u.balance or 0) * 100
    return int(cents or 0)


def _set_cents(u: User, cents: int):
    cents = int(cents)
    if cents < 0:
        cents = 0
    u.balance_cents = cents
    u.balance = cents // 100


def _add_cents(db: Session, u: User, delta_cents: int, kind: str, detail: str = ""):
    from ..models import BalanceLog
    before = _cents_of(u)
    after = before + int(delta_cents)
    if after < 0:
        after = 0
    _set_cents(u, after)
    try:
        db.add(BalanceLog(user_id=u.id, delta=int(delta_cents), kind=kind,
                          detail=detail or "", moment_id=0, balance_after=after))
    except Exception:
        pass
    return after


def _rand_sign_coin_cents(is_vip: bool) -> int:
    lo = 30 if is_vip else 10
    hi = 150 if is_vip else 100
    return lo + random.randrange(hi - lo + 1)


@router.post("/sign", response_model=SignResultOut, summary="每日签到")
def sign_in(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    today = _today()
    if current.last_sign_date == today:
        lv = level_for_exp(current.exp or 0)
        cents = _cents_of(current)
        return SignResultOut(ok=False, gained=0, exp=current.exp or 0, level=lv,
                             level_effect=level_effect(lv), msg="今天已经签过啦",
                             coin=0.0, balance=cents / 100.0, balance_cents=cents)
    current.last_sign_date = today
    _is_vip = current.vip_expire is not None and current.vip_expire > datetime.datetime.utcnow()
    gained = grant_exp(db, current, "sign", force_exp=(100 if _is_vip else 20), force_limit=0)
    _sync_level(db, current)
    coin_cents = _rand_sign_coin_cents(bool(_is_vip))
    after_cents = _add_cents(db, current, coin_cents, "sign", "每日签到奖励")
    db.commit()
    lv = level_for_exp(current.exp or 0)
    coin_val = coin_cents / 100.0
    msg = "签到成功，+%d 经验，+%.2f 金币" % (gained, coin_val)
    return SignResultOut(ok=True, gained=gained, exp=current.exp or 0, level=lv,
                         level_effect=level_effect(lv), msg=msg,
                         coin=coin_val, balance=after_cents / 100.0,
                         balance_cents=after_cents)


@router.get("/me/level", response_model=UserLevelOut, summary="我的等级信息")
def my_level(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    return user_level_out(current, db)


# ==================== 排行榜（v2.1） ====================

def _range_start(rng: str):
    """返回时间范围起点（本地日期）。all -> None。"""
    today = datetime.date.today()
    if rng == "day":
        return today
    if rng == "week":
        # 周一为一周起点
        return today - datetime.timedelta(days=today.weekday())
    return None


def _followed_ids(db: Session, uid):
    if not uid:
        return set()
    rows = db.query(Follow.followee_id).filter(Follow.follower_id == uid).all()
    return {r[0] for r in rows}


@router.get("/rank", response_model=RankOut, summary="排行榜：签到榜/发帖榜 × 总榜/日榜/周榜")
def rank(
    type: str = Query("sign", description="sign=签到榜 / post=发帖榜"),
    range: str = Query("all", description="all=总榜 / day=日榜 / week=周榜"),
    limit: int = Query(50, ge=1, le=100),
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user_optional),
):
    _type = type if type in ("sign", "post", "rich") else "sign"
    _range = range if range in ("all", "day", "week") else "all"
    start = _range_start(_range)

    if _type == "sign":
        # 签到榜：按 TaskLog 中 task_key='sign' 的记录聚合
        q = db.query(TaskLog.user_id, func.count(TaskLog.id).label("score")).filter(
            TaskLog.task_key == "sign")
        if start is not None:
            q = q.filter(func.date(TaskLog.created_at) >= start.strftime("%Y-%m-%d"))
        rows = q.group_by(TaskLog.user_id).order_by(func.count(TaskLog.id).desc()).all()
    elif _type == "rich":
        # 富豪榜：按金币余额排序（单位：分）
        from ..models import BalanceLog
        if start is not None:
            # 日榜/周榜：按时间段内金币净收入（流水求和）排名
            q = db.query(
                BalanceLog.user_id,
                func.sum(BalanceLog.delta).label("score"),
            ).filter(func.date(BalanceLog.created_at) >= start.strftime("%Y-%m-%d"))
            q = q.group_by(BalanceLog.user_id).having(func.sum(BalanceLog.delta) > 0)
            raw = q.order_by(func.sum(BalanceLog.delta).desc()).all()
            rows = [(uid, int(sc or 0) / 100.0) for uid, sc in raw]
        else:
            # 总榜：按当前余额
            q = db.query(User.id, User.balance_cents).filter(User.balance_cents > 0)
            raw = q.order_by(User.balance_cents.desc()).all()
            rows = [(uid, int(sc or 0) / 100.0) for uid, sc in raw]
    else:
        # 发帖榜：按 moments 表中审核通过的帖子聚合（发帖越多越靠前）
        from ..models import Moment
        q = db.query(Moment.user_id, func.count(Moment.id).label("score")).filter(
            Moment.status == "approved")
        if start is not None:
            q = q.filter(func.date(Moment.created_at) >= start.strftime("%Y-%m-%d"))
        rows = q.group_by(Moment.user_id).order_by(func.count(Moment.id).desc()).all()

    if _type == "rich":
        scores = [(uid, round(float(sc or 0), 2)) for uid, sc in rows if uid]
    else:
        scores = [(uid, int(sc or 0)) for uid, sc in rows if uid]
    # 我的分数
    my_score = 0
    my_rank = 0
    if current:
        for i, (uid, sc) in enumerate(scores, start=1):
            if uid == current.id:
                my_score = sc
                my_rank = i
                break

    top = scores[:limit]
    uids = [uid for uid, _ in top]
    users = {}
    if uids:
        for u in db.query(User).filter(User.id.in_(uids)).all():
            users[u.id] = u
    follows = _followed_ids(db, current.id if current else None)
    now = datetime.datetime.utcnow()

    items = []
    for i, (uid, sc) in enumerate(top, start=1):
        u = users.get(uid)
        if not u:
            continue
        lv = level_for_exp(u.exp or 0)
        items.append(RankItemOut(
            rank=i, user_id=uid, username=u.username, nickname=u.nickname or "",
            avatar=u.avatar or "", level=lv, level_effect=level_effect(lv),
            name_color=getattr(u, "name_color", "") or "",
            text_effect=getattr(u, "text_effect", "") or "",
            is_vip=(u.vip_expire is not None and u.vip_expire > now),
            score=sc, is_me=(current is not None and uid == current.id),
            followed=(uid in follows)))

    return RankOut(type=_type, range=_range, updated_at=now,
                   my_rank=my_rank, my_score=my_score, items=items)
