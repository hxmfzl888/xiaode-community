# -*- coding: utf-8 -*-
"""小德日历 API - 程序入口

启动：
    uvicorn app.main:app --host 0.0.0.0 --port 8000
或：
    python -m app.main

文档（浏览器打开）：
    http://127.0.0.1:8000/docs
网页后台：
    http://127.0.0.1:8000/admin
"""
import os

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, FileResponse
from fastapi.staticfiles import StaticFiles

from .database import Base, engine
from .routers import (
    coins_router,
    auth_router,
    moments_router,
    diaries_router,
    users_router,
    announcements_router,
    notifications_router,
    chat_router,
    chat_ext_router,
    channels_router,
    version_router,
    vip_router,
    weather_router,
    task_router,
    admin_router,
    game_router,
    apps_router,
    resources_router,
    tools_router,
    search_router,
    ai_router,
    qr_router,
    social_router,
    reviewer_router,
    ads_router,
    redeem_router,
    tip_router,
    splash_router,
)

# 首次启动自动建表
Base.metadata.create_all(bind=engine)

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")
os.makedirs(os.path.join(UPLOAD_DIR, "avatars"), exist_ok=True)
os.makedirs(os.path.join(UPLOAD_DIR, "images"), exist_ok=True)
STATIC_DIR = os.path.join(BASE_DIR, "static")
os.makedirs(STATIC_DIR, exist_ok=True)

app = FastAPI(
    title="小德日历 API",
    description="为「小德日历」App 提供 账号登录 / 朋友圈 / 日记 / 头像 / 评论 / 公告 的云端接口，并带网页后台。",
    version="2.0.0",
)

# 允许跨域（App / 浏览器调试都需要）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 静态：上传的图片
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")
# 静态：网页后台前端资源
# 静态：后台前端资源（禁止直接下载 .html 源码，防止后台被拷贝）
from starlette.responses import PlainTextResponse as _PTR


class _GuardedStatic(StaticFiles):
    async def get_response(self, path, scope):
        if path.lower().endswith(".html"):
            return _PTR("403 Forbidden", status_code=403)
        return await super().get_response(path, scope)


app.mount("/static", _GuardedStatic(directory=STATIC_DIR), name="static")

# 路由注册
app.include_router(auth_router.router)
app.include_router(moments_router.router)
app.include_router(diaries_router.router)
app.include_router(users_router.router)
app.include_router(announcements_router.router)
app.include_router(notifications_router.router)
app.include_router(chat_router.router)
app.include_router(chat_ext_router.router)
app.include_router(channels_router.router)
app.include_router(version_router.router)
app.include_router(vip_router.router)
app.include_router(weather_router.router)
app.include_router(task_router.router)
app.include_router(admin_router.router)
app.include_router(game_router.router)
app.include_router(apps_router.router)
app.include_router(resources_router.router)
app.include_router(tools_router.router)
app.include_router(splash_router.router)
app.include_router(search_router.router)
app.include_router(ai_router.router)
app.include_router(qr_router.router)
app.include_router(social_router.router)
app.include_router(reviewer_router.router)
app.include_router(ads_router.router)
app.include_router(redeem_router.router)
app.include_router(tip_router.router)
app.include_router(coins_router.router)


@app.get("/", tags=["系统"], summary="服务信息")
def root():
    """打开网址首页时返回的服务概览。"""
    return {
        "ok": True,
        "service": "小德日历 API",
        "version": "2.0.0",
        "status": "running",
        "description": "为「小德日历」App 提供 账号登录 / 朋友圈(动态) / 日记(云端) / 头像 / 评论 / 公告 接口",
        "docs": "/docs",
        "redoc": "/redoc",
        "admin_page": "/admin",
        "health": "/api/health",
        "modules": {
            "认证": {
                "POST /api/auth/register": "注册",
                "POST /api/auth/login": "登录",
                "GET /api/auth/me": "获取当前用户",
            },
            "用户资料": {
                "GET /api/users/me": "我的资料",
                "PUT /api/users/me": "修改昵称/头像",
                "POST /api/users/avatar": "上传头像(<=2MB)",
                "POST /api/users/images": "上传动态图片(<=5MB, 单张)",
            },
            "动态": {
                "POST /api/moments": "发布动态(最多3图, 默认待审核)",
                "GET /api/moments": "广场(仅显示已通过)",
                "GET /api/moments/mine": "我的动态",
                "GET /api/moments/{id}": "动态详情(含评论)",
                "DELETE /api/moments/{id}": "删除我的动态",
                "POST /api/moments/{id}/comments": "发表评论",
                "GET /api/moments/{id}/comments": "评论列表",
                "DELETE /api/moments/comments/{cid}": "删除评论(本人/管理员)",
            },
            "日记": {
                "POST /api/diaries": "写日记",
                "GET /api/diaries": "日记列表(分页+搜索)",
                "GET /api/diaries/{id}": "日记详情",
                "PUT /api/diaries/{id}": "修改日记",
                "DELETE /api/diaries/{id}": "删除日记",
            },
            "公告": {
                "GET /api/announcements": "公告列表(所有人)",
                "POST /api/announcements": "发布公告(管理员)",
                "DELETE /api/announcements/{id}": "删除公告(管理员)",
            },
            "后台": {
                "GET /api/admin/stats": "概览统计",
                "GET /api/admin/users": "用户列表",
                "POST /api/admin/users/{id}/reset-password": "重置密码",
                "POST /api/admin/users/{id}/toggle-admin": "设置/取消管理员",
                "DELETE /api/admin/users/{id}": "删除用户",
                "GET /api/admin/moments": "动态审核列表",
                "POST /api/admin/moments/{id}/review": "审核动态通过/驳回",
            },
        },
    }




def _serve(name):
    path = os.path.join(STATIC_DIR, name)
    if not os.path.isfile(path):
        raise HTTPException(status_code=404, detail="页面不存在")
    return FileResponse(path)


@app.get("/web", include_in_schema=False)
def web_page():
    """社区网页版（纯文字，仅浏览帖子）"""
    return _serve("web.html")


@app.get("/terms", include_in_schema=False)
def terms_page():
    """用户使用规范与免责声明"""
    return _serve("terms.html")


@app.get("/admin", include_in_schema=False)
def admin_page():
    """主后台（新）"""
    return _serve("admin_new.html")


@app.get("/admin/lite", include_in_schema=False)
def admin_lite_page():
    """精简版审核台（兼容 Android 4.4）"""
    return _serve("admin_lite.html")


@app.get("/admin/old", include_in_schema=False)
def admin_old_page():
    """旧版管理后台（兼容保留）"""
    return _serve("admin.html")


@app.get("/hrlzhk", include_in_schema=False)
def hrlzhk_page():
    return _serve("admin.html")


@app.get("/review", include_in_schema=False)
def review_page():
    """审核端"""
    return _serve("reviewer.html")


@app.get("/api/health", tags=["系统"], summary="服务健康检查")
def health():
    return {"status": "healthy"}


@app.exception_handler(Exception)
async def unhandled_exception_handler(request, exc):
    return JSONResponse(status_code=500, content={"detail": "服务器内部错误：%s" % str(exc)})


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=False)
