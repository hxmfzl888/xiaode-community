package com.xiaode.community.ui.square

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 广场状态。
 * - mode = 全部广场 / 某频道
 * - 帖子上报后刷新，点赞/评论做本地乐观更新
 */
data class SquareUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val moments: List<MomentOut> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val joinedChannels: List<Channel> = emptyList(),
    val notifyUnread: Int = 0,
    val channelUnread: Set<Int> = emptySet(),   // 有未读新帖的频道
    val activeChannelId: Int = 0,               // 0 = 全部广场
    val activeChannelName: String = "",
    val hasMore: Boolean = true,
    val toast: String? = null,
    val error: String? = null,
    val ads: List<AdOut> = emptyList(),
    val announcements: List<AnnouncementOut> = emptyList(),
    val activeTag: String = "",                 // v2.1 标签筛选（空=全部）
)

class SquareViewModel : ViewModel() {

    private val api = Repo.api
    private val sp = XiaodeApp.store

    private val _ui = MutableStateFlow(SquareUiState())
    val ui: StateFlow<SquareUiState> = _ui.asStateFlow()

    private var offset = 0
    private val pageSize = 20

    init {
        loadChannels()
        loadAds()
        loadAnnouncements()
        refresh()
        loadNotifyUnread()
    }

    fun consumeToast() { _ui.value = _ui.value.copy(toast = null, error = null) }

