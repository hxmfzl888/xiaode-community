# -*- coding: utf-8 -*-
"""小德日历 API - 数据模型（用户 / 朋友圈 / 评论 / 日记 / 公告）"""
from datetime import datetime

from sqlalchemy import (
    Column, Integer, String, Text, DateTime, ForeignKey, Boolean, UniqueConstraint
)
from sqlalchemy.orm import relationship

from .database import Base


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    # UID_V1: 8 位数字唯一 ID（对外展示、可搜索），与主键 id 解耦
    uid = Column(String(8), unique=True, index=True, nullable=True)
    username = Column(String(50), unique=True, index=True, nullable=False)
    nickname = Column(String(50), nullable=False, default="")
    password_hash = Column(String(255), nullable=False)
    avatar = Column(String(255), nullable=False, default="")   # 头像 URL
    email = Column(String(120), nullable=False, default="", index=True)  # 绑定邮箱
    chat_theme = Column(String(30), nullable=False, default="")   # 聊天气泡主题
    chat_bg = Column(String(255), nullable=False, default="")     # 聊天背景图 URL
    name_color = Column(String(20), nullable=False, default="")    # 名字颜色(如 #ff0000 或 gold/rainbow)
    text_effect = Column(String(20), nullable=False, default="")   # 文字特效(none/glow/rainbow/gradient/shake)
    is_admin = Column(Boolean, nullable=False, default=False)  # 是否管理员
    created_at = Column(DateTime, default=datetime.utcnow)
    exp = Column(Integer, nullable=False, default=0)    # 经验值
    level = Column(Integer, nullable=False, default=1)  # 等级
    last_sign_date = Column(String(10), nullable=True)   # 签到日期
    sec_question = Column(String(200), nullable=False, default="")  # 密保问题
    sec_answer_hash = Column(String(255), nullable=False, default="")  # 密保答案哈希

    moments = relationship("Moment", back_populates="author", cascade="all, delete-orphan")
    diaries = relationship("Diary", back_populates="author", cascade="all, delete-orphan")
    comments = relationship("Comment", back_populates="author", cascade="all, delete-orphan")
    balance = Column(Integer, nullable=False, default=0)         # 余额（整数金币，兼容旧逻辑）
    balance_cents = Column(Integer, nullable=False, default=0)   # 余额（单位：分，1金币=100分，支持小数）
    vip_expire = Column(DateTime, nullable=True)               # 会员到期时间
    page_bg = Column(String(255), nullable=False, default="")  # 会员整页背景图URL
    chat_bubble = Column(String(30), nullable=False, default="") # 会员聊天气泡样式
    bio = Column(String(200), nullable=False, default="")       # 个性标签(最多80字)
    is_banned = Column(Boolean, nullable=False, default=False, index=True)   # 是否封禁
    ban_reason = Column(String(200), nullable=False, default="")  # 封禁理由
    ban_until = Column(DateTime, nullable=True)  # 解封时间（null=永久）


class Moment(Base):
    """朋友圈一条动态"""
    __tablename__ = "moments"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    title = Column(String(100), nullable=False, default="")
    content = Column(Text, nullable=False, default="")
    images = Column(Text, nullable=False, default="")   # 多张图片 URL，逗号分隔
    videos = Column(Text, nullable=False, default="")   # 多个视频 URL，逗号分隔
    channel_id = Column(Integer, nullable=False, default=0, index=True)
    lang = Column(String(16), nullable=False, default="", index=False)  # 代码块语言；空=普通文字
    tags = Column(String(120), nullable=False, default="", index=True)  # v2.1 分类标签，逗号分隔（教程/求助/分享/讨论）
    allow_download = Column(Integer, nullable=False, default=0)  # 图片是否允许下载：0=不可下载(默认) 1=可下载
    status = Column(String(10), nullable=False, default="pending", index=True)  # pending待审 approved通过 rejected驳回
    # FAV_VIEW_V1: 浏览量（详情页被打开一次 +1）
    view_count = Column(Integer, nullable=False, default=0, server_default="0")
    created_at = Column(DateTime, default=datetime.utcnow, index=True)

    author = relationship("User", back_populates="moments")
    comments = relationship("Comment", back_populates="moment", cascade="all, delete-orphan")
    likes = relationship("MomentLike", back_populates="moment", cascade="all, delete-orphan")


