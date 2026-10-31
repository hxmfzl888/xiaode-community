package com.xiaode.community.data

import com.google.gson.annotations.SerializedName

// ---------- 认证 ----------
data class RegisterIn(
    val username: String,
    val password: String,
    val nickname: String? = null,
    val email: String? = null,
    val code: String? = null,
)

data class LoginIn(val username: String, val password: String)

data class TokenOut(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
    val user: UserOut,
)

data class SendCodeIn(val email: String, val purpose: String = "register")

data class ResetPasswordIn(
    val email: String,
    val code: String,
    @SerializedName("new_password") val newPassword: String,
)

data class ChangePasswordIn(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String,
)

data class ChangeUsernameIn(
    @SerializedName("new_username") val newUsername: String,
    val password: String,
)

data class ChangeEmailIn(
    @SerializedName("new_email") val newEmail: String,
    val code: String,
)

// ---------- 用户 ----------
data class UserOut(
    val id: Int,
    val username: String,
    val nickname: String = "",
    val avatar: String = "",
    val email: String = "",
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    val exp: Int = 0,
    val level: Int = 1,
    @SerializedName("name_color") val nameColor: String = "",
    @SerializedName("text_effect") val textEffect: String = "",
    @SerializedName("is_vip") val isVip: Boolean = false,
    @SerializedName("vip_expire") val vipExpire: String? = null,
    val balance: Double = 0.0,
    @SerializedName("balance_cents") val balanceCents: Int = 0,
    @SerializedName("page_bg") val pageBg: String = "",
    @SerializedName("chat_bubble") val chatBubble: String = "",
    val bio: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)

data class ProfileUpdateIn(val nickname: String? = null, val bio: String? = null)

data class ChatSettingIn(
    @SerializedName("chat_theme") val chatTheme: String? = null,
    @SerializedName("chat_bg") val chatBg: String? = null,
    @SerializedName("name_color") val nameColor: String? = null,
    @SerializedName("text_effect") val textEffect: String? = null,
)

// ---------- 广场 ----------
data class MomentCreateIn(
    val title: String = "",
    val content: String = "",
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    @SerializedName("channel_id") val channelId: Int = 0,
    val lang: String = "",
    @SerializedName("allow_download") val allowDownload: Boolean = false,
    // v2.1: 分类标签（教程/求助/分享/讨论，最多 3 个）
    val tags: List<String> = emptyList(),
)

data class MomentOut(
    val id: Int,
    val title: String = "",
    val content: String = "",
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    val status: String = "pending",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("user_id") val userId: Int = 0,
    val nickname: String = "",
    val username: String = "",
    val avatar: String = "",
    @SerializedName("comment_count") val commentCount: Int = 0,
    @SerializedName("like_count") val likeCount: Int = 0,
    val liked: Boolean = false,
    val level: Int = 1,
    @SerializedName("level_effect") val levelEffect: String = "normal",
    @SerializedName("channel_id") val channelId: Int = 0,
    @SerializedName("channel_name") val channelName: String = "",
    val bio: String? = null,
    val lang: String = "",
    @SerializedName("allow_download") val allowDownload: Boolean = false,
    @SerializedName("is_vip") val isVip: Boolean = false,
    // v2.1: 分类标签
    val tags: List<String> = emptyList(),
    // FAV_VIEW_V1: 浏览量 + 是否已收藏
    @SerializedName("view_count") val viewCount: Int = 0,
    val favorited: Boolean = false,
    @SerializedName("comments") private val _comments: List<CommentOut>? = emptyList(),
) {
    // Gson 可能把 null 写进非空 List，这里统一兜底，避免 NPE
    val comments: List<CommentOut> get() = _comments ?: emptyList()
}

