package com.xiaode.community.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络客户端。BASE_URL 固定指向小德社区后端。
 * 自动附带 Authorization: Bearer <token>。
 */
object ApiClient {

    const val BASE_URL = "https://api.delai.fun/"
    /** 图片/视频统一走 API 域名（已废弃 img.i520.gay CDN） */
    const val MEDIA_BASE = "https://api.delai.fun/"

    /** 后端返回的相对路径（/uploads/xxx） → 拼成完整 URL */
    fun absUrl(path: String?): String {
        if (path.isNullOrEmpty()) return ""
        if (path.startsWith("http")) return path
        val p = if (path.startsWith("/")) path else "/$path"
        // APK 等动态文件不走 CDN，避免缓存旧版本
        return if (p.startsWith("/uploads/app_files/")) BASE_URL.trimEnd('/') + p
        else MEDIA_BASE.trimEnd('/') + p
    }

    lateinit var tokenProvider: () -> String?

    /**
     * JSON 中为 null 的字符串统一转成 ""，
     * 避免 Gson 把 null 写进 Kotlin 非空 String 导致 NPE（如 isBlank()/take()）。
     */
    private val nullSafeStringAdapter = object : TypeAdapter<String>() {
        override fun write(out: JsonWriter, value: String?) {
            out.value(value ?: "")
        }

        override fun read(reader: JsonReader): String {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                return ""
            }
            return reader.nextString() ?: ""
        }
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(String::class.java, nullSafeStringAdapter)
        .create()

    val api: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                tokenProvider()?.let { tk ->
                    if (tk.isNotEmpty()) builder.addHeader("Authorization", "Bearer $tk")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
