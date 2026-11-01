# -*- coding: utf-8 -*-
"""小德助手 API - 邮件服务（QQ 邮箱 SMTP 发送验证码）"""
import os
import io
import smtplib
import ssl
import random
from email.mime.text import MIMEText
from email.header import Header
from email.utils import formataddr

# ===== 自动加载项目根目录的 .env（修复环境变量未生效问题）=====
def _load_env_file():
    """从 /opt/xiaode-api/.env 读取配置到 os.environ（不覆盖已有环境变量）"""
    here = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        os.path.join(os.path.dirname(here), ".env"),
        os.path.join(here, ".env"),
    ]
    for fp in candidates:
        if not os.path.isfile(fp):
            continue
        try:
            with io.open(fp, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    k, v = line.split("=", 1)
                    k = k.strip()
                    v = v.strip().strip('"').strip("'")
                    if k and k not in os.environ:
                        os.environ[k] = v
        except Exception:
            pass

_load_env_file()


SMTP_HOST = os.environ.get("XIAODE_SMTP_HOST", "smtp.qq.com")
SMTP_PORT = int(os.environ.get("XIAODE_SMTP_PORT", "465"))
SMTP_USER = os.environ.get("XIAODE_SMTP_USER", "")
SMTP_PASS = os.environ.get("XIAODE_SMTP_PASS", "")
SMTP_FROM = os.environ.get("XIAODE_SMTP_FROM", "\u5c0f\u5fb7\u52a9\u624b")

def is_configured() -> bool:
    return bool(SMTP_USER and SMTP_PASS)

def make_code(digits: int = 6) -> str:
    return "".join(str(random.randint(0, 9)) for _ in range(digits))

def _template(code: str, purpose: str) -> str:
    titles = {"register": "\u6ce8\u518c\u8d26\u53f7", "reset": "\u91cd\u7f6e\u5bc6\u7801",
              "bind": "\u6362\u7ed1\u90ae\u7bb1", "login": "\u767b\u5f55\u9a8c\u8bc1"}
    action = titles.get(purpose, "\u8eab\u4efd\u9a8c\u8bc1")
    return ("<div style=\"font-family:sans-serif;max-width:480px;margin:0 auto;padding:28px;"
            "background:#f5f6fa;border-radius:14px;\">"
            "<h2 style=\"color:#3F7FF0;margin:0 0 8px;\">\u5c0f\u5fb7\u52a9\u624b</h2>"
            "<div style=\"background:#fff;border-radius:12px;padding:24px;\">"
            "<p style=\"font-size:15px;color:#222;\">\u60a8\u6b63\u5728\u8fdb\u884c <b>" + action + "</b>\uff0c\u9a8c\u8bc1\u7801\u4e3a\uff1a</p>"
            "<div style=\"font-size:34px;font-weight:bold;letter-spacing:8px;color:#3F7FF0;"
            "text-align:center;padding:16px 0;\">" + code + "</div>"
            "<p style=\"font-size:13px;color:#999;\">\u9a8c\u8bc1\u7801 10 \u5206\u949f\u5185\u6709\u6548\uff0c\u8bf7\u52ff\u6cc4\u9732\u7ed9\u4ed6\u4eba\u3002</p>"
            "</div></div>")

def send_code(to_email: str, code: str, purpose: str = "register") -> bool:
    if not is_configured():
        return False
    titles = {"register": "\u6ce8\u518c\u8d26\u53f7", "reset": "\u91cd\u7f6e\u5bc6\u7801",
              "bind": "\u6362\u7ed1\u90ae\u7bb1", "login": "\u767b\u5f55\u9a8c\u8bc1"}
    subject = "\u3010\u5c0f\u5fb7\u52a9\u624b\u3011%s\u9a8c\u8bc1\u7801\uff1a%s" % (titles.get(purpose, "\u9a8c\u8bc1"), code)
    msg = MIMEText(_template(code, purpose), "html", "utf-8")
    msg["Subject"] = Header(subject, "utf-8")
    msg["From"] = formataddr((str(Header(SMTP_FROM, "utf-8")), SMTP_USER))
    msg["To"] = to_email
    try:
        if SMTP_PORT == 465:
            ctx = ssl.create_default_context()
            with smtplib.SMTP_SSL(SMTP_HOST, SMTP_PORT, context=ctx, timeout=15) as s:
                s.login(SMTP_USER, SMTP_PASS)
                s.sendmail(SMTP_USER, [to_email], msg.as_string())
        else:
            with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as s:
                s.starttls(context=ssl.create_default_context())
                s.login(SMTP_USER, SMTP_PASS)
                s.sendmail(SMTP_USER, [to_email], msg.as_string())
        return True
    except Exception as e:
        print("[mailer] send failed: %s" % e)
        return False
