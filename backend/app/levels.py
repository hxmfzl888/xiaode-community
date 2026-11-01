# -*- coding: utf-8 -*-
"""小德助手 API - 等级系统：经验换算 / 任务配置 / 权限门槛"""

# 升级所需经验递增（升到 n 级累计需要 exp）
def exp_for_level(level: int) -> int:
    """到达 level 级所需的累计经验。公式：50*(L-1)*L /2 的变体——越到后面越难。"""
    if level <= 1:
        return 0
    return int(50 * (level - 1) * level / 2)


def level_for_exp(exp: int) -> int:
    """根据累计经验算当前等级"""
    if exp < 0:
        exp = 0
    lv = 1
    while lv < 99 and exp >= exp_for_level(lv + 1):
        lv += 1
    return lv


def level_progress(exp: int):
    """返回 (当前等级, 当前级起始exp, 下一级所需累计exp, 是否满级)"""
    lv = level_for_exp(exp)
    cur = exp_for_level(lv)
    nxt = exp_for_level(lv + 1)
    return lv, cur, nxt


# 任务配置：key -> (名称, 经验, 每日上限次数, 说明)
# 每日上限：0 表示不限次数
TASKS = {
    "sign":        ("\u6bcf\u65e5\u7b7e\u5230", 20, 1, "\u6bcf\u5929\u7b7e\u5230\u4e00\u6b21"),
    "post":        ("\u53d1\u5e03\u52a8\u6001", 15, 3, "\u4eca\u65e5\u53d1\u5e03\u52a8\u6001"),
    "comment":     ("\u53d1\u8868\u8bc4\u8bba", 5, 10, "\u4eca\u65e5\u8bc4\u8bba\u522b\u4eba"),
    "like":        ("\u70b9\u8d5e", 2, 20, "\u4eca\u65e5\u70b9\u8d5e"),
    "diary":       ("\u5199\u65e5\u8bb0", 10, 2, "\u4eca\u65e5\u5199\u65e5\u8bb0"),
    "chat":        ("\u804a\u5929", 3, 10, "\u4eca\u65e5\u53d1\u6d88\u606f"),
    "friend":      ("\u6dfb\u52a0\u597d\u53cb", 30, 5, "\u4eca\u65e5\u901a\u8fc7\u597d\u53cb"),
    "study":       ("\u5b66\u4e60\u4e13\u6ce8", 25, 3, "\u5b8c\u6210\u4e00\u6b21\u5b66\u4e60\u8ba1\u65f6"),
}

# 权限门槛（新版：聊天视频无等级限制；动态按等级放宽）
VIDEO_MIN_LEVEL = 1        # 聊天发视频：无限制

def can_send_video(level: int) -> bool:
    """聊天发视频：全部放开，无等级限制"""
    return True

def moment_image_limit(level: int) -> int:
    """动态可发图片数量上限（随等级提升）"""
    if level >= 12: return 9
    if level >= 8:  return 6
    if level >= 5:  return 4
    if level >= 3:  return 3
    return 1

def moment_video_limit(level: int) -> int:
    """动态可发视频数量上限（低等级为 0=不能发）"""
    if level >= 12: return 3
    if level >= 8:  return 2
    if level >= 5:  return 1
    return 0

def can_send_moment_video(level: int) -> bool:
    return moment_video_limit(level) > 0

def can_custom_chat(level: int) -> bool:
    return level >= 4
NICK_FX_MIN_LEVEL = 5    # >=5 级名字有金色特效
AVATAR_RING_MIN_LEVEL = 8  # >=8 级头像有光环




def level_effect(level: int) -> str:
    """返回等级特效标识，供 App 渲染"""
    if level >= 8:
        return "aurora"    # 极光
    if level >= 5:
        return "gold"      # 金色
    if level >= 3:
        return "blue"      # 蓝色
    return "normal"