data class CommentOut(
    val id: Int,
    val content: String = "",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("user_id") val userId: Int = 0,
    val nickname: String = "",
    val username: String = "",
    val avatar: String = "",
    @SerializedName("parent_id") val parentId: Int? = null,
    @SerializedName("reply_to_id") val replyToId: Int? = null,
    @SerializedName("reply_to_nickname") val replyToNickname: String = "",
    val lang: String = "",
    @SerializedName("like_count") val likeCount: Int = 0,
    val liked: Boolean = false,
    val level: Int = 1,
    @SerializedName("level_effect") val levelEffect: String = "normal",
    @SerializedName("is_vip") val isVip: Boolean = false,
    @SerializedName("replies") private val _replies: List<CommentOut>? = emptyList(),
) {
    // Gson 可能把 null 写进非空 List，这里统一兜底，避免 NPE
    val replies: List<CommentOut> get() = _replies ?: emptyList()
}

data class CommentCreateIn(val content: String, @SerializedName("parent_id") val parentId: Int? = null, @SerializedName("reply_to_id") val replyToId: Int? = null, val lang: String = "")

data class LikeResult(val liked: Boolean = false, @SerializedName("like_count") val likeCount: Int = 0)

// ---------- 频道 ----------
data class Channel(
    val id: Int,
    val name: String = "",
    val intro: String = "",
    val image: String = "",
    @SerializedName("owner_id") val ownerId: Int = 0,
    @SerializedName("owner_name") val ownerName: String = "",
    val status: String = "pending",
    @SerializedName("member_count") val memberCount: Int = 0,
    val joined: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
)

data class ChannelCreateIn(val name: String, val intro: String = "", val image: String = "")

data class JoinResult(val ok: Boolean = true, val joined: Boolean = false)

// ---------- 消息通知 ----------
data class NotificationOut(
    val id: Int,
    val type: String = "",
    @SerializedName("moment_id") val momentId: Int? = null,
    @SerializedName("comment_id") val commentId: Int? = null,
    val content: String = "",
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("is_pinned") val isPinned: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("actor_id") val actorId: Int = 0,
    @SerializedName("actor_nickname") val actorNickname: String = "",
    @SerializedName("actor_username") val actorUsername: String = "",
    @SerializedName("actor_avatar") val actorAvatar: String = "",
)

data class NotificationListOut(
    val total: Int = 0,
    val unread: Int = 0,
    val items: List<NotificationOut> = emptyList(),
)

data class UnreadCount(val unread: Int = 0)

// ---------- 聊天 ----------
data class FriendOut(
    val id: Int,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val level: Int = 1,
    @SerializedName("level_effect") val levelEffect: String = "normal",
    val unread: Int = 0,
)

data class FriendReqOut(
    val id: Int,
    @SerializedName("user_id") val userId: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val level: Int = 1,
    val message: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)

data class FriendRequestIn(val username: String)

data class UserLevelOut(
    val id: Int,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val exp: Int = 0,
    val level: Int = 1,
    @SerializedName("level_effect") val levelEffect: String = "normal",
    @SerializedName("name_color") val nameColor: String = "",
    @SerializedName("text_effect") val textEffect: String = "",
    @SerializedName("next_exp") val nextExp: Int = 0,
    @SerializedName("can_video") val canVideo: Boolean = false,
    @SerializedName("is_friend") val isFriend: Boolean = false,
    @SerializedName("friend_status") val friendStatus: String = "",
)

data class ChatSendIn(
    val kind: String = "text",
    val content: String = "",
    @SerializedName("receiver_username") val receiverUsername: String? = null,
    @SerializedName("receiver_id") val receiverId: Int? = null,
)

data class ChatMsgOut(
    val id: Int,
    @SerializedName("sender_id") val senderId: Int = 0,
    @SerializedName("receiver_id") val receiverId: Int = 0,
    val kind: String = "text",
    val content: String = "",
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("is_recalled") val isRecalled: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("sender_name") val senderName: String = "",
    @SerializedName("sender_username") val senderUsername: String = "",
    @SerializedName("sender_avatar") val senderAvatar: String = "",
    @SerializedName("sender_level") val senderLevel: Int = 1,
    @SerializedName("sender_effect") val senderEffect: String = "normal",
    @SerializedName("sender_is_vip") val senderIsVip: Boolean = false,
)