    // ---------- 频道 ----------
    fun loadChannels() {
        viewModelScope.launch {
            when (val r = safeCall { api.channels() }) {
                is ApiResult.Ok -> {
                    val all = r.data
                    _ui.value = _ui.value.copy(
                        channels = all,
                        // 简单判断：频道创建时间比 lastSync 新 → 标红点
                        channelUnread = all.filter { ch ->
                            ch.createdAt.isNotBlank() && sp.lastSync > 0 &&
                                parseTs(ch.createdAt) > sp.lastSync
                        }.map { it.id }.toSet(),
                    )
                }
                is ApiResult.Err -> {}
            }
            when (val r2 = safeCall { api.joinedChannels() }) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(joinedChannels = r2.data)
                is ApiResult.Err -> {}
            }
        }
    }

    fun markChannelsRead() {
        sp.lastSync = System.currentTimeMillis()
        _ui.value = _ui.value.copy(channelUnread = emptySet())
    }

    // ---------- 帖子流 ----------
    fun refresh() {
        offset = 0
        _ui.value = _ui.value.copy(refreshing = true, hasMore = true)
        loadAnnouncements()
        loadPage(reset = true)
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.refreshing || !s.hasMore) return
        loadPage(reset = false)
    }

    private fun loadPage(reset: Boolean) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val cid = _ui.value.activeChannelId
            val tag = _ui.value.activeTag
            val res = if (cid == 0) {
                safeCall { api.moments(pageSize, offset, tag) }
            } else {
                safeCall { api.channelMoments(cid, pageSize, offset, tag) }
            }
            when (res) {
                is ApiResult.Ok -> {
                    val list = res.data
                    val merged = (if (reset) list else _ui.value.moments + list)
                        .distinctBy { it.id }   // 防止重复 id 造成 LazyColumn key 冲突崩溃
                    offset += list.size
                    _ui.value = _ui.value.copy(
                        loading = false, refreshing = false,
                        moments = merged, hasMore = list.size >= pageSize,
                    )
                }
                is ApiResult.Err -> {
                    _ui.value = _ui.value.copy(loading = false, refreshing = false, error = res.message)
                }
            }
        }
    }

    fun switchChannel(id: Int, name: String) {
        _ui.value = _ui.value.copy(activeChannelId = id, activeChannelName = name, moments = emptyList())
        refresh()
    }

    /** v2.1 标签筛选：空串=全部 */
    fun switchTag(tag: String) {
        if (_ui.value.activeTag == tag) return
        _ui.value = _ui.value.copy(activeTag = tag, moments = emptyList())
        refresh()
    }

    // ---------- 点赞 ----------
    fun toggleLike(m: MomentOut) {
        // 乐观更新
        _ui.value = _ui.value.copy(moments = _ui.value.moments.map {
            if (it.id == m.id) it.copy(liked = !it.liked, likeCount = it.likeCount + if (it.liked) -1 else 1) else it
        })
        viewModelScope.launch {
            when (val r = safeCall { api.likeMoment(m.id) }) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(moments = _ui.value.moments.map {
                        if (it.id == m.id) it.copy(liked = r.data.liked, likeCount = r.data.likeCount) else it
                    })
                }
                is ApiResult.Err -> {
                    // 回滚
                    _ui.value = _ui.value.copy(moments = _ui.value.moments.map {
                        if (it.id == m.id) it.copy(liked = m.liked, likeCount = m.likeCount) else it
                    }, toast = r.message)
                }
            }
        }
    }

    // ---------- 删除帖子 ----------
    fun deleteMoment(id: Int) {
        viewModelScope.launch {
            when (val r = safeCall { api.deleteMoment(id) }) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(
                        moments = _ui.value.moments.filter { it.id != id },
                        toast = "已删除",
                    )
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(toast = r.message)
            }
        }
    }

    // ---------- 发帖 ----------
    fun post(content: String, images: List<String>, channelId: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            val body = MomentCreateIn(content = content, images = images, channelId = channelId)
            when (val r = safeCall { api.createMoment(body) }) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(toast = "发布成功，审核通过后展示")
                    onDone()
                    refresh()
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(toast = r.message)
            }
        }
    }

    // ---------- 评论 ----------
    fun loadComments(momentId: Int, onOk: (List<CommentOut>) -> Unit) {
        viewModelScope.launch {
            when (val r = safeCall { api.comments(momentId) }) {
                is ApiResult.Ok -> onOk(r.data)
                is ApiResult.Err -> _ui.value = _ui.value.copy(toast = r.message)
            }
        }
    }

    fun addComment(momentId: Int, content: String, parentId: Int?, onOk: () -> Unit) {
        viewModelScope.launch {
            when (val r = safeCall { api.createComment(momentId, CommentCreateIn(content, parentId)) }) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(moments = _ui.value.moments.map {
                        if (it.id == momentId) it.copy(commentCount = it.commentCount + 1) else it
                    })
                    onOk()
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(toast = r.message)
            }
        }
    }

    fun deleteComment(momentId: Int, commentId: Int, onOk: () -> Unit) {
        viewModelScope.launch {
            when (val r = safeCall { api.deleteComment(commentId) }) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(moments = _ui.value.moments.map {
                        if (it.id == momentId) it.copy(commentCount = maxOf(0, it.commentCount - 1)) else it
                    })
                    onOk()
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(toast = r.message)
            }
        }
    }

    // ---------- 公告 ----------
    fun loadAnnouncements() {
        viewModelScope.launch {
            when (val r = safeCall { api.announcements() }) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(announcements = r.data)
                is ApiResult.Err -> {}
            }
        }
    }

    // ---------- 广告位 ----------
    fun loadAds() {
        viewModelScope.launch {
            when (val r = safeCall { api.ads() }) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(ads = r.data)
                is ApiResult.Err -> {}
            }
        }
    }

    // ---------- 消息红点 ----------
    fun loadNotifyUnread() {
        viewModelScope.launch {
            when (val r = safeCall { api.notifyUnread() }) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(notifyUnread = r.data.unread)
                is ApiResult.Err -> {}
            }
        }
    }

    /** 时间戳解析（ISO → 毫秒），用于频道红点判断 */
    private fun parseTs(iso: String): Long = try {
        java.time.OffsetDateTime.parse(iso.replace("Z", "+00:00")).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.LocalDateTime.parse(iso).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) { 0L }
    }
}