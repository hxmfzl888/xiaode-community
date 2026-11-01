#!/bin/bash
# 小德社区后端启动脚本
cd /opt/xiaode-api || exit 1
. venv/bin/activate
exec uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 1