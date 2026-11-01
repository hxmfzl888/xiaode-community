# 小德社区 · 后端服务

FastAPI 实现的社区后端，配合 `../app/` 的 Android 客户端使用。

## 快速开始

```bash
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
cp .env.example .env          # 编辑填入密钥
bash run.sh
```

访问 http://127.0.0.1:8000/docs 查看 API 文档。

## 环境变量

见 `.env.example`，关键项：

| 变量 | 必填 | 说明 |
|---|---|---|
| `XIAODE_SECRET_KEY` | ✅ | JWT 签名密钥，必须改成随机长串 |
| `DATABASE_URL` | | 默认 `sqlite:///./xiaode.db` |
| `XIAODE_SMTP_*` | | 邮件服务，用于注册验证码 |

**生成密钥**：`python3 -c "import secrets;print(secrets.token_urlsafe(48))"`

## 路由模块

```
routers/
├── auth_router.py            注册登录、验证码
├── moments_router.py         帖子、评论、点赞、收藏、推荐
├── apps_router.py            应用商店
├── resources_router.py       资源库
├── users_router.py           用户资料
├── social_router.py          关注、粉丝、个人主页
├── search_router.py          搜索（含 UID 精准匹配）
├── chat_router.py            私信
├── chat_ext_router.py        私信扩展
├── notifications_router.py   通知
├── coins_router.py           金币账户
├── vip_router.py             VIP 会员
├── task_router.py            任务、签到
├── redeem_router.py          兑换码
├── channels_router.py        频道
├── game_router.py            小游戏
├── ai_router.py              AI 对话（SSE 流式）
├── weather_router.py         天气
├── qr_router.py              扫码登录
├── review_router.py          审核相关
├── reviewer_router.py        审核员管理
├── admin_router.py           管理后台（最大，3000+ 行）
├── announcements_router.py   公告
├── splash_router.py          启动页配置
├── tip_router.py             在线人数、提示
├── tools_router.py           工具箱
├── version_router.py         版本检查
├── ads_router.py             广告位
└── diaries_router.py         日记
```

## 管理后台

| 页面 | 地址 | 说明 |
|---|---|---|
| 完整版 | `/admin` | 功能全，需 Chrome 55+ |
| 精简版 | `/admin/lite` | **兼容 Android 4.4**，四大类审核 |
| 审核员 | `/reviewer` | 审核员专用 |

管理员权限通过数据库 `users.is_admin = 1` 控制。

## 注意

- `.env` 含密钥，**绝不能提交**
- `uploads/` 存用户上传，已加入 `.gitignore`
- `*.db` 是本地数据，已忽略
