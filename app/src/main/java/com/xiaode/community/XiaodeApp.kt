package com.xiaode.community
import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.xiaode.community.data.ApiClient
import com.xiaode.community.data.TokenStore
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import java.util.concurrent.TimeUnit

class XiaodeApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        store = TokenStore(this)
        ApiClient.tokenProvider = { store.token }
        CrashCatcher.install(this)
        }

    /**
     * 自定义 Coil ImageLoader：
     * 1. 图片请求独立 OkHttp（更长超时，避免弱网加载失败）
     * 2. 服务端返回 4xx/5xx 时 DROP 缓存 —— 防止旧的失败结果被永久缓存，
     *    这正是"部分图片一直打不开、老图能看"的根因
     * 3. 磁盘缓存 200MB / 内存缓存 25%，减少重复加载
     */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(Interceptor { chain ->
                val resp = chain.proceed(chain.request())
                // 失败响应不要写进磁盘缓存，下次重新请求
                if (!resp.isSuccessful) {
                    try { resp.close() } catch (_: Throwable) {}
                    throw java.io.IOException("image http " + resp.code)
                }
                resp
            })
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(180)
            .build()
    }

    companion object {
        lateinit var instance: XiaodeApp
            private set
        lateinit var store: TokenStore
            private set
        val context: Context get() = instance.applicationContext
        /** 全局登录态（Compose 可观察）：登录/退出/切换账号时统一改这个，驱动 UI 切换，避免导航崩溃 */
        val loginState = mutableStateOf(false)
        fun syncLoginState() { loginState.value = !store.token.isNullOrBlank() }
        /** 点赞脏标记：详情页点赞后 +1，主页监听到变化时刷新，保证列表与详情一致 */
        val momentsDirty = mutableStateOf(0)
        fun markMomentsDirty() { momentsDirty.value += 1 }
        /** 消息脏标记：消息页已读/清空后 +1，广场顶部红点监听到变化时刷新未读数 */
        val notifyDirty = mutableStateOf(0)
        fun markNotifyDirty() { notifyDirty.value += 1 }
        /** 金币脏标记：签到/打赏/购买后 +1，金币相关界面刷新 */
        val coinDirty = mutableStateOf(0)
        fun markCoinDirty() { coinDirty.value += 1 }
    }
}