#!/bin/bash
# 小德社区后端 · 启动脚本
# 所有敏感配置从 .env 读取，切勿把密钥写死在本文件
cd /opt/xiaode-api || exit 1

# 加载环境变量
if [ -f .env ]; then
    set -a
    . ./.env
    set +a
else
    echo "缺少 .env 文件，请先 cp .env.example .env 并填写" >&2
    exit 1
fi

pkill -f "uvicorn app.main" 2>/dev/null
sleep 1

setsid ./venv/bin/python -m uvicorn app.main:app \
    --host 0.0.0.0 --port 8000 \
    > /root/xiaode.log 2>&1 < /dev/null &
disown
sleep 1
echo STARTED
