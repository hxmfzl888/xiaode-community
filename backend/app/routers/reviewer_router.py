# -*- coding: utf-8 -*-
"""小德社区 API - 审核系统：审核员登录 / 审核帖子·应用·频道 / 举报处理
审核员使用独立表 reviewers，与普通用户完全隔离。
审核端看不到发布者昵称/头像，仅能看到其用户 ID。
"""
from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import (User, Moment, MomentLike, AppItem, Channel, Comment, Reviewer, Report, Notification, Tool,
                        ResourceItem, LoginLog, ReviewerAgreement)
from ..deps import get_current_admin
from ..auth import hash_password, verify_password, create_access_token, decode_token

router = APIRouter(prefix="/api/reviewer", tags=["审核端"])

# ============ resreview_v1 审核协议全文 ============
AGREEMENT_VERSION = "v1"
AGREEMENT_TITLE = "小德社区审核员责任协议书"
AGREEMENT_TEXT = """小德社区审核员责任协议书

一、审核员的身份与职责
1. 审核员是平台内容安全的第一责任人，负责对用户发布的帖子、应用、工具、频道、资源等内容进行合规性审核。
2. 审核员使用平台分配的独立账号开展工作，账号不得转借、共享、出租或以任何形式交由他人使用。
3. 审核员应在规定时限内完成待审内容的处理，不得无故积压。

二、必须驳回的内容（一票否决）
出现以下任一情形的，一律驳回，不得通过：
1. 违反国家法律法规的内容，包括但不限于危害国家安全、泄露国家秘密、颠覆国家政权、破坏国家统一；
2. 煽动民族仇恨、民族歧视，破坏民族团结，或破坏国家宗教政策、宣扬邪教和封建迷信；
3. 散布谣言，扰乱社会秩序，破坏社会稳定；
4. 散布淫秽、色情、赌博、暴力、凶杀、恐怖或者教唆犯罪的内容；
5. 侮辱、诽谤他人，侵害他人名誉权、隐私权、肖像权、知识产权等合法权益；
6. 含有涉未成年人不良信息，或诱导未成年人从事危险行为的内容；
7. 涉及毒品、枪支弹药、管制刀具、爆炸物、伪造证件、黑客工具、木马病毒、外挂破解等违法违禁品；
8. 涉及诈骗、传销、非法集资、非法放贷、洗钱、刷单、兼职骗局等金融与欺诈类内容；
9. 侵犯他人版权的盗版资源、破解软件、未授权影视音乐书籍等；
10. 含有恶意广告、外链引流、二维码拉人、私域导流等与社区定位无关的营销内容；
11. 涉及个人隐私信息（身份证号、手机号、住址、银行卡号、人脸照片等）的泄露内容；
12. 恶意攻击平台、攻击其他用户、组织刷量刷赞、买卖账号或数据的内容；
13. 其他明显违背公序良俗、社会公德的内容。

三、可以放行的内容
1. 内容真实、表达正常，不违反上述第二条任何一项；
2. 图片、视频清晰可辨，不含违规元素；
3. 资源类内容：来源合法、可正常下载、描述与实物一致、无病毒无捆绑、付费标价合理；
4. 应用类内容：可正常安装运行、无恶意权限索取、无后台偷跑流量、无窃取隐私行为；
5. 存在轻微瑕疵但不构成违规的（如排版混乱、表述不清），可驳回并写明修改建议，指导用户重新提交。

四、审核员的行为红线
1. 严禁利用审核权限对特定用户进行差别对待、恶意打压或违规放行；
2. 严禁收受任何形式的财物、好处，为违规内容开绿灯；
3. 严禁泄露审核过程中接触到的用户隐私、内部数据、审核策略；
4. 严禁将审核权限用于与工作无关的目的；
5. 严禁与他人串通、批量放行违规内容。

五、责任条款（重要）
1. 审核员对其审核结论承担全部责任。经审核员通过并上线的内容，若事后被认定为违规，责任由该审核员承担。
2. 因审核员故意或重大过失放行违规内容，导致平台被监管部门处罚、被应用商店下架、被司法机关调查，或造成用户损失、平台声誉损失的，平台有权向该审核员追偿全部直接与间接损失。
3. 审核员的审核操作全程留痕，包括操作账号、操作时间、操作 IP、审核结论与驳回理由，可作为责任认定依据。
4. 平台定期抽查已通过内容，发现错审、漏审的，视情节给予警告、暂停权限、终止合作处理；情节严重的，移交司法机关。
5. 审核员离职或合作终止后，其在职期间的审核责任不因离职而免除。

六、协议的生效与变更
1. 审核员阅读并点击"同意并签署"后，本协议立即生效，与纸质签署具有同等效力。
2. 平台有权根据法律法规和运营需要更新本协议，更新后需重新签署。
3. 本协议的解释权归小德社区运营方所有。

签署即表示：本人已完整阅读并理解上述全部条款，自愿承担相应的审核责任。
"""