data class ChatConvOut(
    @SerializedName("peer_id") val peerId: Int = 0,
    @SerializedName("peer_username") val peerUsername: String = "",
    @SerializedName("peer_nickname") val peerNickname: String = "",
    @SerializedName("peer_avatar") val peerAvatar: String = "",
    @SerializedName("peer_level") val peerLevel: Int = 1,
    @SerializedName("last_kind") val lastKind: String = "text",
    @SerializedName("last_content") val lastContent: String = "",
    @SerializedName("last_at") val lastAt: String? = null,
    val unread: Int = 0,
)

data class BlackUser(val id: Int = 0, val username: String = "", val nickname: String = "", val avatar: String = "")
data class PinsOut(val pins: List<Int> = emptyList())
data class BlockedResult(val ok: Boolean = true, val blocked: Boolean = false)
data class PinnedResult(val ok: Boolean = true, val pinned: Boolean = false)

// ---------- 会员 ----------
data class VipPlan(
    val key: String,
    val name: String,
    val price: Double = 0.0,
    val days: Int = 0,
    val badge: String = "",
    val intro: String = "",
)
data class VipInfo(
    @SerializedName("is_vip") val isVip: Boolean = false,
    @SerializedName("vip_expire") val vipExpire: String = "",
    val balance: Double = 0.0,
    @SerializedName("balance_cents") val balanceCents: Int = 0,
    @SerializedName("page_bg") val pageBg: String = "",
    @SerializedName("chat_bubble") val chatBubble: String = "",
    val plans: List<VipPlan> = emptyList(),
)

data class VipBuyIn(val plan: String)
data class BubbleStyle(val key: String, val name: String)
data class BgResult(val ok: Boolean = true, val url: String = "")

// ---------- 等级/任务 ----------
data class TaskItem(
    val key: String,
    val name: String,
    val exp: Int = 0,
    val limit: Int = 0,
    val done: Int = 0,
    val desc: String = "",
)

data class TaskListOut(
    val exp: Int = 0,
    val level: Int = 1,
    @SerializedName("level_effect") val levelEffect: String = "normal",
    @SerializedName("next_exp") val nextExp: Int = 0,
    @SerializedName("cur_level_exp") val curLevelExp: Int = 0,
    @SerializedName("signed_today") val signedToday: Boolean = false,
    @SerializedName("can_video") val canVideo: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
)

data class SignResult(
    val ok: Boolean = true,
    val gained: Int = 0,
    val exp: Int = 0,
    val level: Int = 1,
    val msg: String = "",
    val coin: Double = 0.0,
    val balance: Double = 0.0,
    @SerializedName("balance_cents") val balanceCents: Int = 0,
)

// ---------- v2.1 排行榜 ----------
data class RankItemOut(
    val rank: Int = 0,
    @SerializedName("user_id") val userId: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val level: Int = 1,
    @SerializedName("level_effect") val levelEffect: String = "normal",
    @SerializedName("name_color") val nameColor: String = "",
    @SerializedName("text_effect") val textEffect: String = "",
    @SerializedName("is_vip") val isVip: Boolean = false,
    val score: Int = 0,
    @SerializedName("is_me") val isMe: Boolean = false,
    val followed: Boolean = false,
)

data class RankOut(
    val type: String = "sign",          // sign | post
    val range: String = "all",          // all | day | week
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("my_rank") val myRank: Int = 0,
    @SerializedName("my_score") val myScore: Double = 0.0,
    val items: List<RankItemOut> = emptyList(),
)

// ---------- 公告 ----------
data class AnnouncementOut(
    val id: Int,
    val title: String = "",
    val content: String = "",
    @SerializedName("target_version") val targetVersion: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)

// ---------- 通用 ----------
data class OkMsg(val ok: Boolean = true, val msg: String = "")
data class UploadResult(val ok: Boolean = true, val url: String = "")

