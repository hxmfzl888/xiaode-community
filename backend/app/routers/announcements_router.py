# -*- coding: utf-8 -*-
"""小德日历 API - 公告路由：所有人可看，管理员可发/删"""
from typing import List

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User, Announcement
from ..schemas import AnnouncementIn, AnnouncementOut
from ..deps import get_current_user, get_current_admin

router = APIRouter(prefix="/api/announcements", tags=["公告"])


@router.get("", response_model=List[AnnouncementOut], summary="公告列表（所有人可见）")
def list_announcements(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    version: str = Query("", description="客户端版本号，用于筛选指定版本公告"),
    db: Session = Depends(get_db),
):
    q = db.query(Announcement)
    if version:
        # 返回：通用公告(空) 或 指定当前版本的公告
        q = q.filter((Announcement.target_version == "") | (Announcement.target_version == version))
    rows = q.order_by(Announcement.created_at.desc()).offset(offset).limit(limit).all()
    return rows


@router.post("", response_model=AnnouncementOut, summary="发布公告（仅管理员）")
def create_announcement(
    data: AnnouncementIn,
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    a = Announcement(title=data.title, content=data.content, target_version=getattr(data, "target_version", "") or "")
    db.add(a)
    db.commit()
    db.refresh(a)
    return a


@router.delete("/{ann_id}", summary="删除公告（仅管理员）")
def delete_announcement(
    ann_id: int,
    db: Session = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    a = db.query(Announcement).filter(Announcement.id == ann_id).first()
    if not a:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="公告不存在")
    db.delete(a)
    db.commit()
    return {"ok": True}