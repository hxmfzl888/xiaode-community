# -*- coding: utf-8 -*-
"""小德日历 API - 日记路由：写 / 列表 / 详情 / 改 / 删（每人只能看自己的）"""
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User, Diary
from ..schemas import DiaryCreateIn, DiaryUpdateIn, DiaryOut, DiaryListOut
from ..deps import get_current_user
from .task_router import grant_exp

router = APIRouter(prefix="/api/diaries", tags=["日记"])


def _diary_out(d: Diary) -> dict:
    """把数据库行转成 DiaryOut 需要的 dict（images 字符串 -> 列表）"""
    return {
        "id": d.id,
        "title": d.title,
        "content": d.content,
        "mood": d.mood,
        "weather": getattr(d, "weather", None),
        "diary_date": d.diary_date,
        "images": [x for x in (d.images or "").split(",") if x],
        "video": d.video or "",
        "created_at": d.created_at,
        "updated_at": d.updated_at,
    }


def _get_own_diary(diary_id: int, db: Session, current: User) -> Diary:
    d = db.query(Diary).filter(Diary.id == diary_id).first()
    if not d:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="日记不存在")
    if d.user_id != current.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="只能操作自己的日记")
    return d


@router.post("", response_model=DiaryOut, summary="写一篇日记")
def create_diary(
    data: DiaryCreateIn,
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    d = Diary(
        user_id=current.id,
        title=data.title or "",
        content=data.content,
        mood=data.mood,
        weather=getattr(data, "weather", None),
        diary_date=data.diary_date,
        images=",".join(data.images or []),
        video=data.video or "",
    )
    db.add(d)
    grant_exp(db, current, "diary")
    db.commit()
    db.refresh(d)
    return _diary_out(d)


@router.get("", response_model=DiaryListOut, summary="我的日记列表（按时间倒序）")
def list_diaries(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    keyword: str = Query(None, description="按标题/内容模糊搜索"),
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    q = db.query(Diary).filter(Diary.user_id == current.id)
    if keyword:
        like = f"%{keyword}%"
        q = q.filter((Diary.title.like(like)) | (Diary.content.like(like)))
    total = q.count()
    rows = q.order_by(Diary.created_at.desc()).offset(offset).limit(limit).all()
    return DiaryListOut(total=total, items=[_diary_out(r) for r in rows])


@router.get("/{diary_id}", response_model=DiaryOut, summary="查看一篇日记")
def get_diary(
    diary_id: int,
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    return _diary_out(_get_own_diary(diary_id, db, current))


@router.put("/{diary_id}", response_model=DiaryOut, summary="修改一篇日记")
def update_diary(
    diary_id: int,
    data: DiaryUpdateIn,
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    d = _get_own_diary(diary_id, db, current)
    if data.title is not None:
        d.title = data.title
    if data.content is not None:
        d.content = data.content
    if data.mood is not None:
        d.mood = data.mood
    if getattr(data, "weather", None) is not None:
        d.weather = data.weather
    if data.diary_date is not None:
        d.diary_date = data.diary_date
    if data.images is not None:
        d.images = ",".join(data.images)
    if data.video is not None:
        d.video = data.video
    db.commit()
    db.refresh(d)
    return _diary_out(d)


@router.delete("/{diary_id}", summary="删除一篇日记")
def delete_diary(
    diary_id: int,
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    d = _get_own_diary(diary_id, db, current)
    db.delete(d)
    db.commit()
    return {"ok": True}