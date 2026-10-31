package com.xiaode.community.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * 统一返回：Either 风格。
 */
sealed class ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>()
    data class Err(val code: Int, val message: String) : ApiResult<Nothing>()
}

/**
 * 网络调用包装：把异常转成可读错误提示（含 FastAPI 的 detail 字段）。
 */
suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> = withContext(Dispatchers.IO) {
    try {
        ApiResult.Ok(block())
    } catch (e: HttpException) {
        val detail = try {
            val body = e.response()?.errorBody()?.string().orEmpty()
            Regex("\"detail\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1) ?: body
        } catch (_: Exception) { "" }
        val msg = when (e.code()) {
            401 -> "登录已过期，请重新登录"
            403 -> detail.ifBlank { "没有权限" }
            404 -> detail.ifBlank { "内容不存在" }
            429 -> detail.ifBlank { "操作太频繁，请稍后再试" }
            else -> detail.ifBlank { "请求失败(${e.code()})" }
        }
        ApiResult.Err(e.code(), msg)
    } catch (e: IOException) {
        ApiResult.Err(-1, "网络连接失败，请检查网络")
    } catch (e: Exception) {
        ApiResult.Err(-2, e.message ?: "未知错误")
    }
}

/**
 * 仓库：对 ApiService 做一层薄封装（后续可加本地缓存）。
 */
object Repo {
    val api get() = ApiClient.api

    // ===== 资源上传辅助（Uri → MultipartBody）=====

    private fun uriToPart(ctx: android.content.Context, uri: android.net.Uri, field: String): okhttp3.MultipartBody.Part {
        val resolver = ctx.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val ext = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mime) ?: "jpg"
        val body = okhttp3.RequestBody.create(
            mime.toMediaTypeOrNull(), bytes
        )
        return okhttp3.MultipartBody.Part.createFormData(
            field, "upload.${ext}", body
        )
    }

    suspend fun uploadResCover(ctx: android.content.Context, uri: android.net.Uri): String {
        val part = uriToPart(ctx, uri, "file")
        val map = api.uploadResCoverRaw(part)
        return (map["url"] as? String) ?: ""
    }

    suspend fun uploadResShot(ctx: android.content.Context, uri: android.net.Uri): String {
        val part = uriToPart(ctx, uri, "file")
        val map = api.uploadResShotRaw(part)
        return (map["url"] as? String) ?: ""
    }
}