class Comment(Base):
    """动态下的一条评论"""
    __tablename__ = "comments"

    id = Column(Integer, primary_key=True, index=True)
    moment_id = Column(Integer, ForeignKey("moments.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    content = Column(Text, nullable=False)
    lang = Column(String(16), nullable=False, default="", index=False)  # 代码块语言；空=普通文字
    parent_id = Column(Integer, nullable=True)   # 回复哪条评论（可空）
    reply_to_id = Column(Integer, nullable=True)
    reply_to_nickname = Column(String(64), nullable=True, default="")

    created_at = Column(DateTime, default=datetime.utcnow, index=True)

    moment = relationship("Moment", back_populates="comments")
    author = relationship("User", back_populates="comments")
    likes = relationship("CommentLike", back_populates="comment", cascade="all, delete-orphan")


class Diary(Base):
    """一篇日记"""
    __tablename__ = "diaries"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    title = Column(String(120), nullable=False, default="")
    content = Column(Text, nullable=False, default="")
    mood = Column(String(20), nullable=True)          # 可选：心情
    weather = Column(String(60), nullable=True)       # 可选：天气文本（如 "☀ 北京 26℃ 晴"）
    diary_date = Column(String(10), nullable=True)    # 可选：YYYY-MM-DD，日记归属日
    images = Column(Text, nullable=False, default="")  # 图片 URL，逗号分隔（最多 5 张）
    video = Column(String(255), nullable=False, default="")  # 视频 URL（单个，最大 10MB）
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    author = relationship("User", back_populates="diaries")
    save_mode = Column(String(20), nullable=False, default="cloud")  # cloud/local/both


class Announcement(Base):
    """后台发布的公告"""
    __tablename__ = "announcements"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(120), nullable=False, default="")
    content = Column(Text, nullable=False, default="")
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    target_version = Column(String(20), nullable=False, default="")  # 指定版本(空=所有版本)


class MomentLike(Base):
    """用户对某条动态的点赞（同一用户对同一动态只能赞一次）"""
    __tablename__ = "moment_likes"
    __table_args__ = (UniqueConstraint("moment_id", "user_id", name="uq_moment_like"),)
    id = Column(Integer, primary_key=True, index=True)
    moment_id = Column(Integer, ForeignKey("moments.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    moment = relationship("Moment", back_populates="likes")
    user = relationship("User")


class CommentLike(Base):
    """用户对某条评论的点赞"""
    __tablename__ = "comment_likes"
    __table_args__ = (UniqueConstraint("comment_id", "user_id", name="uq_comment_like"),)
    id = Column(Integer, primary_key=True, index=True)
    comment_id = Column(Integer, ForeignKey("comments.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    comment = relationship("Comment", back_populates="likes")
    user = relationship("User")


class Notification(Base):
    """消息通知：别人点赞/评论了我（type: moment_like / comment_like / comment / reply）"""
    __tablename__ = "notifications"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)  # 接收者
    actor_id = Column(Integer, ForeignKey("users.id"), nullable=False)              # 触发者
    type = Column(String(20), nullable=False, index=True)
    moment_id = Column(Integer, nullable=True)
    comment_id = Column(Integer, nullable=True)
    content = Column(String(255), nullable=False, default="")  # 摘要文本
    is_read = Column(Boolean, nullable=False, default=False, index=True)
    is_pinned = Column(Boolean, nullable=False, default=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class Friendship(Base):
    """好友关系：一条记录代表一对用户之间的关系"""
    __tablename__ = "friendships"
    __table_args__ = (UniqueConstraint("user_id", "friend_id", name="uq_friend_pair"),)
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)   # 发起方
    friend_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True) # 接收方
    status = Column(String(12), nullable=False, default="pending", index=True)  # pending/accepted/rejected
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class ChatMessage(Base):
    """私聊消息（文字 / 图片 / 视频）"""
    __tablename__ = "chat_messages"
    id = Column(Integer, primary_key=True, index=True)
    sender_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    receiver_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    kind = Column(String(10), nullable=False, default="text")  # text/image/video
    content = Column(Text, nullable=False, default="")         # 文本内容 或 媒体 URL
    is_read = Column(Boolean, nullable=False, default=False, index=True)
    is_recalled = Column(Boolean, nullable=False, default=False)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class TaskLog(Base):
    """任务/积分流水：记录用户完成的任务与获得的经验"""
    __tablename__ = "task_logs"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    task_key = Column(String(40), nullable=False, index=True)  # sign/daily_post/comment/chat...
    exp = Column(Integer, nullable=False, default=0)           # 本次获得经验
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class EmailCode(Base):
    """邮箱验证码（注册 / 重置密码 / 换绑邮箱 / 登录验证）"""
    __tablename__ = "email_codes"
    id = Column(Integer, primary_key=True, index=True)
    email = Column(String(120), nullable=False, index=True)
    code = Column(String(10), nullable=False)
    purpose = Column(String(20), nullable=False, default="register", index=True)
    used = Column(Boolean, nullable=False, default=False)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class ChatBlacklist(Base):
    """聊天黑名单：owner 拉黑 target 后，target 不能给 owner 发消息，双方互不可见"""
    __tablename__ = "chat_blacklist"
    id = Column(Integer, primary_key=True, index=True)
    owner_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    target_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow)


class ChatPin(Base):
    """聊天会话置顶：owner 把 target 的会话置顶"""
    __tablename__ = "chat_pins"
    id = Column(Integer, primary_key=True, index=True)
    owner_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    target_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow)


class Channel(Base):
    """频道：用户创建，需管理员审核后才公开"""
    __tablename__ = "channels"
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(60), nullable=False, default="")
    intro = Column(String(300), nullable=False, default="")
    image = Column(String(255), nullable=False, default="")
    owner_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    status = Column(String(20), nullable=False, default="pending")  # pending/approved/rejected
    member_count = Column(Integer, nullable=False, default=1)
    created_at = Column(DateTime, default=datetime.utcnow)


