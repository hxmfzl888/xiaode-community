# -*- coding: utf-8 -*-
import os
import uuid
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File
from sqlalchemy import or_, desc
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import User, Tool
from ..deps import get_current_user, get_current_admin, get_current_user_optional
from ..schemas import ToolCreateIn, ToolOut


router = APIRouter(prefix="/api/tools", tags=["tools"])


BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOL_DIR = os.path.join(BASE_DIR, "uploads", "tools")
os.makedirs(TOOL_DIR, exist_ok=True)
ALLOWED_IMG_EXT = {"jpg", "jpeg", "png", "gif", "webp"}
MAX_COVER_BYTES = 10 * 1024 * 1024




def _tool_out(t):
    up = getattr(t, "uploader", None)
    return {
        "id": t.id,
        "name": t.name or "",
        "cover": t.cover or "",
        "intro": t.intro or "",
        "url": t.url or "",
        "status": t.status or "pending",
        "reject_reason": t.reject_reason or "",
        "use_count": t.use_count or 0,
        "uploader_id": t.uploader_id,
        "uploader_name": ((up.nickname or up.username) if up else ""),
        "created_at": t.created_at,
    }




@router.get("", response_model=List[ToolOut])
def list_tools(q: str = Query("", max_length=60),
               limit: int = Query(50, ge=1, le=100), offset: int = Query(0, ge=0),
               db: Session = Depends(get_db),
               current: Optional[User] = Depends(get_current_user_optional)):
    if current:
        query = db.query(Tool).filter(or_(Tool.status == "approved", Tool.uploader_id == current.id))
    else:
        query = db.query(Tool).filter(Tool.status == "approved")
    if q.strip():
        kw = "%%%s%%" % q.strip()
        query = query.filter(or_(Tool.name.like(kw), Tool.intro.like(kw)))
    rows = query.order_by(desc(Tool.created_at)).offset(offset).limit(limit).all()
    return [_tool_out(t) for t in rows]




@router.get("/mine", response_model=List[ToolOut])
def my_tools(db: Session = Depends(get_db), current: User = Depends(get_current_user)):
    rows = db.query(Tool).filter(Tool.uploader_id == current.id).order_by(desc(Tool.created_at)).all()
    return [_tool_out(t) for t in rows]




@router.get("/pending", response_model=List[ToolOut])
def pending_tools(db: Session = Depends(get_db), current: User = Depends(get_current_admin)):
    rows = db.query(Tool).filter(Tool.status == "pending").order_by(desc(Tool.created_at)).all()
    return [_tool_out(t) for t in rows]




@router.post("/upload_cover")
def upload_cover(file: UploadFile = File(...), current: User = Depends(get_current_user)):
    fn = file.filename or ""
    ext = fn.rsplit(".", 1)[-1].lower() if "." in fn else ""
    if ext not in ALLOWED_IMG_EXT:
        raise HTTPException(status_code=400, detail="unsupported image type")
    data = file.file.read()
    if len(data) > MAX_COVER_BYTES:
        raise HTTPException(status_code=400, detail="image too large")
    fname = uuid.uuid4().hex + "." + ext
    with open(os.path.join(TOOL_DIR, fname), "wb") as f:
        f.write(data)
    return {"ok": True, "url": "/uploads/tools/" + fname}




@router.post("", response_model=ToolOut)
def create_tool(data: ToolCreateIn, db: Session = Depends(get_db),
                current: User = Depends(get_current_user)):
    name = (data.name or "").strip()
    url = (data.url or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="tool name required")
    if not url.startswith("http"):
        raise HTTPException(status_code=400, detail="url must start with http")
    t = Tool(
        name=name, cover=(data.cover or "").strip(),
        intro=(data.intro or "").strip(), url=url,
        status="pending", uploader_id=current.id,
    )
    db.add(t)
    db.commit()
    db.refresh(t)
    return _tool_out(t)




@router.get("/{tool_id}", response_model=ToolOut)
def tool_detail(tool_id: int, db: Session = Depends(get_db)):
    t = db.query(Tool).filter(Tool.id == tool_id).first()
    if not t:
        raise HTTPException(status_code=404, detail="tool not found")
    t.use_count = (t.use_count or 0) + 1
    db.commit()
    return _tool_out(t)




@router.post("/{tool_id}/review", response_model=ToolOut)
def review_tool(tool_id: int, ok: bool = Query(True), reason: str = Query(""),
                db: Session = Depends(get_db), current: User = Depends(get_current_admin)):
    t = db.query(Tool).filter(Tool.id == tool_id).first()
    if not t:
        raise HTTPException(status_code=404, detail="tool not found")
    t.status = "approved" if ok else "rejected"
    t.reject_reason = "" if ok else (reason or "")
    db.commit()
    db.refresh(t)
    return _tool_out(t)

