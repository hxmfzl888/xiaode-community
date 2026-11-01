# -*- coding: utf-8 -*-
"""
AI_XIAODE_V1: 小德 AI 聊天（本地 Ollama + qwen2.5:0.5b-instruct）
- 仅本机 127.0.0.1:11434，不暴露公网
- 每用户限流 10 次/分钟
- 上下文裁剪：最近 8 轮 + num_ctx 1024
- 支持 SSE 流式
"""
import json
import time
from collections import defaultdict, deque
from typing import List

import httpx
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from ..models import User
from ..deps import get_current_user

router = APIRouter(prefix="/api/ai", tags=["小德AI"])

OLLAMA_URL = "http://127.0.0.1:11434/api/chat"
MODEL = "qwen2.5:0.5b-instruct"
NUM_CTX = 1024
MAX_TURNS = 8
RATE_LIMIT = 10
RATE_WINDOW = 60

SYSTEM_PROMPT = (
    "你是「小德」，小德社区 App 的 AI 助手。"
    "小德社区是一个中文社交社区，用户可以发帖、私信、逛论坛、下载应用、看资源。"
    "回答要简短、友好、口语化，一般不超过 60 字。"
    "不确定的事就说不知道，不要编造功能。"
)

_hits = defaultdict(deque)


def _check_rate(uid: int) -> None:
    now = time.time()
    dq = _hits[uid]
    while dq and now - dq[0] > RATE_WINDOW:
        dq.popleft()
    if len(dq) >= RATE_LIMIT:
        raise HTTPException(status_code=429, detail="说得太快啦，休息一下再聊～")
    dq.append(now)


class MsgIn(BaseModel):
    role: str = Field(default="user")
    content: str = Field(default="", max_length=2000)


class ChatIn(BaseModel):
    messages: List[MsgIn] = Field(default_factory=list)
    stream: bool = False


def _build(messages: List[MsgIn]) -> list:
    msgs = [{"role": "system", "content": SYSTEM_PROMPT}]
    tail = messages[-MAX_TURNS * 2:]
    for m in tail:
        role = m.role if m.role in ("user", "assistant") else "user"
        txt = (m.content or "").strip()
        if not txt:
            continue
        msgs.append({"role": role, "content": txt[:800]})
    return msgs


@router.post("/chat", summary="与小德对话")
async def chat(data: ChatIn, current: User = Depends(get_current_user)):
    _check_rate(current.id)

    msgs = _build(data.messages)
    if len(msgs) <= 1:
        raise HTTPException(status_code=400, detail="说点什么吧")

    payload = {
        "model": MODEL,
        "messages": msgs,
        "stream": bool(data.stream),
        "options": {
            "num_ctx": NUM_CTX,
            "temperature": 0.7,
            "top_p": 0.9,
            "repeat_penalty": 1.1,
            "num_predict": 220,
        },
    }

    if not data.stream:
        try:
            async with httpx.AsyncClient(timeout=90.0) as cli:
                r = await cli.post(OLLAMA_URL, json=payload)
                if r.status_code != 200:
                    raise HTTPException(status_code=502, detail="小德走神了，稍后再试")
                d = r.json()
        except httpx.TimeoutException:
            raise HTTPException(status_code=504, detail="小德思考太久了，换个问法试试")
        except HTTPException:
            raise
        except Exception:
            raise HTTPException(status_code=502, detail="小德暂时联系不上")

        return {
            "reply": (d.get("message") or {}).get("content", "").strip(),
            "tokens": d.get("eval_count", 0),
        }

    async def gen():
        try:
            async with httpx.AsyncClient(timeout=90.0) as cli:
                async with cli.stream("POST", OLLAMA_URL, json=payload) as resp:
                    if resp.status_code != 200:
                        yield "data: " + json.dumps({"error": "小德走神了"}, ensure_ascii=False) + "\n\n"
                        return
                    async for line in resp.aiter_lines():
                        if not line.strip():
                            continue
                        try:
                            obj = json.loads(line)
                        except Exception:
                            continue
                        piece = (obj.get("message") or {}).get("content", "")
                        if piece:
                            yield "data: " + json.dumps({"delta": piece}, ensure_ascii=False) + "\n\n"
                        if obj.get("done"):
                            yield "data: " + json.dumps({"done": True}) + "\n\n"
                            return
        except Exception:
            yield "data: " + json.dumps({"error": "连接中断"}, ensure_ascii=False) + "\n\n"

    return StreamingResponse(
        gen(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.get("/health", summary="小德健康检查")
async def health():
    try:
        async with httpx.AsyncClient(timeout=5.0) as cli:
            r = await cli.get("http://127.0.0.1:11434/api/version")
            return {"ok": True, "ollama": r.json().get("version"), "model": MODEL}
    except Exception as e:
        return {"ok": False, "error": str(e)}
