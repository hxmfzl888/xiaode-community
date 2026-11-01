<div align="center">

# 小德社区

**一个轻量、干净的社区平台 · Android 客户端 + FastAPI 后端全栈开源**

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202024.02-orange.svg)](https://developer.android.com/jetpack/compose)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-teal.svg)](https://fastapi.tiangolo.com)

[官网](https://delai.fun) · [下载 APK](https://delai.fun/download/小德社区_2.8.1.apk) · [问题反馈](https://github.com/hxmfzl888/xiaode-community/issues)

</div>

---

## 项目简介

小德社区是一个完整可运行的社区平台，包含 **Android 客户端** 与 **FastAPI 后端服务**，两端代码全部开源。

不是 Demo，不是脚手架 —— 是真实运营中的产品代码，包含账号体系、内容审核、金币经济、会员体系等完整业务逻辑。

### 核心特性

**客户端**
- Jetpack Compose 全量实现，零 XML 布局
- Material 3 + 液态玻璃视觉风格
- 6 个主 Tab：广场 / 应用 / 资源 / 小德 / 消息 / 我的
- 头像自由裁剪（拖拽 + 双指缩放）
- 扫码登录、个人二维码
- 本地预置 AI 问答（离线秒回）
- 深色模式、宋体字体支持

**后端**
- 28 个业务路由模块，9,300+ 行 Python
- JWT 鉴权（HS256，30 天有效期）
- bcrypt 密码哈希
- 内容先审后发（帖子 / 应用 / 资源 / 评论）
- 推荐算法：关注优先 + 热度加权
- 金币经济系统 + VIP 会员 + 任务中心
- 邮件验证码（SMTP）
- 完整管理后台（含 Android 4.4 兼容精简版）

---

## 技术栈

### 客户端

| 组件 | 技术 |
|---|---|
| 语言 | Kotlin 1.9.22 |
| UI | Jetpack Compose（BOM 2024.02.01）+ Material 3 |
| 架构 | MVVM + Repository |
| 网络 | Retrofit 2 + OkHttp 3 + Gson |
| 图片 | Coil |
| 二维码 | ZXing core 3.5.3 |
| 相机 | CameraX 1.3.1 |
| SDK | minSdk 24 / targetSdk 34 / compileSdk 34 |
| 构建 | Gradle 8.4 + AGP 8.2 |

### 后端

| 组件 | 技术 |
|---|---|
| 语言 | Python 3.10 ~ 3.12 |
| 框架 | FastAPI 0.115 |
| ORM | SQLAlchemy 2.0 |
| 数据库 | SQLite（可换 PostgreSQL）|
| 鉴权 | python-jose（JWT HS256）|
| 密码 | passlib + bcrypt |
| 服务器 | uvicorn |

---

## 目录结构

```
xiaode-community/
├── app/                          # 📱 Android 客户端
│   └── src/main/
│       ├── java/com/xiaode/community/
│       │   ├── data/             # 网络层、模型、仓库
│       │   ├── ui/               # 界面
│       │   │   ├── auth/         # 登录注册、启动页
│       │   │   ├── square/       # 广场、帖子详情
│       │   │   ├── appstore/     # 应用商店
│       │   │   ├── resource/     # 资源库
│       │   │   ├── chat/         # 私信、AI 助手
│       │   │   ├── social/       # 个人主页、关注
│       │   │   ├── mine/         # 我的、编辑资料
│       │   │   ├── common/       # 通用组件
│       │   │   └── theme/        # 主题、字体
│       │   └── XiaodeRoot.kt     # 导航根
│       └── res/                  # 资源文件
│
├── backend/                      # 🖥 FastAPI 后端
│   ├── app/
│   │   ├── main.py               # 主入口
│   │   ├── models.py             # 数据模型
│   │   ├── schemas.py            # Pydantic 模型
│   │   ├── auth.py               # JWT + 密码
│   │   ├── deps.py               # 鉴权依赖
│   │   ├── database.py           # 数据库连接
│   │   └── routers/              # 28 个路由模块
│   ├── static/                   # 管理后台页面
│   ├── requirements.txt
│   ├── .env.example
│   └── run.sh
│
├── BUILD.md                      # 详细部署教程
├── DISCLAIMER.md                 # 免责声明
└── LICENSE                       # MIT
```

---

## 快速开始

### 一、后端

```bash
cd backend

# 1. 创建虚拟环境
python3 -m venv venv
source venv/bin/activate

# 2. 安装依赖
pip install -r requirements.txt

# 3. 配置环境变量
cp .env.example .env
# 编辑 .env，至少填写 XIAODE_SECRET_KEY
python3 -c "import secrets;print(secrets.token_urlsafe(48))"   # 生成密钥

# 4. 启动
bash run.sh
# 或前台：uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

启动后访问 **http://127.0.0.1:8000/docs** 查看自动生成的 API 文档。

> 数据库首次启动自动创建，无需手动建表。

### 二、客户端

```bash
# 1. 确认 SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 2. 编译 Debug 包
./gradlew assembleDebug

# 3. 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

**如果你的后端不在本机**，修改 `app/src/main/java/com/xiaode/community/data/ApiService.kt` 里的 `BASE_URL`。

**要打正式包**，需要自己生成签名：

```bash
keytool -genkey -v -keystore my-release.jks -keyalg RSA \
        -keysize 2048 -validity 10000 -alias mykey
```

然后在 `app/build.gradle.kts` 中配置 `signingConfigs`。

---

## 主要功能

### 社区
- 发帖（图文 / 视频 / 频道 / 标签）
- 评论、回复、点赞、收藏
- 关注 / 粉丝
- 频道创建与加入
- 全文搜索（帖子 / 频道 / 用户 / UID 精准匹配）

### 应用商店
- APK 上传 / 外链
- 应用详情、截图、分类
- 下载统计、评论评分

### 资源库
- 链接 / 文件两种形式
- 付费资源（金币）
- 打赏

### 账号体系
- 邮箱注册（QQ 号 + 域名快捷输入）
- UID 8 位数字唯一 ID
- 等级、经验、金币
- VIP 会员
- 每日签到、任务中心
- 兑换码

### 内容安全
- **先审后发**：帖子 / 应用 / 资源 / 评论全部需审核
- 举报系统（帖子 / 评论 / 用户）
- 用户封禁
- 审核员角色（可分配权限）

### 管理后台
- `/admin` — 完整版（需 Chrome 55+）
- `/admin/lite` — 精简版（**兼容 Android 4.4**，帖子 / 应用 / 资源 / 举报四合一）
- `/reviewer` — 审核员专用

---

## API 概览

| 模块 | 路径前缀 | 说明 |
|---|---|---|
| 认证 | `/api/auth` | 注册、登录、验证码、找回密码 |
| 帖子 | `/api/moments` | 发帖、列表、详情、点赞、收藏 |
| 评论 | `/api/moments/{id}/comments` | 评论、回复 |
| 应用 | `/api/apps` | 应用列表、详情、下载 |
| 资源 | `/api/resources` | 资源列表、详情 |
| 用户 | `/api/users` | 个人主页、编辑资料 |
| 社交 | `/api/social` | 关注、粉丝、动态 |
| 搜索 | `/api/search` | 综合搜索 |
| 私信 | `/api/chat` | 会话、消息 |
| 金币 | `/api/coins` | 余额、流水、兑换 |
| VIP | `/api/vip` | 套餐、开通 |
| 任务 | `/api/task` | 每日任务、签到 |
| 通知 | `/api/notifications` | 消息通知 |
| 审核 | `/api/admin` | 后台管理接口 |
| 扫码 | `/api/qr` | 扫码登录 |

完整文档见运行后的 `/docs`（Swagger UI）。

---

## 数据库

默认 SQLite，单文件 `xiaode.db`，首次启动自动建表。

主要表：

```
users              用户
moments            帖子
comments           评论
moment_likes       点赞
moment_favorites   收藏
apps              应用
resources         资源
channels          频道
friendships       关注关系
notifications     通知
reports           举报
coin_accounts     金币账户
coin_logs         金币流水
vip_orders        VIP 订单
tasks             任务
```

**换 PostgreSQL**：修改 `.env` 里的 `DATABASE_URL` 即可，SQLAlchemy 自动适配。

---

## 贡献

欢迎提交 Issue 和 Pull Request。

1. Fork 本仓库
2. 创建分支 `git checkout -b feature/your-feature`
3. 提交改动 `git commit -m "feat: 你的功能"`
4. 推送 `git push origin feature/your-feature`
5. 发起 Pull Request

**提交信息规范**：`feat:` 新功能 / `fix:` 修复 / `docs:` 文档 / `refactor:` 重构 / `style:` 格式

---

## 免责声明

本项目仅供**学习与技术交流**使用。

- 使用者需自行承担部署与运营的全部责任
- 请遵守当地法律法规，不得用于违法用途
- 严禁发布违法违规、侵犯他人权益的内容
- 作者不对使用本项目产生的任何后果负责

详见 [DISCLAIMER.md](DISCLAIMER.md)

---

## 许可证

[MIT License](LICENSE) — 可自由使用、修改、商用，无需署名。

---

<div align="center">

**如果这个项目对你有帮助，点个 ⭐ 支持一下**

[官网](https://delai.fun) · [下载](https://delai.fun) · [Issues](https://github.com/hxmfzl888/xiaode-community/issues)

</div>
