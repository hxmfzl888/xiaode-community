package com.xiaode.community.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.pageFadeIn
import com.xiaode.community.ui.legal.NOT_AGREED_HINT
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 登录 / 注册页（两步式）。
 *
 * 登录流程：输入账号密码 → 弹出「确认登录」页（头像 + 昵称 + 简介）→ 点确认才真正登录。
 * 注册流程：填写账号/邮箱/验证码注册 → 进入「完善资料」页（昵称 + 简介 + 头像）→ 点确认完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
// TWO_STEP_AUTH_V1: 登录/注册两步式改造
@Composable
fun LoginScreen(nav: NavController, onLoggedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var agreed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var showForgot by remember { mutableStateOf(false) }

    // ===== 两步式状态 =====
    /** 待确认的登录结果（非空 = 显示确认登录页） */
    var pendingLogin by remember { mutableStateOf<TokenOut?>(null) }
    /** 注册成功后待完善资料的会话（非空 = 显示完善资料页） */
    var pendingProfile by remember { mutableStateOf<TokenOut?>(null) }
    /** 完善资料页字段 */
    var pfNickname by remember { mutableStateOf("") }
    var pfBio by remember { mutableStateOf("") }
    var pfAvatarUrl by remember { mutableStateOf("") }
    var pfUploading by remember { mutableStateOf(false) }
    var pfAvatarBytes by remember { mutableStateOf<ByteArray?>(null) }

    // 验证码倒计时
    LaunchedEffect(countdown) {
        if (countdown > 0) { kotlinx.coroutines.delay(1000); countdown -= 1 }
    }

    /** 将登录账号存入多账号列表（去重，最多保存 10 个） */
    fun saveAccountToList(t: TokenOut) {
        val cur = XiaodeApp.store.accountListJson
        val list = if (cur.isNullOrBlank()) mutableListOf<com.google.gson.JsonObject>()
                   else runCatching { Gson().fromJson(cur, com.google.gson.JsonArray::class.java) }
                       .getOrNull()
                       ?.mapNotNull { it as? com.google.gson.JsonObject }
                       ?.toMutableList() ?: mutableListOf()
        list.removeAll { it.get("user")?.asJsonObject?.get("id")?.asInt == t.user.id }
        val obj = com.google.gson.JsonObject()
        obj.add("user", Gson().toJsonTree(t.user))
        obj.addProperty("token", t.accessToken)
        list.add(0, obj)
        while (list.size > 10) list.removeAt(list.size - 1)
        XiaodeApp.store.accountListJson = Gson().toJson(list)
    }

    /** 保存登录结果并跳转 */
    fun saveAndEnter(t: TokenOut) {
        XiaodeApp.store.token = t.accessToken
        XiaodeApp.store.currentUserJson = Gson().toJson(t.user)
        saveAccountToList(t)
        onLoggedIn()
    }

    /** 未勾选 → 提示 */
    fun requireAgreed(action: () -> Unit) {
        if (!agreed) {
            scope.launch { snackbar.showSnackbar(NOT_AGREED_HINT) }
            return
        }
        action()
    }

    /**
     * 上传头像字节。必须在已写入 token 之后调用（接口需要鉴权）。
     * 返回头像 URL，失败返回空字符串。
     */
    suspend fun uploadAvatarBytes(bytes: ByteArray): String {
        return try {
            val mime = "image/*"
            val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "avatar.jpg", body)
            when (val r = safeCall { Repo.api.uploadAvatar(part) }) {
                is ApiResult.Ok -> r.data.avatar
                is ApiResult.Err -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    // ===== 分支渲染：三步取其一 =====
    if (pendingLogin != null) {
        // ---------- 第二步（登录）：确认页 ----------
        ConfirmLoginPage(
            token = pendingLogin!!,
            snackbar = snackbar,
            onBack = { pendingLogin = null },
            onConfirm = { t -> saveAndEnter(t) },
        )
        return
    }

    if (pendingProfile != null) {
        // ---------- 第二步（注册）：完善资料页 ----------
        CompleteProfilePage(
            token = pendingProfile!!,
            ctx = ctx,
            snackbar = snackbar,
            nickname = pfNickname,
            onNickname = { pfNickname = it },
            bio = pfBio,
            onBio = { pfBio = it },
            avatarUrl = pfAvatarUrl,
            uploading = pfUploading,
            onPickAvatar = { bytes ->
                scope.launch {
                    pfUploading = true
                    pfAvatarBytes = bytes
                    val url = uploadAvatarBytes(bytes)
                    pfUploading = false
                    if (url.isNotBlank()) {
                        pfAvatarUrl = url
                    } else {
                        snackbar.showSnackbar("头像上传失败，可稍后在个人主页更换")
                    }
                }
            },
            onBack = {
                // 返回上一步：清掉已注册 token，让用户重新注册/换账号
                XiaodeApp.store.token = ""
                pendingProfile = null
                pfNickname = ""; pfBio = ""; pfAvatarUrl = ""; pfAvatarBytes = null
            },
            onConfirm = {
                scope.launch {
                    loading = true
                    // 1) 提交昵称 / 简介
                    val nn = pfNickname.trim()
                    val bb = pfBio.trim()
                    if (nn.isNotBlank() || bb.isNotBlank()) {
                        when (val r = safeCall {
                            Repo.api.updateProfile(
                                ProfileUpdateIn(
                                    nickname = nn.ifBlank { null },
                                    bio = bb.ifBlank { null },
                                )
                            )
                        }) {
                            is ApiResult.Ok -> { /* ok */ }
                            is ApiResult.Err -> {
                                loading = false
                                snackbar.showSnackbar(r.message)
                                return@launch
                            }
                        }
                    }
                    // 2) 拉一次最新资料，写入本地
                    val latest = when (val r = safeCall { Repo.api.profile() }) {
                        is ApiResult.Ok -> r.data
                        is ApiResult.Err -> pendingProfile!!.user
                    }
                    loading = false
                    saveAndEnter(
                        TokenOut(
                            accessToken = pendingProfile!!.accessToken,
                            user = latest,
                        )
                    )
                }
            },
            loading = loading,
        )
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        // SF_LOGIN_UI_V1: StarFree 风格 —— 白底大标题 + 无描边输入框 + 主色胶囊按钮
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .pageFadeIn()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            // ---- Logo：圆角渐变方块 ----
            Box(
                Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "德",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(18.dp))
            // ---- 大标题 ----
            Text(
                if (isRegister) "创建账号" else "欢迎回来",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (isRegister) "加入小德社区，开始分享" else "登录小德社区，继续你的旅程",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(34.dp))
            // ---- 用户名 ----
            SfAuthField(
                value = username,
                onValueChange = { username = it },
                label = "用户名",
                icon = Icons.Filled.Person,
            )
            Spacer(Modifier.height(12.dp))
            // ---- 密码 ----
            SfAuthField(
                value = password,
                onValueChange = { password = it },
                label = "密码",
                icon = Icons.Filled.Lock,
                isPassword = true,
                showPassword = showPwd,
                onTogglePassword = { showPwd = !showPwd },
            )
            // ---- 注册附加字段（昵称不再在这里填，改为注册后完善资料页）----
            AnimatedVisibility(visible = isRegister) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    SfAuthField(
                        value = email,
                        onValueChange = { email = it },
                        label = "邮箱",
                        icon = Icons.Filled.Email,
                        keyboardType = KeyboardType.Email,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SfAuthField(
                                value = code,
                                onValueChange = { code = it },
                                label = "邮箱验证码",
                                icon = Icons.Filled.Email,
                                keyboardType = KeyboardType.Number,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            onClick = {
                                if (countdown > 0) return@TextButton
                                if (email.isBlank()) {
                                    scope.launch { snackbar.showSnackbar("请先填写邮箱") }
                                    return@TextButton
                                }
                                scope.launch {
                                    val r = safeCall { Repo.api.sendCode(SendCodeIn(email = email.trim())) }
                                    when (r) {
                                        is ApiResult.Ok -> {
                                            snackbar.showSnackbar("验证码已发送，请查收邮箱")
                                            countdown = 60
                                            while (countdown > 0) {
                                                kotlinx.coroutines.delay(1000)
                                                countdown--
                                            }
                                        }
                                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                    }
                                }
                            },
                            enabled = countdown == 0,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(
                                if (countdown > 0) "${countdown}s" else "获取验证码",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // ---- 主按钮：主色胶囊 ----
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        scope.launch { snackbar.showSnackbar("请输入用户名和密码") }
                        return@Button
                    }
                    scope.launch {
                        loading = true
                        if (isRegister) {
                            // ==== 注册第一步：只创建账号，进入完善资料页 ====
                            if (!agreed) {
                                loading = false
                                snackbar.showSnackbar("请先阅读并同意用户规范与免责声明")
                                return@launch
                            }
                            if (email.isBlank()) {
                                loading = false
                                snackbar.showSnackbar("请填写邮箱")
                                return@launch
                            }
                            if (code.isBlank()) {
                                loading = false
                                snackbar.showSnackbar("请填写邮箱验证码")
                                return@launch
                            }
                            val r = safeCall {
                                Repo.api.register(
                                    RegisterIn(
                                        username = username.trim(),
                                        password = password,
                                        nickname = username.trim(),   // 先用用户名占位
                                        email = email.trim(),
                                        code = code.trim(),
                                    )
                                )
                            }
                            loading = false
                            when (r) {
                                is ApiResult.Ok -> {
                                    // 先写入 token，完善资料页上传头像/改昵称需要鉴权
                                    XiaodeApp.store.token = r.data.accessToken
                                    pfNickname = r.data.user.nickname.ifBlank { username.trim() }
                                    pfBio = ""
                                    pfAvatarUrl = ""
                                    pfAvatarBytes = null
                                    pendingProfile = r.data
                                }
                                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                            }
                        } else {
                            // ==== 登录第一步：校验密码，成功后弹确认页 ====
                            val r = safeCall { Repo.api.login(LoginIn(username.trim(), password)) }
                            loading = false
                            when (r) {
                                is ApiResult.Ok -> pendingLogin = r.data
                                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                            }
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.size(19.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        if (isRegister) "注册" else "下一步",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
            // 提示为什么叫“下一步”
            Spacer(Modifier.height(8.dp))
            Text(
                if (isRegister) "注册后还需填写昵称、简介和头像" else "下一步确认你的账号头像与昵称",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // ---- 切换登录/注册 ----
            TextButton(
                onClick = {
                    isRegister = !isRegister
                    code = ""
                    nickname = ""
                    email = ""
                },
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    if (isRegister) "已有账号？去登录" else "还没有账号？立即注册",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            // ---- 协议勾选（仅注册） ----
            AnimatedVisibility(visible = isRegister) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { agreed = !agreed }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (agreed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        null,
                        tint = if (agreed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "我已阅读并同意",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "《用户规范与免责声明》",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://api.delai.fun/terms"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            } catch (e: Exception) { }
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // ---- 忘记密码 ----
            if (!isRegister) {
                TextButton(
                    onClick = { showForgot = true },
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "忘记密码？",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "小德社区 · 由个人开发者独立运营",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
    if (showForgot) {
        ForgotPasswordDialog(onDismiss = { showForgot = false }, snackbar = snackbar)
    }
}
// ============================================================
// 第二步（登录）：确认登录页
// 展示头像 / 昵称 / 简介，点「确认登录」才真正登录
// ============================================================
@Composable
private fun ConfirmLoginPage(
    token: TokenOut,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onConfirm: (TokenOut) -> Unit,
) {
    val u = token.user
    val name = u.nickname.ifBlank { u.username }
    val bio = u.bio

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .pageFadeIn()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            // ---- 顶部返回 ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹ 返回",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "确认登录",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "请确认这是你的账号",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(36.dp))

            // ---- 头像 ----
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                if (u.avatar.isNotBlank()) {
                    AsyncImage(
                        model = u.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Text(
                        name.take(1).ifBlank { "德" },
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- 昵称 ----
            Text(
                name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "@${u.username}",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            // ---- 简介 ----
            if (bio.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        bio,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(44.dp))

            // ---- 确认登录 ----
            Button(
                onClick = { onConfirm(token) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    "确认登录",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onBack, shape = RoundedCornerShape(50)) {
                Text(
                    "不是我，换个账号",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "小德社区 · 由个人开发者独立运营",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ============================================================
// 第二步（注册）：完善资料页
// 填写昵称 / 简介 / 头像，点「完成注册」才走完注册
// ============================================================
@Composable
private fun CompleteProfilePage(
    token: TokenOut,
    ctx: Context,
    snackbar: SnackbarHostState,
    nickname: String,
    onNickname: (String) -> Unit,
    bio: String,
    onBio: (String) -> Unit,
    avatarUrl: String,
    uploading: Boolean,
    onPickAvatar: (ByteArray) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    loading: Boolean,
) {
    // 当前注册会话（token.user 用于兜底展示）
    val sessionUser = token.user

    // 相册选图（单张）
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    val bytes = ins.readBytes()
                    if (bytes.isNotEmpty()) onPickAvatar(bytes)
                }
            } catch (e: Exception) {
                // 读取失败静默，用户可重试
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .pageFadeIn()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹ 返回",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "完善你的资料",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "让社区里的其他人认识你（@${sessionUser.username}）",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(30.dp))

            // ---- 头像：点击从相册选择 ----
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable(enabled = !uploading) {
                            pickLauncher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        uploading -> CircularProgressIndicator(
                            Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        avatarUrl.isNotBlank() -> AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape),
                        )
                        nickname.isNotBlank() -> Text(
                            nickname.take(1),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> Icon(
                            Icons.Filled.Person,
                            null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
                // 右下角小相机角标
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (uploading) "正在上传…" else "点击选择头像（可选）",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(28.dp))

            // ---- 昵称 ----
            SfAuthField(
                value = nickname,
                onValueChange = { if (it.length <= 50) onNickname(it) },
                label = "昵称",
                icon = Icons.Filled.Person,
                keyboardType = KeyboardType.Text,
            )
            Spacer(Modifier.height(14.dp))

            // ---- 简介 ----
            SfAuthField(
                value = bio,
                onValueChange = { if (it.length <= 80) onBio(it) },
                label = "简介（选填，最多 80 字）",
                icon = Icons.Filled.Person,
                keyboardType = KeyboardType.Text,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "${bio.length}/80",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(32.dp))

            // ---- 完成注册 ----
            Button(
                onClick = { onConfirm() },
                enabled = !loading && !uploading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.size(19.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "完成注册",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "昵称、简介和头像之后都可以在个人主页修改",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}
@Composable
private fun SfAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 15.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = primary.copy(alpha = 0.68f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(11.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
            ),
            visualTransformation = if (isPassword && !showPassword) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 15.dp),
            decorationBox = { innerTextField: @Composable () -> Unit ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            label,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (isPassword && onTogglePassword != null) {
            IconButton(
                onClick = onTogglePassword,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}


@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
/** 忘记密码对话框：邮箱 + 验证码 + 新密码 */
@Composable
private fun ForgotPasswordDialog(onDismiss: () -> Unit, snackbar: SnackbarHostState) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(countdown) {
        if (countdown > 0) { kotlinx.coroutines.delay(1000); countdown -= 1 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("找回密码", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("注册邮箱") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it },
                        label = { Text("验证码") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        enabled = countdown == 0 && email.isNotBlank(),
                        onClick = {
                            scope.launch {
                                when (val r = safeCall { api.sendCode(SendCodeIn(email.trim(), "reset")) }) {
                                    is ApiResult.Ok -> { countdown = 60; snackbar.showSnackbar("验证码已发送") }
                                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                }
                            }
                        },
                    ) { Text(if (countdown > 0) "${countdown}s" else "获取", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newPwd, onValueChange = { newPwd = it },
                    label = { Text("新密码（至少6位）") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && email.isNotBlank() && code.isNotBlank() && newPwd.length >= 6,
                onClick = {
                    scope.launch {
                        submitting = true
                        val body = ResetPasswordIn(email.trim(), code.trim(), newPwd)
                        when (val r = safeCall { api.resetPassword(body) }) {
                            is ApiResult.Ok -> { snackbar.showSnackbar("密码已重置，请用新密码登录"); onDismiss() }
                            is ApiResult.Err -> snackbar.showSnackbar(r.message)
                        }
                        submitting = false
                    }
                },
            ) { Text("重置密码") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
