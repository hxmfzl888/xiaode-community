# 小德社区 · 搭建与部署教程

本教程教你从零跑起 **后端 API** 和 **Android App**，全程约 20 分钟。

---

## 一、环境要求

| 组件 | 版本要求 | 说明 |
|---|---|---|
| 服务器 | 1核1G 以上 | Linux（Ubuntu 20.04+ / Debian / CentOS） |
| Python | 3.10 ~ 3.12 | 后端运行环境 |
| JDK | 17 | 编译 App |
| Android SDK | API 34 | 编译 App |
| Gradle | 8.4 | 项目自带 wrapper |

---

## 二、部署后端（xiaode-api）

### 1. 上传并解压

```bash
# 把 xiaode-api-src.zip 上传到服务器后
unzip xiaode-api-src.zip -d /opt/
cd /opt/xiaode-api
```

### 2. 创建虚拟环境并安装依赖

```bash
python3 -m venv venv
./venv/bin/pip install -r requirements.txt
```

### 3. 配置环境变量

```bash
cp .env.example .env    # 或 cp run.sh.example run.sh
nano .env
```

**必须修改：**

```ini
# JWT 签名密钥 —— 改成你自己的随机长字符串（可用 openssl rand -hex 32 生成）
XIAODE_SECRET_KEY=你生成的随机密钥

# 邮箱验证码（选填，不填则注册流程不发邮件）
XIAODE_SMTP_HOST=smtp.qq.com
XIAODE_SMTP_PORT=465
XIAODE_SMTP_USER=你的邮箱@qq.com
XIAODE_SMTP_PASS=你的邮箱授权码
XIAODE_SMTP_FROM=小德社区
```

### 4. 启动服务

```bash
bash start.sh          # 后台启动，监听 8000 端口
# 或前台调试：
./venv/bin/python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

启动后访问 **http://你的IP:8000/docs** 可看到自动生成的接口文档。

首次启动会自动创建 SQLite 数据库 `xiaode.db` 并建表。

### 5. 配置 Nginx 反向代理（推荐）

```nginx
server {
    listen 443 ssl http2;
    server_name api.your-domain.com;

    ssl_certificate     /path/to/fullchain.pem;
    ssl_certificate_key /path/to/privkey.pem;

    client_max_body_size 50m;   # 允许上传头像/图片/视频

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 6. 设置开机自启（systemd，可选）

```ini
# /etc/systemd/system/xiaode-api.service
[Unit]
Description=Xiaode Community API
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/xiaode-api
EnvironmentFile=/opt/xiaode-api/.env
ExecStart=/opt/xiaode-api/venv/bin/python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now xiaode-api
```

---

## 三、编译 Android App（XiaodeCommunity）

### 1. 上传并解压

```bash
unzip xiaode-app-src.zip -d /opt/
cd /opt/XiaodeCommunity
```

### 2. 配置后端地址

打开 `app/src/main/java/com/xiaode/community/data/ApiClient.kt`（或 `ApiService.kt`），
把 `BASE_URL` 改成你自己的后端地址：

```kotlin
const val BASE_URL = "https://api.your-domain.com/"
```

### 3. 配置签名（发布版需要）

在 `app/build.gradle.kts` 中配置你自己的 keystore：

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("your.keystore")
        storePassword = "你的store密码"
        keyAlias = "你的别名"
        keyPassword = "你的key密码"
    }
}
```

> ⚠️ **开源版源码包中不含官方 keystore**，请生成自己的：
> `keytool -genkey -v -keystore your.keystore -alias yourkey -keyalg RSA -keysize 2048 -validity 10000`

### 4. 编译打包

```bash
# 调试版
./gradlew assembleDebug

# 发布版（需先配好签名）
./gradlew assembleRelease
```

产物位置：
- 调试版：`app/build/outputs/apk/debug/app-debug.apk`
- 发布版：`app/build/outputs/apk/release/app-release.apk`

### 5. 安装到手机

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 四、常见问题

**Q：后端启动报 `ModuleNotFoundError`？**
A：确认用的是虚拟环境的 Python：`./venv/bin/python -m uvicorn ...`，并已 `pip install -r requirements.txt`。

**Q：App 连不上后端？**
A：① 检查 `BASE_URL` 是否填对（注意结尾斜杠）；② 后端是否监听 `0.0.0.0`；③ 服务器防火墙/安全组是否放行端口；④ 用 HTTPS 时证书是否有效。

**Q：上传图片/视频失败？**
A：Nginx 需设置 `client_max_body_size 50m;`；后端 `uploads/` 目录需有写权限。

**Q：忘记管理员怎么设置？**
A：数据库 `users` 表中把对应用户的 `is_admin` 字段改为 1。

**Q：数据库如何备份？**
A：直接复制 `/opt/xiaode-api/xiaode.db` 即可（SQLite 单文件）。

---

## 五、目录结构速览

```
xiaode-api/              后端
├── app/
│   ├── main.py          入口
│   ├── models.py        数据模型
│   ├── schemas.py       请求/响应模型
│   ├── auth.py          密码 & JWT
│   ├── levels.py        等级/经验/任务配置
│   ├── mailer.py        邮件发送
│   └── routers/         各业务路由
├── requirements.txt     依赖
├── .env.example         环境变量示例
├── start.sh             启动脚本
└── xiaode.db            SQLite 数据库（首次启动生成）

XiaodeCommunity/         App
├── app/src/main/java/com/xiaode/community/
│   ├── data/            网络层（Retrofit）+ 数据模型
│   ├── ui/              界面（Compose）
│   │   ├── square/      广场
│   │   ├── apps/        应用商店
│   │   ├── mine/        我的 / 等级 / 设置
│   │   └── theme/       主题
│   └── MainActivity.kt
├── app/build.gradle.kts 构建配置
└── gradlew              构建脚本
```

---

## 六、开源协议与免责

本项目以 **Apache License 2.0** 开源。

使用前请务必阅读根目录 **DISCLAIMER.md**（免责声明）。
本软件按「现状（AS IS）」提供，不附带任何担保；因使用本软件产生的一切风险与后果，由使用者自行承担。

Copyright © 2026 景聚智途数字科技
