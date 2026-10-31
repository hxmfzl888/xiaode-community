<div align="center">

# 小德社区 Xiaode Community

**一个轻量、干净的 Android 社区客户端**

发帖 · 看图 · 聊天 · 账号体系 · 多端同步

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-5.0%2B%20(API%2024)-3DDC84.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/version-2.5.2-brightgreen.svg)](https://delai.fun)

[官网](https://delai.fun) · [下载 APK](https://delai.fun/download/小德社区_2.5.2.apk) · [问题反馈](https://github.com/hxmfzl888/xiaode-community/issues)

</div>

---

## 项目简介

小德社区是一个由**个人开发者独立运营**的轻量级 Android 社区客户端。项目最初为了解决"想要一个干净、不臃肿、无广告的社区 App"这个简单需求，从零开始用 Kotlin + Jetpack Compose 构建。

现在将客户端代码**完全开源**，希望能给同样想做独立社区的朋友一点参考，也欢迎一起把它做得更好。

### 有什么不一样

- **真正轻量** — 单 APK 约 12MB，无第三方 SDK 堆砌，无广告
- **全 Compose 实现** — 100% Jetpack Compose，无 XML 布局，UI 代码即文档
- **完整功能闭环** — 不只是 Demo，包含发帖、评论、点赞、私信、频道、审核、会员等完整业务
- **审核端独立** — 审核员使用独立账号体系，内容匿名化处理
- **无追踪** — 不接入任何统计或行为分析 SDK

---

## 功能特性

<table>
<tr>
<td width="50%" valign="top">

### 社区核心
- 📝 **发帖** — 支持图文、多图、视频、代码块（语法高亮）
- 🏷️ **标签分类** — 教程 / 求助 / 分享 / 讨论
- 💬 **评论** — 多级回复、评论点赞
- ❤️ **点赞** — 帖子与评论均支持
- 🔍 **搜索** — 帖子、用户、频道全文检索

### 频道与社交
- 📢 **频道** — 自建频道、成员管理、频道内发帖
- 👥 **关注** — 关注用户、好友体系
- 💌 **私信** — 单聊、群聊、消息置顶、黑名单
- 🔔 **通知** — 点赞/评论/回复/关注/审核结果实时推送

</td>
<td width="50%" valign="top">

### 账号与成长
- 🔐 **账号体系** — 注册/登录/找回密码（邮箱验证码）
- 👤 **个人主页** — 头像、昵称、简介、等级、经验值
- 🏆 **等级系统** — 发帖/评论/登录获得经验升级
- 💰 **金币系统** — 打赏、兑换、余额明细
- 👑 **会员** — 会员专属功能（视频、自定义装扮）

### 内容安全
- 🛡️ **审核机制** — 发帖先审后发，审核员独立后台
- 🚨 **举报系统** — 用户举报 → 审核员处理
- ⛔ **封禁** — 支持限时/永久封禁、一键下架违规内容
- 📜 **用户协议** — 内置免责声明与用户规范

</td>
</tr>
</table>

---

## 技术栈

| 层次 | 技术选型 |
|---|---|
| **语言** | Kotlin 1.9.22 |
| **UI** | Jetpack Compose（Material 3） |
| **架构** | MVVM + Repository |
| **网络** | Retrofit 2 + OkHttp 3 + Gson |
| **图片** | Coil（Compose 原生支持） |
| **本地存储** | SharedPreferences（Token / 多账号） |
| **异步** | Kotlin Coroutines + Flow |
| **导航** | Navigation Compose |
| **最低支持** | Android 5.0（API 24） |
| **目标版本** | Android 14（API 34） |

### 为什么选这套

- **Compose 而非 XML**：17K 行代码几乎全是 Kotlin，UI 逻辑和业务逻辑在同一文件里，阅读和维护成本极低
- **Retrofit + Gson**：纯 JVM 依赖，未来如果做桌面端可直接复用网络层
- **不引入 Hilt/Koin**：项目规模下手动依赖注入足够，少一层魔法，新人上手更快

---

## 项目结构

```
xiaode-community/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/xiaode/community/
│       │   ├── XiaodeApp.kt            # Application 入口
│       │   ├── data/                   # 数据层
│       │   │   ├── ApiClient.kt        # OkHttp + Retrofit 初始化
│       │   │   ├── ApiService.kt       # 全部 API 接口定义
│       │   │   ├── Models.kt           # 数据模型（DTO）
│       │   │   ├── Repo.kt             # 仓库层 + 统一错误处理
│       │   │   └── TokenStore.kt       # Token / 多账号持久化
│       │   └── ui/                     # 界面层（全 Compose）
│       │       ├── XiaodeRoot.kt       # 根导航
│       │       ├── auth/               # 登录注册（两步式）
│       │       ├── square/             # 广场、详情、发帖、搜索
│       │       ├── chat/               # 私信、群聊
│       │       ├── social/             # 个人主页、关注
│       │       ├── mine/               # 我的、设置
│       │       ├── appstore/           # 应用市场
│       │       ├── resource/           # 资源库
│       │       ├── theme/              # 主题、配色
│       │       └── common/             # 通用组件
│       └── res/                        # 图标、字符串、主题
├── gradle/wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── LICENSE
└── README.md
```

---

## 快速开始

### 环境要求

- **JDK 17**（必需，Compose Compiler 要求）
- **Android SDK**（compileSdk 34）
- **Android Studio** Hedgehog (2023.1.1) 或更新版本
- 磁盘空间 ≥ 5GB（Gradle 缓存 + SDK）

### 克隆与构建

```bash
git clone https://github.com/hxmfzl888/xiaode-community.git
cd xiaode-community
```

配置 Android SDK 路径（**必需**，该文件已被 gitignore）：

```bash
# 方式 A：Android Studio 打开项目会自动生成
# 方式 B：手动创建
echo "sdk.dir=/path/to/your/Android/Sdk" > local.properties
```

构建 Debug 版本：

```bash
./gradlew assembleDebug
```

产物位置：`app/build/outputs/apk/debug/app-debug.apk`

### 配置后端地址

客户端默认连接官方服务器。如需指向自己的实例，修改：

```kotlin
// app/src/main/java/com/xiaode/community/data/ApiClient.kt

object ApiClient {
    const val BASE_URL = "https://api.delai.fun/"   // ← 改成你的后端地址
    const val MEDIA_BASE = "https://api.delai.fun/" // ← 图片/媒体地址
}
```

> ⚠️ **注意**：本项目只开源了 **Android 客户端**，后端服务端代码未包含在此仓库中。自行搭建需要实现与 `ApiService.kt` 中定义一致的 REST 接口。

### 签名发版（可选）

Debug 构建开箱即用。若要打 Release 包，需要自己的密钥库：

```bash
# 1. 生成 keystore
keytool -genkey -v -keystore xiaode.keystore -alias xiaode \
        -keyalg RSA -keysize 2048 -validity 10000

# 2. 配置密码（不要写进 gradle.properties 后提交！）
#    推荐放在 ~/.gradle/gradle.properties
echo "XIAODE_STORE_PASSWORD=你的密码" >> ~/.gradle/gradle.properties
echo "XIAODE_KEY_PASSWORD=你的密码"   >> ~/.gradle/gradle.properties

# 3. 构建
./gradlew assembleRelease
```

---

## 一些实现细节

### 统一网络错误处理

`Repo.kt` 里的 `safeCall` 把 Retrofit 异常统一转成可读的中文提示，并解析 FastAPI 的 `detail` 字段：

```kotlin
suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> = 
    withContext(Dispatchers.IO) {
        try {
            ApiResult.Ok(block())
        } catch (e: HttpException) {
            // 401 → "登录已过期"
            // 403 → 后端 detail 或 "没有权限"
            // ...
        } catch (e: IOException) {
            ApiResult.Err(-1, "网络连接失败，请检查网络")
        }
    }
```

### 两步式登录 / 注册

登录不是直接进主页，而是先弹「确认页」展示头像昵称简介：

```
输入账号密码 → 点"下一步" → 确认页（头像/昵称/简介） → 点"确认登录"才真正登录
```

注册则是先建账号拿 token（因为头像上传接口需要鉴权），再进「完善资料页」填昵称/简介/头像。

### Gson null 安全适配

后端可能返回 `null` 而 Kotlin 声明是非空 `String`，会导致 NPE。`ApiClient` 里注册了全局 `TypeAdapter` 把 `null` 统一转成 `""`：

```kotlin
private val nullSafeStringAdapter = object : TypeAdapter<String>() {
    override fun write(out: JsonWriter, value: String?) { out.value(value ?: "") }
    // ...
}
```

### 审核端匿名化

审核员看到的帖子**只有用户 ID，没有昵称和头像**，避免审核员基于个人印象判断，也降低信息泄露风险。

---

## 参与贡献

欢迎任何形式的贡献！无论是 bug 报告、功能建议还是代码 PR。

### 提交 Issue

- 🐛 **Bug 报告**：请说明复现步骤、设备型号、Android 版本、App 版本
- 💡 **功能建议**：说明使用场景和期望效果
- ❓ **使用问题**：优先在 [Discussions](https://github.com/hxmfzl888/xiaode-community/discussions) 提问

### 提交 PR

```bash
# 1. Fork 本仓库

# 2. 创建分支（命名建议）
git checkout -b feat/your-feature      # 新功能
git checkout -b fix/your-bugfix        # 修 bug

# 3. 提交前请确认
./gradlew assembleDebug                 # 能编译通过
# 不要提交 build/、.idea/、local.properties、*.keystore

# 4. 提交并按模板填写 PR 说明
```

**代码风格**：遵循 [Kotlin 官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)，提交前请格式化。

---

## 免责声明

> ⚠️ **本项目仅供学习和技术交流使用。**

- 使用本项目搭建的任何实例，其内容由实例运营者自行负责，与原作者无关
- 请遵守当地法律法规，不得发布违法违规内容
- 项目内置《用户规范与免责声明》，使用者需同意后方可使用
- 作者不对因使用本项目产生的任何直接或间接损失承担责任

详见 [DISCLAIMER.md](DISCLAIMER.md)。

---

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

```
Copyright 2026 hxmfzl888

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

你可以自由地使用、修改、分发本项目（包括商业用途），但需保留版权声明和许可证文件。

---

## 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose) — 让 UI 开发重新变得愉快
- [Retrofit](https://square.github.io/retrofit/) / [OkHttp](https://square.github.io/okhttp/) — 稳定可靠的网络层
- [Coil](https://coil-kt.github.io/coil/) — 轻量好用的 Compose 图片加载
- 以及所有提出建议和反馈的用户

---

<div align="center">

**如果这个项目对你有帮助，欢迎点个 ⭐ Star**

Made with ❤️ by 个人开发者

</div>