def _need_agreement(rv, db):
    """检查审核员是否已签署当前版本协议，未签署则 403"""
    signed = db.query(ReviewerAgreement).filter(
        ReviewerAgreement.reviewer_id == rv.id,
        ReviewerAgreement.version == AGREEMENT_VERSION,
        ReviewerAgreement.agreed == True,
    ).first()
    if not signed:
        raise HTTPException(status_code=403, detail="请先阅读并签署《审核员责任协议书》后再进行审核")

def _client_ip(request):
    try:
        xf = request.headers.get("x-forwarded-for") or ""
        if xf:
            return xf.split(",")[0].strip()[:64]
        return (request.client.host or "")[:64]
    except Exception:
        return ""

def _log(rv_or_user, role, username, ok, reason="", request=None, platform="web"):
    """写登录日志（独立 session，失败不影响主流程）"""
    try:
        from ..database import SessionLocal
        db2 = SessionLocal()
        try:
            db2.add(LoginLog(
                role=role,
                user_id=getattr(rv_or_user, "id", 0) or 0,
                username=(username or "")[:80],
                ip=_client_ip(request) if request else "",
                user_agent=(request.headers.get("user-agent") or "")[:400] if request else "",
                ok=bool(ok),
                fail_reason=(reason or "")[:120],
                platform=platform,
            ))
            db2.commit()
        finally:
            db2.close()
    except Exception as e:
        print("login log error:", e)



bearer_scheme = HTTPBearer(auto_error=False)