class ChannelMember(Base):
    """频道成员"""
    __tablename__ = "channel_members"
    id = Column(Integer, primary_key=True, index=True)
    channel_id = Column(Integer, ForeignKey("channels.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow)


class AppConfig(Base):
    """全局配置（key-value），用于版本管理/强制更新等"""
    __tablename__ = "app_config"
    id = Column(Integer, primary_key=True, index=True)
    key = Column(String(60), unique=True, index=True, nullable=False)
    value = Column(Text, nullable=False, default="")


class UserDevice(Base):
    """登录设备（用于多设备限制；会员3台，普通1台）"""
    __tablename__ = "user_devices"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    device = Column(String(120), nullable=False, default="")
    last_active = Column(DateTime, default=datetime.utcnow)


# ---------- 应用商店（v9.0 新增） ----------
class AppItem(Base):
    """用户上传的应用（软件）。待审核通过后才公开。"""
    __tablename__ = "app_items"
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(80), nullable=False, default="")            # 应用名
    package_name = Column(String(120), nullable=False, default="")   # 包名（可选）
    version = Column(String(40), nullable=False, default="")         # 版本号
    summary = Column(String(200), nullable=False, default="")        # 一句话简介
    description = Column(Text, nullable=False, default="")           # 详细描述
    icon = Column(String(255), nullable=False, default="")           # 图标 URL
    screenshots = Column(Text, nullable=False, default="")           # 截图 URL 列表（JSON 字符串）
    category = Column(String(40), nullable=False, default="")        # 分类
    # 上传方式：direct=直传服务器（会员）/ external=外链
    upload_type = Column(String(20), nullable=False, default="external")
    apk_url = Column(String(500), nullable=False, default="")         # 安装包地址（直传或外链）
    apk_size = Column(Integer, nullable=False, default=0)             # 安装包大小（字节）
    ext_link = Column(String(500), nullable=False, default="")        # 外链下载地址
    # 审核状态：pending/approved/rejected
    status = Column(String(20), nullable=False, default="pending")
    reject_reason = Column(String(200), nullable=False, default="")
    download_count = Column(Integer, nullable=False, default=0)
    view_count = Column(Integer, nullable=False, default=0)
    uploader_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    is_paid = Column(Boolean, nullable=False, default=False)
    price = Column(Integer, nullable=False, default=0)
    uploader = relationship("User")


class AppComment(Base):
    """应用评论"""
    __tablename__ = "app_comments"
    id = Column(Integer, primary_key=True, index=True)
    app_id = Column(Integer, ForeignKey("app_items.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    content = Column(String(500), nullable=False, default="")
    rating = Column(Integer, nullable=False, default=5)  # 1-5 星
    created_at = Column(DateTime, default=datetime.utcnow)
    user = relationship("User")

class Follow(Base):
    """关注关系：follower_id 关注了 followee_id（单向）"""
    __tablename__ = "follows"
    __table_args__ = (UniqueConstraint("follower_id", "followee_id", name="uq_follow_pair"),)
    id = Column(Integer, primary_key=True, index=True)
    follower_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    followee_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class PrivateMessage(Base):
    """私信（仅文字）：未互关时每人仅可发 1 条"""
    __tablename__ = "private_messages"
    id = Column(Integer, primary_key=True, index=True)
    sender_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    receiver_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    content = Column(Text, nullable=False, default="")
    is_read = Column(Boolean, nullable=False, default=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)

class Reviewer(Base):
    """审核员（独立表，不属于普通用户）"""
    __tablename__ = "reviewers"
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(50), unique=True, index=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    nickname = Column(String(50), nullable=False, default="")
    active = Column(Boolean, nullable=False, default=True)
    created_by = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)
    last_login = Column(DateTime, nullable=True)


class Report(Base):
    """举报（帖子/应用/频道/评论/用户）"""
    __tablename__ = "reports"
    id = Column(Integer, primary_key=True, index=True)
    reporter_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    target_type = Column(String(20), nullable=False, default="moment", index=True)  # moment/app/channel/comment/user
    target_id = Column(Integer, nullable=False, default=0, index=True)
    reason = Column(String(200), nullable=False, default="")
    detail = Column(Text, nullable=False, default="")
    status = Column(String(20), nullable=False, default="pending", index=True)  # pending/handled/dismissed
    handled_by = Column(Integer, nullable=False, default=0)  # reviewer id
    created_at = Column(DateTime, default=datetime.utcnow, index=True)



class Ad(Base):
    __tablename__ = "ads"
    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(120), nullable=False, default="")
    image = Column(String(500), nullable=False, default="")
    link = Column(String(500), nullable=False, default="")
    sort = Column(Integer, nullable=False, default=0, index=True)
    active = Column(Boolean, nullable=False, default=True, index=True)
    start_at = Column(DateTime, nullable=True)
    end_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class Faq(Base):
    __tablename__ = "faqs"
    id = Column(Integer, primary_key=True, index=True)
    question = Column(String(255), nullable=False, default="")
    answer = Column(Text, nullable=False, default="")
    category = Column(String(50), nullable=False, default="", index=True)
    sort = Column(Integer, nullable=False, default=0, index=True)
    pinned = Column(Boolean, nullable=False, default=False, index=True)
    active = Column(Boolean, nullable=False, default=True, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


# ==================== 卡密兑换系统 ====================
class RedeemCode(Base):
    """卡密：kind=balance 加余额 / kind=vip 加会员天数"""
    __tablename__ = "redeem_codes"
    id = Column(Integer, primary_key=True, index=True)
    code = Column(String(64), unique=True, index=True, nullable=False)   # 卡密（自定义格式）
    kind = Column(String(16), nullable=False, default="balance", index=True)  # balance / vip
    amount = Column(Integer, nullable=False, default=0)   # 余额金额 或 会员天数
    batch = Column(String(64), nullable=False, default="", index=True)    # 批次号
    remark = Column(String(255), nullable=False, default="")              # 备注
    expire_at = Column(DateTime, nullable=True)           # 卡密本身有效期（NULL=永久）
    status = Column(Integer, nullable=False, default=0, index=True)       # 0未用 1已用 2已作废
    used_by = Column(Integer, ForeignKey("users.id"), nullable=True)      # 使用者
    used_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class RedeemLog(Base):
    """兑换流水"""
    __tablename__ = "redeem_logs"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    code = Column(String(64), nullable=False, default="")
    kind = Column(String(16), nullable=False, default="balance")
    amount = Column(Integer, nullable=False, default=0)
    detail = Column(String(255), nullable=False, default="")
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class DirectMessage(Base):
    """定向消息：后台向指定社区账号发送的自定义消息（标题+内容）"""
    __tablename__ = "direct_messages"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)  # 接收者
    title = Column(String(200), nullable=False, default="")
    content = Column(Text, nullable=False, default="")
    sender = Column(String(64), nullable=False, default="系统")   # 发送方显示名
    is_read = Column(Boolean, nullable=False, default=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)




class MomentTip(Base):
    """帖子打赏：某用户对某动态打赏了若干金币"""
    __tablename__ = "moment_tips"
    id = Column(Integer, primary_key=True, index=True)
    moment_id = Column(Integer, ForeignKey("moments.id"), nullable=False, index=True)
    from_user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    to_user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    amount = Column(Integer, nullable=False, default=0)   # 金币数
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class BalanceLog(Base):
    """金币账户流水（收入/支出）"""
    __tablename__ = "balance_logs"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    delta = Column(Integer, nullable=False, default=0)     # 正=收入 负=支出
    kind = Column(String(24), nullable=False, default="")  # tip_in/tip_out/redeem/vip_cost/recharge
    detail = Column(String(255), nullable=False, default="")
    moment_id = Column(Integer, nullable=False, default=0)  # 关联帖子(可选)
    balance_after = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)



