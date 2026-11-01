# -*- coding: utf-8 -*-
"""小德日历 API - 请求 / 响应 数据模型"""
from datetime import datetime
from typing import Optional, List

from pydantic import BaseModel, Field, field_validator


# ---------- 认证 ----------
class RegisterIn(BaseModel):
    username: str = Field(..., min_length=3, max_length=50)
    password: str = Field(..., min_length=6, max_length=100)
    nickname: Optional[str] = Field(None, max_length=50)
    email: Optional[str] = Field(None, max_length=120)
    code: Optional[str] = Field(None, max_length=10)

    @field_validator("username")
    @classmethod
    def _strip_username(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 3:
            raise ValueError("用户名至少 3 个字符")
        return v

    @field_validator("nickname")
    @classmethod
    def _strip_nickname(cls, v):
        return v.strip() if isinstance(v, str) else v


class LoginIn(BaseModel):
    username: str
    password: str


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: "UserOut"


class UserOut(BaseModel):
    id: int
    # UID_V1: 8 位对外唯一 ID
    uid: str = ""
    username: str
    nickname: str
    avatar: str = ""
    email: str = ""
    is_admin: bool = False
    exp: int = 0
    level: int = 1
    name_color: str = ""
    text_effect: str = ""
    is_vip: bool = False
    vip_expire: Optional[datetime] = None
    balance: float = 0.0
    balance_cents: int = 0

    @field_validator("balance", mode="before")
    @classmethod
    def _v_balance(cls, v):
        try:
            return float(v) if v is not None else 0.0
        except (TypeError, ValueError):
            return 0.0

    @field_validator("balance_cents", mode="before")
    @classmethod
    def _v_cents(cls, v):
        try:
            return int(v) if v is not None else 0
        except (TypeError, ValueError):
            return 0

    page_bg: str = ""
    chat_bubble: str = ""
    bio: str = ""
    created_at: datetime

    class Config:
        from_attributes = True


class UserPublicOut(BaseModel):
    """公开的用户信息（头像 / 昵称）"""
    id: int
    username: str
    nickname: str
    avatar: str = ""

    class Config:
        from_attributes = True


class ProfileUpdateIn(BaseModel):
    nickname: Optional[str] = Field(None, max_length=50)
    bio: Optional[str] = Field(None, max_length=80)

    @field_validator("nickname")
    @classmethod
    def _strip_nickname(cls, v):
        return v.strip() if isinstance(v, str) else v


# ---------- 朋友圈 ----------
class MomentCreateIn(BaseModel):
    title: str = Field("", max_length=100)
    content: str = Field("", max_length=6000)
    images: List[str] = Field(default_factory=list, description="图片 URL 列表，最多 3 张")
    videos: List[str] = Field(default_factory=list, description="视频 URL 列表")
    channel_id: int = 0
    lang: str = Field("", max_length=16, description="代码块语言；空=普通文字")
    allow_download: bool = Field(False, description="图片是否允许下载，默认不可下载")
    tags: List[str] = Field(default_factory=list, description="分类标签，最多3个（教程/求助/分享/讨论）")

    @field_validator("tags")
    @classmethod
    def _check_tags(cls, v):
        allow = ("教程", "求助", "分享", "讨论")
        out = []
        for t in (v or []):
            t = (t or "").strip()
            if t in allow and t not in out:
                out.append(t)
        if len(out) > 3:
            out = out[:3]
        return out

    @field_validator("images")
    @classmethod
    def _check_images(cls, v):
        if len(v) > 3:
            raise ValueError("最多只能发 3 张图片")
        return v

    @field_validator("content")
    @classmethod
    def _check_content(cls, v):
        return v.strip() if isinstance(v, str) else v


class CommentOut(BaseModel):
    id: int
    content: str
    created_at: datetime
    user_id: int
    nickname: str
    username: str
    avatar: str
    parent_id: Optional[int] = None
    reply_to_id: Optional[int] = None
    reply_to_nickname: str = ""
    lang: str = ""
    like_count: int = 0
    liked: bool = False
    level: int = 1
    level_effect: str = "normal"
    is_vip: bool = False

    class Config:
        from_attributes = True


class CommentTreeOut(CommentOut):
    replies: List["CommentTreeOut"] = []

CommentTreeOut.model_rebuild()


class CommentCreateIn(BaseModel):
    content: str = Field(..., min_length=1, max_length=2000)
    parent_id: Optional[int] = None
    reply_to_id: Optional[int] = None
    lang: str = Field("", max_length=16, description="代码块语言；空=普通文字")

    @field_validator("content")
    @classmethod
    def _strip(cls, v):
        v = v.strip()
        if not v:
            raise ValueError("评论内容不能为空")
        return v


class MomentOut(BaseModel):
    id: int
    title: str = ""
    content: str
    images: List[str] = []
    videos: List[str] = []
    status: str = "pending"
    created_at: datetime
    user_id: int
    nickname: str
    username: str
    avatar: str = ""
    comment_count: int = 0
    like_count: int = 0
    liked: bool = False
    level: int = 1
    level_effect: str = "normal"
    channel_id: int = 0
    channel_name: str = ""
    bio: str = ""
    lang: str = ""
    allow_download: bool = False
    is_vip: bool = False
    tags: List[str] = []
    # FAV_VIEW_V1: 浏览量 + 当前用户是否已收藏
    view_count: int = 0
    favorited: bool = False


class MomentDetailOut(MomentOut):
    comments: List[CommentTreeOut] = []


 # ---------- 日记 ----------
class DiaryCreateIn(BaseModel):
    title: str = Field("", max_length=120)
    content: str = Field(..., min_length=1)
    mood: Optional[str] = Field(None, max_length=20)
    weather: Optional[str] = Field(None, max_length=60)
    diary_date: Optional[str] = Field(None, max_length=10)  # YYYY-MM-DD
    images: List[str] = Field(default_factory=list, description="图片 URL 列表，最多 5 张")
    video: Optional[str] = Field(None, max_length=255, description="视频 URL（单个，最大 10MB）")
    save_mode: Optional[str] = Field("cloud", max_length=20, description="cloud/local/both")

    @field_validator("images")
    @classmethod
    def _check_images(cls, v):
        if len(v) > 5:
            raise ValueError("日记最多只能上传 5 张图片")
        return v

    @field_validator("diary_date")
    @classmethod
    def _check_date(cls, v):
        if v in (None, ""):
            return v
        import re
        if not re.match(r"^\d{4}-\d{2}-\d{2}$", v):
            raise ValueError("diary_date 格式应为 YYYY-MM-DD")
        return v


class DiaryUpdateIn(BaseModel):
    title: Optional[str] = Field(None, max_length=120)
    content: Optional[str] = None
    mood: Optional[str] = Field(None, max_length=20)
    weather: Optional[str] = Field(None, max_length=60)
    diary_date: Optional[str] = Field(None, max_length=10)
    images: Optional[List[str]] = Field(None, description="图片 URL 列表，最多 5 张")
    video: Optional[str] = Field(None, max_length=255)
    save_mode: Optional[str] = Field(None, max_length=20)

    @field_validator("images")
    @classmethod
    def _check_images(cls, v):
        if v is not None and len(v) > 5:
            raise ValueError("日记最多只能上传 5 张图片")
        return v

    @field_validator("diary_date")
    @classmethod
    def _check_date(cls, v):
        if v in (None, ""):
            return v
        import re
        if not re.match(r"^\d{4}-\d{2}-\d{2}$", v):
            raise ValueError("diary_date 格式应为 YYYY-MM-DD")
        return v


class DiaryOut(BaseModel):
    id: int
    title: str
    content: str
    mood: Optional[str] = None
    weather: Optional[str] = None
    diary_date: Optional[str] = None
    images: List[str] = []
    video: str = ""
    save_mode: str = "cloud"
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class DiaryListOut(BaseModel):
    total: int
    items: List[DiaryOut]


# ---------- 公告 ----------
class AnnouncementIn(BaseModel):
    title: str = Field(..., min_length=1, max_length=120)
    content: str = Field(..., min_length=1, max_length=5000)
    target_version: str = Field("", max_length=20, description="指定版本，空=所有版本")


class AnnouncementOut(BaseModel):
    id: int
    title: str
    content: str
    target_version: str = ""
    created_at: datetime

    class Config:
        from_attributes = True


# ---------- 后台 ----------
class ReviewIn(BaseModel):
    status: str = Field(..., description="approved 或 rejected")


class SimpleOk(BaseModel):
    ok: bool = True


TokenOut.model_rebuild()

# ---------- 消息通知 ----------
class NotificationOut(BaseModel):
    id: int
    type: str
    moment_id: Optional[int] = None
    comment_id: Optional[int] = None
    content: str = ""
    is_read: bool = False
    is_pinned: bool = False
    created_at: datetime
    actor_id: int
    actor_nickname: str = ""
    actor_username: str = ""
    actor_avatar: str = ""
    class Config:
        from_attributes = True


class NotificationListOut(BaseModel):
    total: int
    unread: int
    items: List[NotificationOut]


# ---------- 等级 / 用户 ----------
class UserLevelOut(BaseModel):
    id: int
    uid: str = ""  # SEARCH_UID_V1
    username: str
    nickname: str = ""
    avatar: str = ""
    exp: int = 0
    level: int = 1
    level_effect: str = "normal"
    name_color: str = ""
    text_effect: str = ""
    next_exp: int = 0
    can_video: bool = False
    is_friend: bool = False
    friend_status: str = ""   # "" / pending / accepted / rejected / outgoing


class TaskItemOut(BaseModel):
    key: str
    name: str
    exp: int
    limit: int
    done: int
    desc: str = ""


class TaskListOut(BaseModel):
    exp: int
    level: int
    level_effect: str
    next_exp: int
    cur_level_exp: int
    signed_today: bool = False
    can_video: bool = False
    tasks: List[TaskItemOut] = []


class SignResultOut(BaseModel):
    ok: bool
    gained: int = 0
    exp: int = 0
    level: int = 1
    level_effect: str = "normal"
    msg: str = ""
    coin: float = 0.0            # 本次签到获得金币（支持小数）
    balance: float = 0.0         # 签到后金币余额
    balance_cents: int = 0       # 签到后余额（分）


# ---------- 排行榜（v2.1） ----------
class RankItemOut(BaseModel):
    rank: int = 0                  # 名次（从 1 开始）
    user_id: int
    username: str = ""
    nickname: str = ""
    avatar: str = ""
    level: int = 1
    level_effect: str = "normal"
    name_color: str = ""
    text_effect: str = ""
    is_vip: bool = False
    score: float = 0               # 计分：签到=天数，发帖=帖子数，富豪=金币
    is_me: bool = False            # 是否为当前登录用户
    followed: bool = False         # 当前用户是否已关注


class RankOut(BaseModel):
    type: str = "sign"            # sign=签到榜 / post=发帖榜
    range: str = "all"            # all=总榜 / day=日榜 / week=周榜
    updated_at: datetime = Field(default_factory=datetime.utcnow)
    my_rank: int = 0              # 我的名次（0=未上榜）
    my_score: float = 0           # 我的分数
    items: List[RankItemOut] = []


# ---------- 好友 ----------
class FriendRequestIn(BaseModel):
    username: str = Field(..., min_length=1, max_length=50)


class FriendOut(BaseModel):
    id: int
    username: str
    nickname: str = ""
    avatar: str = ""
    level: int = 1
    level_effect: str = "normal"
    unread: int = 0


class FriendReqOut(BaseModel):
    id: int          # friendship id
    user_id: int
    username: str
    nickname: str = ""
    avatar: str = ""
    level: int = 1
    level_effect: str = "normal"
    message: str = ""
    created_at: datetime


# ---------- 聊天 ----------
class ChatSendIn(BaseModel):
    kind: str = "text"          # text / image / video
    content: str = ""
    receiver_username: Optional[str] = None
    receiver_id: Optional[int] = None


class ChatMsgOut(BaseModel):
    id: int
    sender_id: int
    receiver_id: int
    kind: str = "text"
    content: str = ""
    is_read: bool = False
    is_recalled: bool = False
    created_at: datetime
    sender_name: str = ""
    sender_username: str = ""
    sender_avatar: str = ""
    sender_level: int = 1
    sender_effect: str = "normal"
    sender_is_vip: bool = False


class ChatConvOut(BaseModel):
    peer_id: int
    peer_username: str
    peer_nickname: str = ""
    peer_avatar: str = ""
    peer_level: int = 1
    peer_effect: str = "normal"
    last_kind: str = "text"
    last_content: str = ""
    last_at: Optional[datetime] = None
    unread: int = 0


# ---------- 邮箱验证码 / 账号安全 ----------
class SendCodeIn(BaseModel):
    """发送验证码：purpose = register / reset / bind"""
    email: str = Field(..., max_length=120)
    purpose: str = Field("register", max_length=20)
    # CAPTCHA_V1: 人机验证
    captcha_token: str = ""
    captcha_answer: int = 0


class ResetPasswordIn(BaseModel):
    """忘记密码：邮箱 + 验证码 + 新密码"""
    email: str = Field(..., max_length=120)
    code: str = Field(..., max_length=10)
    new_password: str = Field(..., min_length=6, max_length=100)


class ChangePasswordIn(BaseModel):
    old_password: str = Field(..., min_length=1, max_length=100)
    new_password: str = Field(..., min_length=6, max_length=100)


class ChangeUsernameIn(BaseModel):
    new_username: str = Field(..., min_length=3, max_length=50)
    password: str = Field(..., min_length=1, max_length=100)


class ChangeEmailIn(BaseModel):
    """换绑邮箱：新邮箱 + 验证码（发给新邮箱）"""
    new_email: str = Field(..., max_length=120)
    code: str = Field(..., max_length=10)


class ChatSettingIn(BaseModel):
    """聊天个性化：气泡主题 / 背景图 / 名字颜色 / 文字特效"""
    chat_theme: Optional[str] = Field(None, max_length=30)
    chat_bg: Optional[str] = Field(None, max_length=255)
    name_color: Optional[str] = Field(None, max_length=20)
    text_effect: Optional[str] = Field(None, max_length=20)


# ==================== 应用商店（v9.0 新增） ====================
class AppCreateIn(BaseModel):
    name: str
    package_name: str = ""
    version: str = ""
    summary: str = ""
    description: str = ""
    icon: str = ""
    screenshots: List[str] = []
    category: str = ""
    upload_type: str = "external"   # direct（会员直传）/ external（外链）
    ext_link: str = ""              # 外链下载地址
    apk_url: str = ""
    apk_size: int = 0
    is_paid: bool = False
    price: int = 0


class AppOut(BaseModel):
    id: int
    name: str = ""
    package_name: str = ""
    version: str = ""
    summary: str = ""
    description: str = ""
    icon: str = ""
    screenshots: List[str] = []
    category: str = ""
    upload_type: str = "external"
    apk_url: str = ""
    apk_size: int = 0
    ext_link: str = ""
    status: str = "pending"
    reject_reason: str = ""
    download_count: int = 0
    view_count: int = 0
    uploader_id: int = 0
    uploader_name: str = ""
    is_paid: bool = False
    price: int = 0
    tip_total: int = 0
    purchased: bool = False
    created_at: datetime


class AppCommentIn(BaseModel):
    content: str
    rating: int = 5


class AppCommentOut(BaseModel):
    id: int
    app_id: int
    user_id: int
    nickname: str = ""
    avatar: str = ""
    content: str = ""
    rating: int = 5
    created_at: datetime


# ==================== 社交：关注 / 个人详情 / 私信 ====================
class SocialUserOut(BaseModel):
    id: int
    uid: str = ""          # UID_FIX_V1: 个人主页展示 8 位 UID
    username: str
    nickname: str = ""
    avatar: str = ""
    exp: int = 0
    level: int = 1
    level_effect: str = "normal"
    is_vip: bool = False
    follower_count: int = 0
    following_count: int = 0
    followed: bool = False
    is_self: bool = False
    moment_count: int = 0
    app_count: int = 0
    page_bg: str = ""
    bio: str = ""


class CardUserOut(BaseModel):
    id: int
    username: str
    nickname: str = ""
    avatar: str = ""
    level: int = 1
    level_effect: str = "normal"
    is_vip: bool = False
    followed: bool = False


class PrivateMsgIn(BaseModel):
    content: str = Field(..., min_length=1, max_length=500)


class PrivateMsgOut(BaseModel):
    id: int
    sender_id: int
    receiver_id: int
    content: str = ""
    is_read: bool = False
    created_at: datetime


class PrivateConvOut(BaseModel):
    user: CardUserOut
    last_msg: str = ""
    last_time: Optional[datetime] = None
    unread: int = 0



# ==================== Tools / Tools upload ====================
class ToolCreateIn(BaseModel):
    name: str
    cover: str = ""
    intro: str = ""
    url: str




class ToolOut(BaseModel):
    id: int
    name: str = ""
    cover: str = ""
    intro: str = ""
    url: str = ""
    status: str = "pending"
    reject_reason: str = ""
    use_count: int = 0
    uploader_id: int = 0
    uploader_name: str = ""
    created_at: datetime




# ==================== App paid / tip ====================
class AppTipIn(BaseModel):
    amount: int = 10




class AppTipSummary(BaseModel):
    app_id: int
    total: int = 0
    count: int = 0
    top: List[dict] = []




class AppPurchaseOut(BaseModel):
    ok: bool = True
    balance: float = 0.0
    message: str = ""
