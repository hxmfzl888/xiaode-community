package com.xiaode.community.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface ApiService {

    // ==================== 认证 ====================
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterIn): TokenOut

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginIn): TokenOut

    @POST("api/auth/send_code")
    suspend fun sendCode(@Body body: SendCodeIn): OkMsg

    @POST("api/auth/reset_password")
    suspend fun resetPassword(@Body body: ResetPasswordIn): OkMsg

    @GET("api/auth/me")
    suspend fun me(): UserOut

    // ==================== 用户 ====================
    @GET("api/users/me")
    suspend fun profile(): UserOut

    @PUT("api/users/me")
    suspend fun updateProfile(@Body body: ProfileUpdateIn): UserOut

    @Multipart
    @POST("api/users/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UserOut

    @Multipart
    @POST("api/users/images")
    suspend fun uploadImage(@Part file: MultipartBody.Part): UploadResult

    @POST("api/users/change_password")
    suspend fun changePassword(@Body body: ChangePasswordIn): OkMsg

    @POST("api/users/change_username")
    suspend fun changeUsername(@Body body: ChangeUsernameIn): OkMsg

    @POST("api/users/change_email")
    suspend fun changeEmail(@Body body: ChangeEmailIn): OkMsg

    @PUT("api/users/chat_setting")
    suspend fun chatSetting(@Body body: ChatSettingIn): OkMsg

    // ==================== 广场 ====================
    @GET("api/moments")
    suspend fun moments(@Query("limit") limit: Int = 20, @Query("offset") offset: Int = 0, @Query("tag") tag: String = ""): List<MomentOut>

    @POST("api/moments")
    suspend fun createMoment(@Body body: MomentCreateIn): MomentOut

    @GET("api/moments/mine")
    suspend fun myMoments(@Query("limit") limit: Int = 20, @Query("offset") offset: Int = 0): List<MomentOut>

    @GET("api/moments/channel/{cid}")
    suspend fun channelMoments(
        @Path("cid") cid: Int,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("tag") tag: String = "",
    ): List<MomentOut>

    @GET("api/moments/{id}")
    suspend fun momentDetail(@Path("id") id: Int): MomentOut

    @DELETE("api/moments/{id}")
    suspend fun deleteMoment(@Path("id") id: Int): OkMsg

    @POST("api/moments/{id}/comments")
    suspend fun createComment(@Path("id") id: Int, @Body body: CommentCreateIn): CommentOut

    @GET("api/moments/{id}/comments")
    suspend fun comments(@Path("id") id: Int): List<CommentOut>

    @DELETE("api/moments/comments/{cid}")
    suspend fun deleteComment(@Path("cid") cid: Int): OkMsg

    @POST("api/moments/{id}/like")
    suspend fun likeMoment(@Path("id") id: Int): LikeResult

    @POST("api/moments/comments/{cid}/like")
    suspend fun likeComment(@Path("cid") cid: Int): LikeResult

    // ==================== 频道 ====================
    @GET("api/channels")
    suspend fun channels(@Query("q") q: String = ""): List<Channel>

    @GET("api/channels/mine")
    suspend fun myChannels(): List<Channel>

    @GET("api/channels/joined")
    suspend fun joinedChannels(): List<Channel>

    @GET("api/channels/{cid}")
    suspend fun channelDetail(@Path("cid") cid: Int): Channel

    @POST("api/channels")
    suspend fun createChannel(@Body body: ChannelCreateIn): Channel

    @POST("api/channels/{cid}/join")
    suspend fun toggleJoin(@Path("cid") cid: Int): JoinResult

    // ==================== 聊天 ====================
    @GET("api/chat/conversations")
    suspend fun conversations(): List<ChatConvOut>

    @GET("api/chat/friends")
    suspend fun friends(): List<FriendOut>

    @GET("api/chat/friend/requests")
    suspend fun friendRequests(): List<FriendReqOut>

    @POST("api/chat/friend/request")
    suspend fun sendFriendRequest(@Body body: FriendRequestIn): OkMsg

    @POST("api/chat/friend/accept/{id}")
    suspend fun acceptFriend(@Path("id") id: Int): OkMsg

    @POST("api/chat/friend/reject/{id}")
    suspend fun rejectFriend(@Path("id") id: Int): OkMsg

    @GET("api/chat/search")
    suspend fun searchUsers(@Query("q") q: String): List<UserLevelOut>

    @GET("api/chat/user/{username}")
    suspend fun userInfo(@Path("username") username: String): UserLevelOut

    @POST("api/chat/send")
    suspend fun sendMessage(@Body body: ChatSendIn): ChatMsgOut

    @GET("api/chat/messages/{peerId}")
    suspend fun messages(
        @Path("peerId") peerId: Int,
        @Query("limit") limit: Int = 100,
        @Query("before_id") beforeId: Int = 0,
    ): List<ChatMsgOut>

    @POST("api/chat/recall/{msgId}")
    suspend fun recall(@Path("msgId") msgId: Int): OkMsg

    @POST("api/chat/forward/{msgId}")
    suspend fun forward(@Path("msgId") msgId: Int, @Body body: ChatSendIn): ChatMsgOut

    @GET("api/chat/unread_total")
    suspend fun unreadTotal(): UnreadCount

    // 黑名单 / 置顶
    @GET("api/chat/blacklist")
    suspend fun blacklist(): List<BlackUser>

    @POST("api/chat/blacklist/{uid}")
    suspend fun block(@Path("uid") uid: Int): BlockedResult

    @DELETE("api/chat/blacklist/{uid}")
    suspend fun unblock(@Path("uid") uid: Int): BlockedResult

    @GET("api/chat/pins")
    suspend fun pins(): PinsOut

    @POST("api/chat/pins/{uid}")
    suspend fun pin(@Path("uid") uid: Int): PinnedResult

    @DELETE("api/chat/pins/{uid}")
    suspend fun unpin(@Path("uid") uid: Int): PinnedResult

    // ==================== 消息通知 ====================
    @GET("api/notifications")
    suspend fun notifications(@Query("limit") limit: Int = 50, @Query("offset") offset: Int = 0): NotificationListOut

    @GET("api/notifications/unread_count")
    suspend fun notifyUnread(): UnreadCount

    @POST("api/notifications/read_all")
    suspend fun readAll(): OkMsg

    @POST("api/notifications/{id}/read")
    suspend fun readOne(@Path("id") id: Int): OkMsg
    @DELETE("api/notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: Int): OkMsg
    @POST("api/notifications/clear")
    suspend fun clearNotifications(): OkMsg
    @POST("api/notifications/{id}/pin")
    suspend fun pinNotification(@Path("id") id: Int): PinnedResult

    // ==================== v9.3 ads & faq ====================
    @GET("api/ads")
    suspend fun ads(): List<AdOut>
    @GET("api/faqs")
    suspend fun faqs(@Query("category") category: String = ""): List<FaqOut>

    // ==================== 会员 ====================
    @GET("api/vip/me")
    suspend fun vipMe(): VipInfo

    @POST("api/vip/buy")
    suspend fun buyVip(@Body body: VipBuyIn): VipInfo

    @Multipart
    @POST("api/vip/upload_bg")
    suspend fun uploadVipBg(@Part file: MultipartBody.Part): BgResult

    @DELETE("api/vip/bg")
    suspend fun removeVipBg(): OkMsg

    @POST("api/vip/bubble")
    suspend fun setBubble(@Body body: Map<String, String>): OkMsg

    @GET("api/vip/bubbles")
    suspend fun bubbles(): List<BubbleStyle>

    // ==================== 等级/任务 ====================
    @GET("api/tasks/me")
    suspend fun tasks(): TaskListOut

    @POST("api/tasks/sign")
    suspend fun sign(): SignResult

    // 开屏广告
    @GET("api/splash")
    suspend fun getSplash(): SplashResp

    @POST("api/splash/{id}/click")
    suspend fun splashClick(@Path("id") id: Int): OkMsg

    // v2.1: 排行榜。type=sign(签到) | post(发帖) | rich(富豪榜)；range=all | day | week
    @GET("api/tasks/rank")
    suspend fun taskRank(
        @Query("type") type: String,
        @Query("range") range: String = "all",
        @Query("limit") limit: Int = 50,
    ): RankOut

    // ==================== 公告 ====================
    @GET("api/announcements")
    suspend fun announcements(): List<AnnouncementOut>

    // ==================== 第三方登录（游戏） ====================
    @POST("api/game/issue_token")
    suspend fun issueGameToken(): GameTokenOut


    // ==================== v9.0 搜索 ====================
    @GET("api/search/moments")
    suspend fun searchMoments(@Query("q") q: String, @Query("limit") limit: Int = 30, @Query("offset") offset: Int = 0): List<MomentOut>

    @GET("api/search/channels")
    suspend fun searchChannels(@Query("q") q: String, @Query("limit") limit: Int = 30, @Query("offset") offset: Int = 0): List<Channel>

    @GET("api/channels/{cid}/search")
    suspend fun searchChannelMoments(@Path("cid") cid: Int, @Query("q") q: String, @Query("limit") limit: Int = 30, @Query("offset") offset: Int = 0): List<MomentOut>

    // ==================== v9.0 频道头像 ====================
    @Multipart
    @POST("api/channels/{cid}/avatar")
    suspend fun uploadChannelAvatar(@Path("cid") cid: Int, @Part file: MultipartBody.Part): Channel

    // ==================== v9.0 应用商店 ====================
    @GET("api/apps")
    suspend fun apps(@Query("q") q: String = "", @Query("category") category: String = "", @Query("limit") limit: Int = 30, @Query("offset") offset: Int = 0): List<AppOut>

    @GET("api/apps/mine")
    suspend fun myApps(): List<AppOut>

    @GET("api/apps/pending")
    suspend fun pendingApps(): List<AppOut>

    @GET("api/apps/{id}")
    suspend fun appDetail(@Path("id") id: Int): AppOut

    @POST("api/apps")
    suspend fun createApp(@Body body: AppCreateIn): AppOut

    @Multipart
    @POST("api/apps/upload_icon")
    suspend fun uploadAppIcon(@Part file: MultipartBody.Part): UploadResult

    @Multipart
    @POST("api/apps/upload_shot")
    suspend fun uploadAppShot(@Part file: MultipartBody.Part): UploadResult

    @Multipart
    @POST("api/apps/upload_apk")
    suspend fun uploadAppApk(@Part file: MultipartBody.Part): UploadResult

    @POST("api/apps/{id}/review")
    suspend fun reviewApp(@Path("id") id: Int, @Query("approve") approve: Boolean = true, @Query("reason") reason: String = ""): OkMsg

    @POST("api/apps/{id}/download")
    suspend fun recordDownload(@Path("id") id: Int): DownloadResult

    @GET("api/apps/{id}/comments")
    suspend fun appComments(@Path("id") id: Int): List<AppCommentOut>

    @DELETE("api/apps/comments/{cid}")
    suspend fun deleteAppComment(@Path("cid") cid: Int): retrofit2.Response<Unit>

    @POST("api/apps/{id}/comments")
    suspend fun addAppComment(@Path("id") id: Int, @Body body: AppCommentIn): AppCommentOut

    // ==================== v9.0 我的 ====================
    // ==================== v2.1.9 app paid / tip ====================
    @POST("api/apps/{id}/purchase")
    suspend fun purchaseApp(@Path("id") id: Int): AppPurchaseResult


    @POST("api/apps/{id}/tip")
    suspend fun tipApp(@Path("id") id: Int, @Body body: AppTipIn): AppTipResult


    // ==================== v2.1.9 tools ====================
    @GET("api/tools")
    suspend fun tools(@Query("q") q: String = "", @Query("limit") limit: Int = 50, @Query("offset") offset: Int = 0): List<ToolOut>


    @GET("api/tools/mine")
    suspend fun myTools(): List<ToolOut>


    @GET("api/tools/pending")
    suspend fun pendingTools(): List<ToolOut>


    @GET("api/tools/{id}")
    suspend fun toolDetail(@Path("id") id: Int): ToolOut


    @POST("api/tools")
    suspend fun createTool(@Body body: ToolCreateIn): ToolOut


    @Multipart
    @POST("api/tools/upload_cover")
    suspend fun uploadToolCover(@Part file: MultipartBody.Part): UploadResult


    @POST("api/tools/{id}/review")
    suspend fun reviewTool(@Path("id") id: Int, @Query("ok") ok: Boolean = true, @Query("reason") reason: String = ""): ToolOut


    @GET("api/moments/liked")
    suspend fun likedMoments(@Query("limit") limit: Int = 30, @Query("offset") offset: Int = 0): List<MomentOut>

    // ===== FAV_VIEW_V1: 收藏 =====
    @GET("api/moments/favorites")
    suspend fun favoriteMoments(@Query("limit") limit: Int = 20, @Query("offset") offset: Int = 0): List<MomentOut>

    @POST("api/moments/{id}/favorite")
    suspend fun favoriteMoment(@Path("id") id: Int): FavoriteResult

    @DELETE("api/moments/{id}/favorite")
    suspend fun unfavoriteMoment(@Path("id") id: Int): FavoriteResult

    @GET("api/moments/{id}/favorite")
    suspend fun favoriteStatus(@Path("id") id: Int): FavoriteResult

    @GET("api/app/version")
    suspend fun appVersion(): VersionInfo

    // v9.0 注销账号
    @POST("api/users/delete_account")
    suspend fun deleteAccount(@Body body: DeleteAccountIn): OkMsg

    // ==================== v9.0 等级/任务（兼容旧路径） ====================
    @GET("api/tasks/me")
    suspend fun taskMe(): TaskListOut

    @GET("api/tasks/me/level")
    suspend fun myLevel(): UserLevelOut
    // ==================== v9.1 社交/关注/私信/举报 ====================
    @POST("api/social/follow/{user_id}")
    suspend fun toggleFollow(@Path("user_id") userId: Int): FollowResult
    @GET("api/social/following")
    suspend fun myFollowing(): List<CardUserOut>
    @GET("api/social/followers")
    suspend fun myFollowers(): List<CardUserOut>
    @GET("api/social/user/{user_id}")
    suspend fun socialUser(@Path("user_id") userId: Int): SocialUserOut

    // 用户主页内容（公开可见）
    @GET("api/social/user/{user_id}/moments")
    suspend fun userMoments(@Path("user_id") userId: Int,
                            @Query("limit") limit: Int = 30,
                            @Query("offset") offset: Int = 0): MomentPage

    @GET("api/social/user/{user_id}/resources")
    suspend fun userResources(@Path("user_id") userId: Int,
                              @Query("limit") limit: Int = 30,
                              @Query("offset") offset: Int = 0): ResourcePage

    @GET("api/social/user/{user_id}/apps")
    suspend fun userApps(@Path("user_id") userId: Int,
                         @Query("limit") limit: Int = 30,
                         @Query("offset") offset: Int = 0): AppPage
    @POST("api/social/pm/{user_id}")
    suspend fun sendPm(@Path("user_id") userId: Int, @Body body: PrivateMsgIn): PrivateMsgOut
    @GET("api/social/pm/conversations")
    suspend fun pmConversations(): List<PrivateConvOut>
    @GET("api/social/pm/{user_id}")
    suspend fun pmHistory(@Path("user_id") userId: Int): List<PrivateMsgOut>
    @GET("api/social/pm/unread/count")
    suspend fun pmUnread(): UnreadCount
    @POST("api/social/report")
    suspend fun report(@Body body: ReportIn): OkMsg
    @GET("api/social/report/reasons")
    suspend fun reportReasons(): List<String>

    // ==================== 卡密兑换 ====================
    @POST("api/redeem")
    suspend fun redeemCode(@Body body: Map<String, String>): RedeemResult
    @GET("api/redeem/history")
    suspend fun redeemHistory(): List<RedeemRecord>
    // ==================== 定向消息 ====================
    @GET("api/redeem/messages")
    suspend fun directMessages(): List<DirectMsg>
    @GET("api/redeem/messages/unread")
    suspend fun unreadMessages(): UnreadOut
    @POST("api/redeem/messages/{id}/read")
    suspend fun readMessage(@Path("id") id: Int): OkMsg

    // ==================== 打赏 / 金币账户 ====================
    @GET("api/tip/summary/{moment_id}")
    suspend fun tipSummary(@Path("moment_id") momentId: Int): TipSummary

    @POST("api/tip/{moment_id}")
    suspend fun giveTip(@Path("moment_id") momentId: Int, @Body body: Map<String, Int>): TipResult

    @GET("api/tip/account")
    suspend fun tipAccount(): AccountInfo

    @GET("api/tip/online")
    suspend fun onlineCount(): OnlineInfo

    @POST("api/tip/heartbeat")
    suspend fun heartbeat(): Map<String, Object>

    // ===== 资源中心 =====
    @GET("api/resources/categories")
    suspend fun resCategories(): ResCategoriesOut

    @GET("api/resources")
    suspend fun resources(
        @Query("q") q: String = "",
        @Query("category") category: String = "",
        @Query("sort") sort: String = "new",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): ResourceListOut

    @GET("api/resources/mine")
    suspend fun myResources(): ResourceMineOut

    @POST("api/resources")
    suspend fun createResource(@Body body: ResourceCreateIn): ResourceOut

    @GET("api/resources/{rid}")
    suspend fun resourceDetail(@Path("rid") rid: Int): ResourceOut

    @POST("api/resources/{rid}/download")
    suspend fun resDownload(@Path("rid") rid: Int): ResDownloadOut

    @POST("api/resources/{rid}/unlock")
    suspend fun resUnlock(@Path("rid") rid: Int): ResUnlockOut

    @POST("api/resources/{rid}/tip")
    suspend fun resTip(@Path("rid") rid: Int, @Body body: ResTipIn): ResTipOut

    // ===== RES_COMMENT_V1: 资源评论 =====
    @GET("api/resources/comments/{rid}")
    suspend fun resourceComments(@Path("rid") rid: Int): List<ResourceCommentOut>

    @POST("api/resources/{rid}/comments")
    suspend fun addResourceComment(@Path("rid") rid: Int, @Body body: ResourceCommentIn): ResourceCommentOut

    @DELETE("api/resources/comments/{cid}")
    suspend fun deleteResourceComment(@Path("cid") cid: Int): retrofit2.Response<Unit>

    @DELETE("api/resources/{rid}")
    suspend fun deleteResource(@Path("rid") rid: Int): Unit

    // 资源上传（Multipart）
    @Multipart
    @POST("api/resources/upload_cover")
    suspend fun uploadResCoverRaw(@Part file: MultipartBody.Part): Map<String, Any>

    @Multipart
    @POST("api/resources/upload_shot")
    suspend fun uploadResShotRaw(@Part file: MultipartBody.Part): Map<String, Any>

    @Multipart
    @POST("api/resources/upload_file")
    suspend fun uploadResFileRaw(@Part file: MultipartBody.Part): Map<String, Any>
}