// ---------- 游戏第三方登录 ----------
data class GameTokenOut(
    val code: Int = 0,
    val msg: String = "",
    val data: GameTokenData? = null,
)

data class GameTokenData(
    val token: String = "",
    val player: GamePlayer? = null,
)

data class GamePlayer(
    val uid: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val level: Int = 1,
    val exp: Int = 0,
    val avatar: String = "",
    @SerializedName("is_vip") val isVip: Boolean = false,
)

// ==================== v9.0 应用商店 ====================
data class AppOut(
    val id: Int,
    val name: String = "",
    @SerializedName("package_name") val packageName: String = "",
    val version: String = "",
    val summary: String = "",
    val description: String = "",
    val icon: String = "",
    val screenshots: List<String> = emptyList(),
    val category: String = "",
    @SerializedName("upload_type") val uploadType: String = "external",
    @SerializedName("apk_url") val apkUrl: String = "",
    @SerializedName("apk_size") val apkSize: Long = 0,
    @SerializedName("ext_link") val extLink: String = "",
    val status: String = "pending",
    @SerializedName("reject_reason") val rejectReason: String = "",
    @SerializedName("download_count") val downloadCount: Int = 0,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("uploader_id") val uploaderId: Int = 0,
    @SerializedName("uploader_name") val uploaderName: String = "",
    @SerializedName("is_paid") val isPaid: Boolean = false,
    val price: Int = 0,
    @SerializedName("tip_total") val tipTotal: Int = 0,
    @SerializedName("created_at") val createdAt: String = "",
)

data class AppCreateIn(
    val name: String,
    @SerializedName("package_name") val packageName: String = "",
    val version: String = "",
    val summary: String = "",
    val description: String = "",
    val icon: String = "",
    val screenshots: List<String> = emptyList(),
    val category: String = "",
    @SerializedName("upload_type") val uploadType: String = "external",
    @SerializedName("ext_link") val extLink: String = "",
    @SerializedName("apk_url") val apkUrl: String = "",
        @SerializedName("apk_size") val apkSize: Long = 0,
    @SerializedName("is_paid") val isPaid: Boolean = false,
    val price: Int = 0,
)

data class AppCommentOut(
    val id: Int,
    @SerializedName("app_id") val appId: Int = 0,
    @SerializedName("user_id") val userId: Int = 0,
    val nickname: String = "",
    val avatar: String = "",
    val content: String = "",
    val rating: Int = 5,
    @SerializedName("created_at") val createdAt: String = "",
)

data class AppCommentIn(val content: String, val rating: Int = 5)


/** RES_COMMENT_V1: 资源评论（无星级） */
data class ResourceCommentOut(
    val id: Int,
    @SerializedName("resource_id") val resourceId: Int = 0,
    @SerializedName("user_id") val userId: Int = 0,
    val nickname: String = "",
    val avatar: String = "",
    val content: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)

data class ResourceCommentIn(val content: String)

data class DownloadResult(
    val ok: Boolean = true,
    @SerializedName("download_count") val downloadCount: Int = 0,
    val url: String = "",
)

// ==================== v9.0 版本检查 ====================
data class VersionInfo(
    @SerializedName("latest_version") val latestVersion: String = "",
    @SerializedName("min_version") val minVersion: String = "",
    val force: Boolean = false,
    @SerializedName("download_url") val downloadUrl: String = "",
    val changelog: String = "",
    val notice: String = "",
)

// v9.0 注销账号
data class DeleteAccountIn(val password: String)

// ==================== v9.1 社交/关注/私信/举报 ====================
data class CardUserOut(
    val id: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val level: Int = 1,
    @SerializedName("level_effect") val levelEffect: String = "normal",
    @SerializedName("is_vip") val isVip: Boolean = false,
    val followed: Boolean = false,
)
/** 用户主页内容分页包装 */
data class MomentPage(
    val items: List<MomentOut> = emptyList(),
    val total: Int = 0,
    @SerializedName("has_more") val hasMore: Boolean = false,
)