class AppPurchase(Base):
    # App purchase record (charged every time for paid apps)
    __tablename__ = 'app_purchases'
    id = Column(Integer, primary_key=True, index=True)
    app_id = Column(Integer, ForeignKey('app_items.id'), nullable=False, index=True)
    buyer_id = Column(Integer, ForeignKey('users.id'), nullable=False, index=True)
    seller_id = Column(Integer, ForeignKey('users.id'), nullable=False, index=True)
    price = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)

class AppTip(Base):
    # App tip record
    __tablename__ = 'app_tips'
    id = Column(Integer, primary_key=True, index=True)
    app_id = Column(Integer, ForeignKey('app_items.id'), nullable=False, index=True)
    from_user_id = Column(Integer, ForeignKey('users.id'), nullable=False, index=True)
    to_user_id = Column(Integer, ForeignKey('users.id'), nullable=False, index=True)
    amount = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)

class Tool(Base):
    # Tool / website that can be embedded in app
    __tablename__ = 'tools'
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(80), nullable=False, default="")
    cover = Column(String(255), nullable=False, default="")
    intro = Column(String(300), nullable=False, default="")
    url = Column(String(500), nullable=False, default="")
    status = Column(String(20), nullable=False, default='pending')
    reject_reason = Column(String(200), nullable=False, default="")
    use_count = Column(Integer, nullable=False, default=0)
    uploader_id = Column(Integer, ForeignKey('users.id'), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    uploader = relationship('User')

class SplashAd(Base):
    """开屏广告（后台可配，App 启动时全屏展示，可跳过）"""
    __tablename__ = "splash_ads"
    id = Column(Integer, primary_key=True, index=True)
    image = Column(String(255), nullable=False, default="")      # 广告图 URL
    link = Column(String(500), nullable=False, default="")       # 点击跳转链接（外部浏览器）
    title = Column(String(100), nullable=False, default="")      # 备注名
    skip_seconds = Column(Integer, nullable=False, default=3)    # 跳过倒计时秒数
    enabled = Column(Boolean, nullable=False, default=True)      # 是否启用
    sort = Column(Integer, nullable=False, default=0)            # 权重（大在前）
    start_at = Column(DateTime, nullable=True)                   # 生效开始（空=立即）
    end_at = Column(DateTime, nullable=True)                     # 生效结束（空=永久）
    view_count = Column(Integer, nullable=False, default=0)      # 展示次数
    click_count = Column(Integer, nullable=False, default=0)     # 点击次数
    created_at = Column(DateTime, default=datetime.utcnow)

class ResourceItem(Base):
    """用户上传的资源（文件 / 链接）。待审核通过后才公开。"""
    __tablename__ = "resource_items"
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(120), nullable=False, default="")             # 资源名称
    summary = Column(String(300), nullable=False, default="")          # 简介
    description = Column(Text, nullable=False, default="")             # 详细说明
    category = Column(String(40), nullable=False, default="")          # 类别
    avatar = Column(String(255), nullable=False, default="")           # 上传者头像快照
    cover = Column(String(255), nullable=False, default="")            # 封面图
    screenshots = Column(Text, nullable=False, default="")             # 演示截图 JSON
    # 资源形式：link=下载链接 / file=直传文件
    res_type = Column(String(20), nullable=False, default="link")
    download_url = Column(String(600), nullable=False, default="")     # 下载链接 / 文件地址
    file_size = Column(Integer, nullable=False, default=0)             # 文件大小（字节）
    # 付费：0=免费 1=付费
    is_paid = Column(Boolean, nullable=False, default=False)
    price = Column(Integer, nullable=False, default=0)                 # 价格（金币）
    # 审核：pending/approved/rejected
    status = Column(String(20), nullable=False, default="pending")
    reject_reason = Column(String(300), nullable=False, default="")
    download_count = Column(Integer, nullable=False, default=0)
    view_count = Column(Integer, nullable=False, default=0)
    tip_total = Column(Integer, nullable=False, default=0)             # 累计打赏（金币）
    uploader_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    uploader = relationship("User")