def get_current_reviewer(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> Reviewer:
    if credentials is None or not credentials.credentials:
        raise HTTPException(status_code=401, detail="未登录")
    payload = decode_token(credentials.credentials)
    if not payload or payload.get("role") != "reviewer":
        raise HTTPException(status_code=401, detail="令牌无效")
    rid = payload.get("sub")
    rv = db.query(Reviewer).filter(Reviewer.id == int(rid)).first()
    if not rv or not rv.active:
        raise HTTPException(status_code=401, detail="审核员不存在或已停用")
    return rv


# ============ 审核员登录 ============
@router.post("/login", summary="审核员登录")
def reviewer_login(data: dict, request: Request = None, db: Session = Depends(get_db)):
    username = (data.get("username") or "").strip()
    password = (data.get("password") or "")
    rv = db.query(Reviewer).filter(Reviewer.username == username).first()
    if not rv or not verify_password(password, rv.password_hash):
        _log(None, "reviewer", username, False, "账号或密码错误", request)
        raise HTTPException(status_code=401, detail="账号或密码错误")
    if not rv.active:
        _log(rv, "reviewer", username, False, "账号已停用", request)
        raise HTTPException(status_code=403, detail="账号已停用")
    rv.last_login = datetime.utcnow()
    db.commit()
    _log(rv, "reviewer", username, True, "", request)
    token = create_access_token(rv.id, rv.username, role="reviewer")
    # 附上协议签署状态，前端据此决定是否先弹协议
    signed = db.query(ReviewerAgreement).filter(
        ReviewerAgreement.reviewer_id == rv.id,
        ReviewerAgreement.version == AGREEMENT_VERSION,
        ReviewerAgreement.agreed == True,
    ).first() is not None
    return {"access_token": token, "token": token, "role": "reviewer",
            "nickname": rv.nickname or rv.username,
            "agreement_signed": signed, "agreement_version": AGREEMENT_VERSION}


@router.get("/me", summary="当前审核员信息")
def reviewer_me(rv: Reviewer = Depends(get_current_reviewer)):
    return {"id": rv.id, "username": rv.username, "nickname": rv.nickname or rv.username}


# ============ 待审内容（匿名化：只给用户ID，不给昵称/头像） ============
@router.get("/moments", summary="待审/全部动态（匿名）")
def rv_moments(st: Optional[str] = Query(None), limit: int = Query(100, le=300),
               db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    q = db.query(Moment)
    if st:
        q = q.filter(Moment.status == st)
    rows = q.order_by(Moment.created_at.desc()).limit(limit).all()
    out = []
    for m in rows:
        out.append({
            "id": m.id, "title": getattr(m, "title", "") or "",
            "content": m.content or "",
            "images": [x for x in (m.images or "").split(",") if x],
            "videos": [x for x in (getattr(m, "videos", "") or "").split(",") if x],
            "status": m.status, "created_at": m.created_at.isoformat() if m.created_at else "",
            "author_id": m.user_id,   # 只给 ID，不给昵称/头像
        })
    return out


@router.post("/moments/{moment_id}/review", summary="审核动态：通过/驳回")
def rv_review_moment(moment_id: int, data: dict,
                     db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    _need_agreement(rv, db)
    m = db.query(Moment).filter(Moment.id == moment_id).first()
    if not m:
        raise HTTPException(status_code=404, detail="动态不存在")
    approve = bool(data.get("approve", True))
    m.status = "approved" if approve else "rejected"
    # 审核结果通知作者（actor 用作者自己，避免普通用户无法作为 actor 校验）
    _t = (getattr(m, "title", "") or (m.content or ""))[:20]
    db.add(Notification(user_id=m.user_id, actor_id=m.user_id, type="audit",
                        moment_id=m.id,
                        content=("你的帖子《" + _t + "》已通过审核" if approve
                                 else "你的帖子《" + _t + "》未通过审核")))
    db.commit()
    return {"ok": True, "id": m.id, "status": m.status}


@router.get("/apps", summary="待审/全部应用（匿名）")
def rv_apps(st: Optional[str] = Query(None), limit: int = Query(100, le=300),
            db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    q = db.query(AppItem)
    if st:
        q = q.filter(AppItem.status == st)
    rows = q.order_by(AppItem.created_at.desc()).limit(limit).all()
    out = []
    for a in rows:
        out.append({
            "id": a.id, "name": a.name or "", "package_name": a.package_name or "",
            "version": a.version or "", "summary": a.summary or "",
            "icon": a.icon or "", "category": a.category or "",
            "status": a.status, "created_at": a.created_at.isoformat() if a.created_at else "",
            "author_id": a.uploader_id,
        })
    return out


@router.post("/apps/{app_id}/review", summary="审核应用：通过/驳回")
def rv_review_app(app_id: int, data: dict,
                  db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    _need_agreement(rv, db)
    a = db.query(AppItem).filter(AppItem.id == app_id).first()
    if not a:
        raise HTTPException(status_code=404, detail="应用不存在")
    approve = bool(data.get("approve", True))
    a.status = "approved" if approve else "rejected"
    if not approve:
        a.reject_reason = (data.get("reason") or "")[:200]
    db.add(Notification(user_id=a.uploader_id, actor_id=a.uploader_id, type="audit",
                        content=("你的应用《" + (a.name or "")[:20] + "》已通过审核" if approve
                                 else "你的应用《" + (a.name or "")[:20] + "》未通过审核")))
    db.commit()
    return {"ok": True, "id": a.id, "status": a.status}


@router.get("/tools", summary="tool list for review")
def rv_tools(st: Optional[str] = Query(None), limit: int = Query(100, le=300),
             db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    q = db.query(Tool)
    if st:
        q = q.filter(Tool.status == st)
    rows = q.order_by(Tool.created_at.desc()).limit(limit).all()
    out = []
    for t in rows:
        out.append({
            "id": t.id, "name": t.name or "",
            "cover": t.cover or "", "intro": t.intro or "",
            "url": t.url or "",
            "status": t.status, "created_at": t.created_at.isoformat() if t.created_at else "",
            "author_id": t.uploader_id,
        })
    return out




@router.post("/tools/{tool_id}/review", summary="review tool: approve/reject")
def rv_review_tool(tool_id: int, data: dict,
                   db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    _need_agreement(rv, db)
    t = db.query(Tool).filter(Tool.id == tool_id).first()
    if not t:
        raise HTTPException(status_code=404, detail="tool not found")
    approve = bool(data.get("approve", True))
    t.status = "approved" if approve else "rejected"
    if not approve:
        t.reject_reason = (data.get("reason") or "")[:200]
    db.add(Notification(user_id=t.uploader_id, actor_id=t.uploader_id, type="audit",
                        content=("your tool " + (t.name or "")[:20] + " approved" if approve
                                 else "your tool " + (t.name or "")[:20] + " rejected")))
    db.commit()
    return {"ok": True, "id": t.id, "status": t.status}



@router.get("/channels", summary="频道列表（可按状态筛选）")
def rv_channels(st: Optional[str] = Query(None), limit: int = Query(100, le=300),
                db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    q = db.query(Channel)
    if st:
        q = q.filter(Channel.status == st)
    rows = q.order_by(Channel.id.desc()).limit(limit).all()
    out = []
    for c in rows:
        out.append({
            "id": c.id, "name": c.name or "", "intro": c.intro or "", "desc": c.intro or "",
            "image": c.image or "", "owner_id": c.owner_id, "status": c.status,
            "member_count": c.member_count or 0,
            "created_at": c.created_at.isoformat() if c.created_at else "",
        })
    return out

@router.post("/channels/{channel_id}/review", summary="审核频道：通过/驳回")
def rv_review_channel(channel_id: int, data: dict,
                      db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    _need_agreement(rv, db)
    c = db.query(Channel).filter(Channel.id == channel_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="频道不存在")
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
    return {"ok": True, "id": c.id, "status": c.status}


@router.delete("/channels/{channel_id}", summary="删除违规频道")
def rv_del_channel(channel_id: int,
                   db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    _need_agreement(rv, db)
    c = db.query(Channel).filter(Channel.id == channel_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="频道不存在")
    db.delete(c); db.commit()
    return {"ok": True}


# ============ 举报 ============
@router.get("/reports", summary="举报列表")
def rv_reports(st: Optional[str] = Query("pending"), limit: int = Query(100, le=300),
               db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    q = db.query(Report)
    if st:
        q = q.filter(Report.status == st)
    rows = q.order_by(Report.created_at.desc()).limit(limit).all()
    out = []
    for r in rows:
        # 附上被举报内容快照（匿名）
        snap = ""
        if r.target_type == "moment":
            m = db.query(Moment).filter(Moment.id == r.target_id).first()
            if m:
                snap = (getattr(m, "title", "") or "") + " | " + (m.content or "")
        elif r.target_type == "app":
            a = db.query(AppItem).filter(AppItem.id == r.target_id).first()
            if a:
                snap = (a.name or "")
        out.append({
            "id": r.id, "target_type": r.target_type, "target_id": r.target_id,
            "reason": r.reason or "", "detail": r.detail or "",
            "status": r.status, "snapshot": snap[:200],
            "reporter_id": r.reporter_id,
            "created_at": r.created_at.isoformat() if r.created_at else "",
        })
    return out


@router.post("/reports/{report_id}/handle", summary="处理举报（两态：无违规 / 有违规）")
def rv_handle_report(report_id: int, data: dict,
                     db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    """data: { verdict: "clean" | "violation" }
    - clean     → 无违规：举报驳回，内容保留，通知举报人
    - violation → 有违规：直接删除被举报内容（帖子/应用等），通知作者 + 举报人
    """
    _need_agreement(rv, db)
    r = db.query(Report).filter(Report.id == report_id).first()
    if not r:
        raise HTTPException(status_code=404, detail="举报不存在")
    verdict = (data.get("verdict") or data.get("action") or "").strip().lower()
    if verdict in ("clean", "dismiss", "none", "ok", "无违规"):
        verdict = "clean"
    else:
        verdict = "violation"

    r.handled_by = rv.id
    author_id = 0
    title = ""

    if r.target_type == "moment":
        m = db.query(Moment).filter(Moment.id == r.target_id).first()
        if m:
            author_id = m.user_id
            title = (getattr(m, "title", "") or (m.content or ""))[:20]
        if verdict == "violation" and m:
            # 有违规 → 直接删除帖子（连同其评论/点赞关系）
            try:
                db.query(MomentLike).filter(MomentLike.moment_id == m.id).delete(synchronize_session=False)
            except Exception:
                pass
            try:
                from ..models import Comment, CommentLike
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
            if verdict == "violation":
                db.delete(a)

    r.status = "dismissed" if verdict == "clean" else "handled"

    # ---- 通知：举报人 ----
    try:
        if verdict == "clean":
            db.add(Notification(user_id=r.reporter_id, actor_id=r.reporter_id, type="report",
                                content="你的举报经审核【无违规】，内容已保留，感谢反馈"))
        else:
            db.add(Notification(user_id=r.reporter_id, actor_id=r.reporter_id, type="report",
                                content="你的举报经审核【有违规】，内容已删除，感谢反馈"))
    except Exception:
        pass

    # ---- 通知：被举报内容作者（有违规时） ----
    if verdict == "violation" and author_id:
        try:
            db.add(Notification(user_id=author_id, actor_id=author_id, type="report",
                                content="你的内容《" + title + "》因【有违规】已被删除"))
        except Exception:
            pass

    db.commit()
    return {"ok": True, "id": r.id, "status": r.status, "verdict": verdict, "removed": verdict == "violation"}



# ============ 资源审核 ============
@router.get("/resources", summary="待审/全部资源（匿名）")
def rv_resources(st: Optional[str] = Query(None), limit: int = Query(100, le=300),
                 db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    q = db.query(ResourceItem)
    if st:
        q = q.filter(ResourceItem.status == st)
    rows = q.order_by(ResourceItem.created_at.desc()).limit(limit).all()
    out = []
    for r in rows:
        out.append({
            "id": r.id, "name": r.name or "", "summary": r.summary or "",
            "description": r.description or "", "category": r.category or "",
            "cover": r.cover or "", "screenshots": r.screenshots or "",
            "res_type": r.res_type or "link", "download_url": r.download_url or "",
            "file_size": r.file_size or 0, "is_paid": bool(r.is_paid),
            "price": r.price or 0, "status": r.status,
            "reject_reason": r.reject_reason or "",
            "created_at": r.created_at.isoformat() if r.created_at else "",
            "uploader_id": r.uploader_id,
        })
    return out


@router.post("/resources/{rid}/review", summary="审核资源：通过/驳回")
def rv_review_resource(rid: int, data: dict, request: Request = None,
                       db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    _need_agreement(rv, db)
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    approve = bool(data.get("approve", True))
    r.status = "approved" if approve else "rejected"
    reason = (data.get("reason") or "")[:300]
    r.reject_reason = "" if approve else reason
    txt = ("你的资源《" + (r.name or "")[:20] + "》已通过审核") if approve else \
          ("你的资源《" + (r.name or "")[:20] + "》未通过审核" + ("：" + reason if reason else ""))
    db.add(Notification(user_id=r.uploader_id, actor_id=r.uploader_id, type="audit", content=txt))
    db.commit()
    return {"ok": True, "id": r.id, "status": r.status}


@router.delete("/resources/{rid}", summary="删除违规资源")
def rv_delete_resource(rid: int, db: Session = Depends(get_db),
                       rv: Reviewer = Depends(get_current_reviewer)):
    _need_agreement(rv, db)
    r = db.query(ResourceItem).filter(ResourceItem.id == rid).first()
    if not r:
        raise HTTPException(status_code=404, detail="资源不存在")
    db.delete(r)
    db.commit()
    return {"ok": True}


# ============ 审核协议 ============
@router.get("/agreement", summary="获取审核员责任协议书全文")
def rv_get_agreement():
    return {"version": AGREEMENT_VERSION, "title": AGREEMENT_TITLE, "text": AGREEMENT_TEXT}


@router.get("/agreement/status", summary="查询我是否已签署协议")
def rv_agreement_status(db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    row = db.query(ReviewerAgreement).filter(
        ReviewerAgreement.reviewer_id == rv.id,
        ReviewerAgreement.version == AGREEMENT_VERSION,
    ).order_by(ReviewerAgreement.id.desc()).first()
    return {
        "version": AGREEMENT_VERSION,
        "signed": bool(row and row.agreed),
        "signed_at": row.signed_at.isoformat() if row and row.signed_at else "",
    }


@router.post("/agreement/sign", summary="签署审核员责任协议书")
def rv_sign_agreement(data: dict, request: Request = None,
                      db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    ver = (data.get("version") or AGREEMENT_VERSION)
    if ver != AGREEMENT_VERSION:
        raise HTTPException(status_code=400, detail="协议版本不符，请刷新页面重新阅读")
    if not bool(data.get("agreed", False)):
        raise HTTPException(status_code=400, detail="必须同意协议内容才能签署")
    old = db.query(ReviewerAgreement).filter(
        ReviewerAgreement.reviewer_id == rv.id,
        ReviewerAgreement.version == AGREEMENT_VERSION,
    ).first()
    if old:
        old.agreed = True
        old.signed_at = datetime.utcnow()
        old.ip = _client_ip(request)
        old.user_agent = (request.headers.get("user-agent") or "")[:400] if request else ""
    else:
        db.add(ReviewerAgreement(
            reviewer_id=rv.id, version=AGREEMENT_VERSION, agreed=True,
            ip=_client_ip(request),
            user_agent=(request.headers.get("user-agent") or "")[:400] if request else "",
        ))
    db.commit()
    return {"ok": True, "version": AGREEMENT_VERSION, "signed": True}


@router.get("/stats", summary="审核端概览")
def rv_stats(db: Session = Depends(get_db), rv: Reviewer = Depends(get_current_reviewer)):
    return {
        "moment_pending": db.query(Moment).filter(Moment.status == "pending").count(),
        "app_pending": db.query(AppItem).filter(AppItem.status == "pending").count(),
        "report_pending": db.query(Report).filter(Report.status == "pending").count(),
        "resource_pending": db.query(ResourceItem).filter(ResourceItem.status == "pending").count(),
    }

# ============================================================
# RV_BAN_USER_V1: 审核端「封禁用户」功能
# 审核员可在处理帖子/举报时直接封禁作者，无需找管理员。
# 出于匿名原则，只暴露 用户ID / 昵称(用于确认没封错人) / 封禁状态，不暴露邮箱等隐私。
# ============================================================

def _ban_user_out(u: User) -> dict:
    return {
        "id": u.id,
        "nickname": (u.nickname or ""),
        "username": (u.username or ""),
        "is_banned": bool(getattr(u, "is_banned", False)),
        "ban_reason": (getattr(u, "ban_reason", "") or ""),
        "ban_until": u.ban_until.isoformat() if getattr(u, "ban_until", None) else "",
        "post_count": 0,
    }


@router.get("/users", summary="被封禁用户列表")
def rv_banned_users(limit: int = Query(200, le=500), db: Session = Depends(get_db),
                    rv: Reviewer = Depends(get_current_reviewer)):
    rows = (db.query(User).filter(User.is_banned == True)
            .order_by(User.id.desc()).limit(limit).all())
    out = []
    for u in rows:
        d = _ban_user_out(u)
        try:
            d["post_count"] = db.query(Moment).filter(Moment.user_id == u.id).count()
        except Exception:
            pass
        out.append(d)
    return out


@router.get("/users/{user_id}", summary="查询用户封禁状态")
def rv_user_status(user_id: int, db: Session = Depends(get_db),
                   rv: Reviewer = Depends(get_current_reviewer)):
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    d = _ban_user_out(u)
    try:
        d["post_count"] = db.query(Moment).filter(Moment.user_id == u.id).count()
        d["pending_count"] = db.query(Moment).filter(
            Moment.user_id == u.id, Moment.status == "pending").count()
    except Exception:
        pass
    return d


@router.post("/users/{user_id}/ban", summary="封禁/解封用户")
def rv_ban_user(user_id: int, data: dict, db: Session = Depends(get_db),
                rv: Reviewer = Depends(get_current_reviewer)):
    """data: { ban: true/false, reason: "原因", days: 0=永久, 7/30=限时,
               remove_posts: true=同时下架该用户全部帖子 }
    """
    _need_agreement(rv, db)
    u = db.query(User).filter(User.id == user_id).first()
    if not u:
        raise HTTPException(status_code=404, detail="用户不存在")
    if getattr(u, "is_admin", False):
        raise HTTPException(status_code=400, detail="不能封禁管理员账号")

    ban = bool(data.get("ban", True))
    reason = (data.get("reason") or "").strip()[:200]
    if ban and not reason:
        raise HTTPException(status_code=400, detail="请填写封禁理由")

    from datetime import datetime as _dtn, timedelta as _tdt
    removed = 0

    u.is_banned = ban
    u.ban_reason = reason if ban else ""
    if ban:
        try:
            days = int(data.get("days") or 0)
        except Exception:
            days = 0
        u.ban_until = (_dtn.utcnow() + _tdt(days=days)) if days > 0 else None
    else:
        u.ban_until = None

    # 可选：同时把该用户已发布的帖子全部下架（置为 rejected，物理保留便于申诉/追溯）
    if ban and data.get("remove_posts"):
        try:
            rows = db.query(Moment).filter(
                Moment.user_id == u.id, Moment.status.in_(["approved", "pending"])).all()
            for m in rows:
                m.status = "rejected"
                removed += 1
        except Exception:
            pass

    # 通知用户
    try:
        if ban:
            tip = "你的账号已被封禁"
            if reason:
                tip += "：" + reason
            if u.ban_until:
                tip += "（解封时间 " + u.ban_until.strftime("%Y-%m-%d %H:%M") + "）"
            db.add(Notification(user_id=u.id, actor_id=u.id, type="system", content=tip))
        else:
            db.add(Notification(user_id=u.id, actor_id=u.id, type="system",
                                content="你的账号已被解除封禁，欢迎回来"))
    except Exception:
        pass

    db.commit()
    return {
        "ok": True,
        "id": u.id,
        "is_banned": bool(u.is_banned),
        "ban_until": u.ban_until.isoformat() if u.ban_until else "",
        "posts_removed": removed,
    }