data class ResourcePage(
    val items: List<ResourceOut> = emptyList(),
    val total: Int = 0,
    @SerializedName("has_more") val hasMore: Boolean = false,
)

data class AppPage(
    val items: List<AppOut> = emptyList(),
    val total: Int = 0,
    @SerializedName("has_more") val hasMore: Boolean = false,
)

data class SocialUserOut(
    val id: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val exp: Int = 0,
    val level: Int = 1,
    @SerializedName("level_effect") val levelEffect: String = "normal",
    @SerializedName("is_vip") val isVip: Boolean = false,
    @SerializedName("follower_count") val followerCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    val followed: Boolean = false,
    @SerializedName("is_self") val isSelf: Boolean = false,
    @SerializedName("moment_count") val momentCount: Int = 0,
    @SerializedName("app_count") val appCount: Int = 0,
    @SerializedName("page_bg") val pageBg: String = "",
    val bio: String = "",
)
data class PrivateMsgIn(val content: String)
data class PrivateMsgOut(
    val id: Int = 0,
    @SerializedName("sender_id") val senderId: Int = 0,
    @SerializedName("receiver_id") val receiverId: Int = 0,
    val content: String = "",
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
)
data class PrivateConvOut(
    val user: CardUserOut? = null,
    @SerializedName("last_msg") val lastMsg: String = "",
    @SerializedName("last_time") val lastTime: String = "",
    val unread: Int = 0,
)
data class FollowResult(val followed: Boolean = false, @SerializedName("follower_count") val followerCount: Int = 0)
data class ReportIn(
    @SerializedName("target_type") val targetType: String,
    @SerializedName("target_id") val targetId: Int,
    val reason: String,
    val detail: String = "",
)
data class ApkUploadResult(
    val ok: Boolean = true,
    val url: String = "",
    val size: Long = 0,
    @SerializedName("package_name") val packageName: String = "",
    val version: String = "",
    @SerializedName("version_code") val versionCode: Int = 0,
    val name: String = "",
)

// ==================== v9.3 ads & faq ====================
data class AdOut(
    val id: Int = 0,
    val title: String = "",
    val image: String = "",
    val link: String = "",
    val sort: Int = 0,
    val active: Boolean = true,
    @SerializedName("start_at") val startAt: String = "",
    @SerializedName("end_at") val endAt: String = "",
)

data class FaqOut(
    val id: Int = 0,
    val question: String = "",
    val answer: String = "",
    val category: String = "",
    val sort: Int = 0,
    val pinned: Boolean = false,
    val active: Boolean = true,
)

// ---------- 卡密兑换 / 定向消息 ----------
data class RedeemResult(
    val ok: Boolean = true,
    val kind: String = "",
    val amount: Int = 0,
    val detail: String = "",
    val balance: Double = 0.0,
    @SerializedName("vip_expire") val vipExpire: String = "",
    @SerializedName("is_vip") val isVip: Boolean = false,
)
data class RedeemRecord(
    val id: Int = 0,
    val code: String = "",
    val kind: String = "",
    val amount: Int = 0,
    val detail: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)
data class DirectMsg(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val sender: String = "",
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
)
data class UnreadOut(val unread: Int = 0)

// ---------- 打赏 / 金币账户 ----------
data class TipUser(
    @SerializedName("user_id") val userId: Int = 0,
    val nickname: String = "",
    val avatar: String = "",
    val amount: Int = 0,
)

data class TipSummary(
    @SerializedName("moment_id") val momentId: Int = 0,
    val total: Int = 0,
    val count: Int = 0,
    val top: List<TipUser> = emptyList(),
)

data class TipResult(
    val ok: Boolean = true,
    val balance: Double = 0.0,
    val amount: Int = 0,
)

data class BalanceLogItem(
    val id: Int = 0,
    val delta: Int = 0,
    val kind: String = "",
    val detail: String = "",
    @SerializedName("moment_id") val momentId: Int = 0,
    @SerializedName("balance_after") val balanceAfter: Int = 0,
    @SerializedName("created_at") val createdAt: String = "",
)