class ResourceTip(Base):
    """资源打赏记录"""
    __tablename__ = "resource_tips"
    id = Column(Integer, primary_key=True, index=True)
    resource_id = Column(Integer, ForeignKey("resource_items.id"), nullable=False, index=True)
    from_user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    to_user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    amount = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class ResourceUnlock(Base):
    """付费资源购买记录"""
    __tablename__ = "resource_unlocks"
    id = Column(Integer, primary_key=True, index=True)
    resource_id = Column(Integer, ForeignKey("resource_items.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    price = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)


class LoginLog(Base):
    """登录日志：记录管理员 / 审核员 / 普通用户的登录行为"""
    __tablename__ = "login_logs"
    id = Column(Integer, primary_key=True, index=True)
    # 角色：admin=管理员 reviewer=审核员 user=普通用户
    role = Column(String(20), nullable=False, default="user", index=True)
    user_id = Column(Integer, nullable=False, default=0, index=True)
    username = Column(String(80), nullable=False, default="")
    ip = Column(String(64), nullable=False, default="")
    user_agent = Column(String(400), nullable=False, default="")
    ok = Column(Boolean, nullable=False, default=True)
    fail_reason = Column(String(120), nullable=False, default="")
    platform = Column(String(30), nullable=False, default="")   # web / android
    created_at = Column(DateTime, default=datetime.utcnow, index=True)


class ReviewerAgreement(Base):
    """审核员协议签署记录（每次签署留档）"""
    __tablename__ = "reviewer_agreements"
    id = Column(Integer, primary_key=True, index=True)
    reviewer_id = Column(Integer, ForeignKey("reviewers.id"), nullable=False, index=True)
    version = Column(String(20), nullable=False, default="v1")
    agreed = Column(Boolean, nullable=False, default=True)
    ip = Column(String(64), nullable=False, default="")
    user_agent = Column(String(400), nullable=False, default="")
    signed_at = Column(DateTime, default=datetime.utcnow, index=True)

# ============================================================
# FAV_VIEW_V1: 收藏功能
# 与「点赞」的区别：点赞公开可见用于表达认可，收藏是私密的个人书签。
# 三张表分别对应 帖子 / 应用 / 资源，用 (user_id, target_id) 唯一约束防重复。
# ============================================================

class MomentFavorite(Base):
    """用户收藏的帖子"""
    __tablename__ = "moment_favorites"
    __table_args__ = (UniqueConstraint("moment_id", "user_id", name="uq_moment_fav"),)
    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    moment_id = Column(Integer, ForeignKey("moments.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    moment = relationship("Moment")
    user = relationship("User")


class AppFavorite(Base):
    """用户收藏的应用"""
    __tablename__ = "app_favorites"
    __table_args__ = (UniqueConstraint("app_id", "user_id", name="uq_app_fav"),)
    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    app_id = Column(Integer, ForeignKey("app_items.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    user = relationship("User")


class ResourceFavorite(Base):
    """用户收藏的资源"""
    __tablename__ = "resource_favorites"
    __table_args__ = (UniqueConstraint("resource_id", "user_id", name="uq_resource_fav"),)
    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    resource_id = Column(Integer, ForeignKey("resource_items.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    user = relationship("User")


# ============================================================
# RES_COMMENT_V1: 资源评论（对齐 AppComment，但不带星级评分）
# ============================================================
class ResourceComment(Base):
    """资源评论"""
    __tablename__ = "resource_comments"
    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    resource_id = Column(Integer, ForeignKey("resource_items.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    content = Column(String(500), nullable=False, default="")
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    user = relationship("User")


# ============================================================
# UID_V1: 8 位数字 UID 生成器（1 开头 + 7 位随机）
# ============================================================
import random as _random

def gen_uid() -> str:
    return "1" + "".join(_random.choice("0123456789") for _ in range(7))

def gen_unique_uid(db, model=None, max_try: int = 30) -> str:
    M = model if model is not None else User
    for _ in range(max_try):
        cand = gen_uid()
        try:
            exists = db.query(M).filter(M.uid == cand).first()
        except Exception:
            return cand
        if not exists:
            return cand
    import time as _t
    return "1" + str(int(_t.time() * 1000))[-7:]


# ============================================================
# CAPTCHA_V1: 人机验证挑战（数学题），一次性使用、5 分钟过期
# ============================================================
class CaptchaChallenge(Base):
    __tablename__ = "captcha_challenges"
    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    token = Column(String(64), nullable=False, unique=True, index=True)
    answer = Column(Integer, nullable=False, default=0)
    used = Column(Boolean, nullable=False, default=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
