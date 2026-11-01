# -*- coding: utf-8 -*-
"""小德社区 - 版本管理：App 检查更新 + 后台配置"""
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import User, AppConfig
from ..deps import get_current_admin

router = APIRouter(prefix="/api/app", tags=["App版本"])

DEFAULTS = {
    "latest_version": "7.0.0",
    "min_version": "0.0.0",
    "force": "false",
    "download_url": "http://xiaode.yuxian.shop/",
    "changelog": "",
    "notice": "有新版本可用，建议更新体验最新功能。",
}


def _get(db: Session, key: str) -> str:
    row = db.query(AppConfig).filter(AppConfig.key == key).first()
    return row.value if row and row.value is not None else DEFAULTS.get(key, "")


def _set(db: Session, key: str, value: str):
    row = db.query(AppConfig).filter(AppConfig.key == key).first()
    if row:
        row.value = value
    else:
        db.add(AppConfig(key=key, value=value))


@router.get("/version", summary="App 检查更新（公开）")
def app_version(db: Session = Depends(get_db)):
    latest = _get(db, "latest_version")
    minv = _get(db, "min_version")
    force = _get(db, "force").lower() in ("1", "true", "yes")
    return {
        "latest_version": latest,
        "min_version": minv,
        "force": force,
        "download_url": _get(db, "download_url"),
        "changelog": _get(db, "changelog"),
        "notice": _get(db, "notice"),
    }


@router.get("/version/admin", summary="读取版本配置（管理员）")
def version_admin_get(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    return {k: _get(db, k) for k in DEFAULTS.keys()}


@router.post("/version/admin", summary="保存版本配置（管理员）")
def version_admin_set(data: dict, db: Session = Depends(get_db),
                      admin: User = Depends(get_current_admin)):
    for k in DEFAULTS.keys():
        if k in data:
            _set(db, k, str(data[k]))
    db.commit()
    return {k: _get(db, k) for k in DEFAULTS.keys()}