data class AccountInfo(
    val balance: Double = 0.0,
    @SerializedName("total_in") val totalIn: Int = 0,
    @SerializedName("total_out") val totalOut: Int = 0,
    @SerializedName("coin_per_yuan") val coinPerYuan: Int = 10,
    val logs: List<BalanceLogItem> = emptyList(),
)

data class OnlineInfo(
    val online: Int = 0,
    @SerializedName("window_minutes") val windowMinutes: Int = 5,
)

data class HeartbeatOut(
    val ok: Boolean = true,
    val online: Int = 0,
)




data class ToolOut(
    val id: Int,
    val name: String = "",
    val cover: String = "",
    val intro: String = "",
    val url: String = "",
    val status: String = "pending",
    @SerializedName("reject_reason") val rejectReason: String = "",
    @SerializedName("use_count") val useCount: Int = 0,
    @SerializedName("uploader_id") val uploaderId: Int = 0,
    @SerializedName("uploader_name") val uploaderName: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)


data class ToolCreateIn(
    val name: String,
    val cover: String = "",
    val intro: String = "",
    val url: String,
)


data class AppTipIn(val amount: Int = 10)


data class AppTipResult(val ok: Boolean = true, val balance: Double = 0.0, val total: Int = 0)


data class AppPurchaseResult(val ok: Boolean = true, val balance: Double = 0.0, val message: String = "")

// ---------- 开屏广告 ----------
data class SplashAdOut(
    val id: Int = 0,
    val image: String = "",
    val link: String = "",
    val title: String = "",
    @SerializedName("skip_seconds") val skipSeconds: Int = 3,
    val enabled: Boolean = true,
    val sort: Int = 0,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("click_count") val clickCount: Int = 0,
)

data class SplashResp(
    val ok: Boolean = true,
    val ad: SplashAdOut? = null,
)


// ===== 资源中心 =====
data class ResourceOut(
    val id: Int,
    val name: String = "",
    val summary: String = "",
    val description: String = "",
    val category: String = "",
    val avatar: String = "",
    val cover: String = "",
    val screenshots: List<String> = emptyList(),
    @SerializedName("res_type") val resType: String = "link",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("file_size") val fileSize: Long = 0,
    @SerializedName("is_paid") val isPaid: Boolean = false,
    val price: Int = 0,
    val status: String = "pending",
    @SerializedName("reject_reason") val rejectReason: String = "",
    @SerializedName("download_count") val downloadCount: Int = 0,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("tip_total") val tipTotal: Int = 0,
    @SerializedName("uploader_id") val uploaderId: Int = 0,
    @SerializedName("uploader_name") val uploaderName: String = "",
    val unlocked: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ResourceListOut(
    val total: Int = 0,
    val page: Int = 1,
    @SerializedName("has_more") val hasMore: Boolean = false,
    val items: List<ResourceOut> = emptyList(),
)

data class ResourceMineOut(
    val items: List<ResourceOut> = emptyList(),
)

data class ResourceCreateIn(
    val name: String,
    val summary: String = "",
    val description: String = "",
    val category: String = "",
    val cover: String = "",
    val screenshots: List<String> = emptyList(),
    @SerializedName("res_type") val resType: String = "link",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("file_size") val fileSize: Long = 0,
    @SerializedName("is_paid") val isPaid: Boolean = false,
    val price: Int = 0,
)

data class ResTipIn(val amount: Int)

data class ResTipOut(val ok: Boolean = true, @SerializedName("tip_total") val tipTotal: Int = 0)

data class ResUnlockOut(
    val ok: Boolean = true,
    val url: String = "",
    val msg: String = "",
)

data class ResDownloadOut(val ok: Boolean = true, val url: String = "")

data class ResCategoriesOut(val items: List<String> = emptyList())


/** FAV_VIEW_V1: 收藏接口返回 */
data class FavoriteResult(
    val ok: Boolean = true,
    val favorited: Boolean = false,
    val changed: Boolean = false,
)
