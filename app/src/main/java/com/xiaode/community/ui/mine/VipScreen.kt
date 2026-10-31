package com.xiaode.community.ui.mine

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.xiaode.community.R
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

// ============ VIP_UI_COMPOSE: 照 buyvip.vue 配色 ============
private val VipBg1 = Color(0xFF4A4A67)
private val VipBg2 = Color(0xFF696985)
private val VipBg3 = Color(0xFF53536B)
private val VipUser = Color(0xFFF9D7B2)      // 米杏
private val VipUserDark = Color(0xFFCEB295)
private val VipOrange = Color(0xFFF5984A)
private val VipCream = Color(0xFFFFF6ED)
private val VipTextDark = Color(0xFF3A2A18)
private val VipGray = Color(0xFF777777)
private val VipLightGray = Color(0xFF999999)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipScreenV2(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var info by remember { mutableStateOf<VipInfo?>(null) }
    var buying by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(0) }
    // 加载状态：null=加载中，""=成功，非空=错误信息
    var errMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val ctx = XiaodeApp.context
    val bgUploading = remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        errMsg = null
        when (val r = safeCall { api.vipMe() }) {
            is ApiResult.Ok -> {
                info = r.data
                errMsg = null
            }
            is ApiResult.Err -> {
                // 只有真正拿不到数据时才报错；已有数据时静默失败
                if (info == null) errMsg = r.message.ifBlank { "加载失败，请重试" }
            }
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    // 扫光动画
    val infinite = rememberInfiniteTransition(label = "vipshine")
    val shine by infinite.animateFloat(
        initialValue = -0.7f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "shine"
    )
    // 呼吸光晕
    val glow by infinite.animateFloat(
        initialValue = 0.30f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "glow"
    )
    // 内容淡入
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); appeared = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(420), label = "alpha"
    )

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uriRes ->
        val uri = uriRes ?: return@rememberLauncherForActivityResult
        if (info?.isVip != true) {
            scope.launch { snackbar.showSnackbar("会员专属功能，请先开通会员") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            bgUploading.value = true
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val tmp = File(ctx.cacheDir, "vipbg_${System.currentTimeMillis()}.jpg").apply { writeBytes(bytes) }
                val part = MultipartBody.Part.createFormData(
                    "file", tmp.name, tmp.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                when (val r = safeCall { api.uploadVipBg(part) }) {
                    is ApiResult.Ok -> { snackbar.showSnackbar("主页背景已更新"); load() }
                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                }
                tmp.delete()
            }
            bgUploading.value = false
        }
    }

    val plans = info?.plans ?: emptyList()
    if (selected >= plans.size && plans.isNotEmpty()) selected = 0

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(VipBg1, VipBg2, VipBg3)))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // ===== 顶栏 =====
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                }
                Text(
                    "VIP 特权", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(28.dp))

            // ===== 用户卡 #F9D7B2 + VIP 水印 =====
            // VIP_ANIM_FIX: 呼吸光晕边框
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(VipUser)
                    .border(
                        width = 2.dp,
                        color = VipOrange.copy(alpha = glow * 0.75f),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    )
                    .height(86.dp),
            ) {
                // VIP_ANIM_FIX: 扫光层（斜向白光扫过）
                Box(
                    Modifier.matchParentSize().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .width(70.dp)
                            .offset(x = ((shine.coerceIn(-0.7f, 1.5f) + 0.7f) / 2.2f * 420f).dp)
                            .rotate(18f)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.55f),
                                        Color.Transparent,
                                    )
                                )
                            )
                    )
                }
                // VIP_ICONS: 水印用 vip.png + VIP 字样
                Row(
                    Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).rotate(-8f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "VIP",
                        fontSize = 44.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                        color = Color(0xFFB99B7A).copy(alpha = 0.30f + glow * 0.42f),
                    )
                    Spacer(Modifier.width(2.dp))
                    Image(
                        painter = painterResource(R.drawable.vip),
                        contentDescription = "VIP",
                        modifier = Modifier.size(44.dp).alpha(0.42f + glow * 0.38f),
                        contentScale = ContentScale.Fit,
                    )
                }
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 头像
                    Box {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color.White, Color(0xFFFFE9D0))))
                                .border(2.dp, Color.White.copy(0.75f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "德",
                                color = Color(0xFFC98B4A), fontWeight = FontWeight.Black, fontSize = 22.sp,
                            )
                        }
                        // VIP_ICONS: 会员头像加 vip 徽章
                        if (info?.isVip == true) {
                            Box(
                                Modifier.size(20.dp).align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp)
                                    .clip(CircleShape).background(VipUser),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.vip),
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("我的会员", color = VipTextDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                info?.isVip == true -> "到期：${info?.vipExpire?.take(10) ?: ""}"
                                else -> "您还未开通会员"
                            },
                            color = if (info?.isVip == true) Color(0xFFC9762E) else VipGray,
                            fontSize = 12.sp,
                            fontWeight = if (info?.isVip == true) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            // ===== 白底内容区 =====
            // VIP_ANIM_FIX: 内容区淡入 + 上浮
            val contentOffset by animateDpAsState(
                targetValue = if (appeared) 0.dp else 18.dp,
                animationSpec = tween(420, easing = FastOutSlowInEasing), label = "coff"
            )
            Column(
                Modifier.fillMaxWidth()
                    .offset(y = contentOffset)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color.White)
                    .padding(horizontal = 16.dp)
                    .alpha(contentAlpha),
            ) {
                Spacer(Modifier.height(18.dp))

                // 余额
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("金币余额", color = VipGray, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${info?.balance ?: 0}",
                        color = VipOrange, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                }

                // ---- 会员套餐 ----
                Text(
                    "会员套餐", color = Color(0xFF222222), fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                )
                if (plans.isEmpty()) {
                    Text(
                        "暂无套餐", color = VipLightGray, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        plans.forEachIndexed { i, plan ->
                            PlanCard(
                                plan = plan,
                                isSelected = i == selected,
                                badge = when {
                                    plans.size >= 3 && i == plans.size - 1 -> "超值"
                                    plans.size >= 2 && i == 1 -> "热门"
                                    else -> ""
                                },
                                onClick = { selected = i },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ---- 开通按钮 ----
                Spacer(Modifier.height(18.dp))
                val curPlan = plans.getOrNull(selected)
                Button(
                    onClick = {
                        if (curPlan == null || buying) return@Button
                        scope.launch {
                            buying = true
                            when (val r = safeCall { api.buyVip(VipBuyIn(curPlan.key)) }) {
                                is ApiResult.Ok -> {
                                    info = r.data
                                    snackbar.showSnackbar("开通成功！+${curPlan.days} 天")
                                }
                                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                            }
                            buying = false
                        }
                    },
                    enabled = curPlan != null && !buying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VipUser, contentColor = VipTextDark,
                        disabledContainerColor = VipUser.copy(0.45f),
                        disabledContentColor = VipTextDark.copy(0.5f),
                    ),
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                buying -> "处理中…"
                                curPlan == null -> "请选择套餐"
                                else -> if (info?.isVip == true) "立即续费${curPlan.name}" else "立即开通${curPlan.name}"
                            },
                            fontWeight = FontWeight.Black, fontSize = 15.sp,
                        )
                        if (curPlan != null) {
                            Text("${curPlan.price} 金币 · ${curPlan.days} 天", fontSize = 11.sp, color = Color(0xFF6B5A48))
                        }
                    }
                }

                // ---- 会员特权 ----
                Text(
                    "会员特权", color = Color(0xFF222222), fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
                )
                // VIP_ICONS: 用 PNG 图标
                data class Perk(val t: String, val d: String, val icon: Int)
                val perks = listOf(
                    Perk("应用直传服务器", "上传安装包", R.drawable.upload),
                    Perk("多图多视频", "帖子最多 5 图 / 2 视频", R.drawable.addimg),
                    Perk("多设备登录", "最多 3 台同时在线", R.drawable.level),
                    Perk("专属主页背景", "会员专属背景 / 气泡", R.drawable.setting),
                    Perk("专属会员标识", "尊贵铭牌显示", R.drawable.vip),
                    Perk("更多特权", "随更新解锁更多", R.drawable.announcement),
                )
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    perks.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { perk ->
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape)
                                            .background(Color.White)
                                            .border(2.dp, VipUser, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            painter = painterResource(perk.icon),
                                            contentDescription = perk.t,
                                            modifier = Modifier.size(19.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(perk.t, color = VipOrange, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text(perk.d, color = Color(0xFF888888), fontSize = 10.sp)
                                    }
                                }
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // ---- 主页背景 ----
                Text(
                    "主页背景", color = Color(0xFF222222), fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (info?.pageBg.isNullOrBlank()) "尚未设置" else "已设置",
                        color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (info?.isVip != true) {
                                scope.launch { snackbar.showSnackbar("会员专属功能，请先开通会员") }
                            } else {
                                bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                        enabled = !bgUploading.value,
                        colors = ButtonDefaults.buttonColors(containerColor = VipUser, contentColor = VipTextDark),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        if (bgUploading.value) {
                            Text("上传中…", fontSize = 12.sp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(R.drawable.upload),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("上传", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                when (val r = safeCall { api.removeVipBg() }) {
                                    is ApiResult.Ok -> { snackbar.showSnackbar("已移除"); load() }
                                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                }
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) { Text("移除", fontSize = 12.sp, color = VipOrange) }
                }

                // ---- 说明 ----
                Text(
                    "会员说明", color = VipGray, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
                )
                listOf(
                    "会员为虚拟服务，开通后不支持退款。",
                    "续费时在原到期时间上顺延，不会浪费剩余天数。",
                    "会员权益若因系统调整发生变化，会提前公告。",
                    "以及享受以后更新的所有特权…",
                ).forEach {
                    Text(
                        it, color = Color(0xFF888888), fontSize = 11.5.sp,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }

        // 加载/错误遮罩
        if (info == null) {
            Box(
                Modifier.fillMaxSize().background(VipBg1.copy(0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                if (loading && errMsg == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("正在加载…", color = Color.White.copy(0.85f), fontSize = 13.sp)
                    }
                } else {
                    // 失败：给出提示与重试
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text("!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            errMsg ?: "加载失败，请重试",
                            color = Color.White, fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { scope.launch { load() } },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VipOrange, contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("重新加载", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { nav.popBackStack() }) {
                            Text("返回", color = Color.White.copy(0.7f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

// ============ 套餐卡（照源码 3 列样式） ============
@Composable
private fun PlanCard(
    plan: VipPlan,
    isSelected: Boolean,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 640f), label = "pscale"
    )
    Box(modifier = modifier.scale(scale)) {
        Column(
            Modifier.fillMaxWidth().height(132.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) VipCream else Color.White)
                .border(
                    width = if (isSelected) 2.dp else 1.5.dp,
                    color = if (isSelected) VipOrange else Color(0xFFDDDDDD),
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable { onClick() }
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                plan.name, color = Color(0xFF555555), fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${plan.price}",
                    color = if (isSelected) Color(0xFFC9762E) else Color(0xFF333333),
                    fontWeight = FontWeight.Black, fontSize = 22.sp,
                )
                Text(" 金币", color = VipLightGray, fontSize = 9.5.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${plan.days} 天", color = VipLightGray, fontSize = 10.5.sp, textAlign = TextAlign.Center,
            )
        }
        // 角标
        if (badge.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopStart).offset(x = (-3).dp, y = (-8).dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFF4757))))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
