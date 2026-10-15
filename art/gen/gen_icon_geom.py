#!/usr/bin/env python3
"""几何真单一来源（design 坐标，108 视口，未缩放）。
   gen-icon.py（SVG 预览）与 gen-android.py（drawable 生成）都从这里取数，
   避免"预览和实际 drawable 不一致"。"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen import spiral

# ── 基准线宽 u=2.2（用户 2026-10-16 定）→ 其余线宽都是 u 的倍数 ──
def set_u(u):
    global U, K, D, S, RA, RR, RE, RP
    U = u
    K = u          # 剪影描边
    D = u          # 螺线脊 / 嘴
    S = 2.7 * u    # 眼柄胶囊宽
    RA = 2.6 * u   # 听筒杆
    RR = 2.2 * u   # 听筒端头半径
    RE = 7.0       # 眼球半径（固定：光学修正，非等大网格）
    RP = 2.8       # 瞳孔半径

set_u(2.2)

# ── 形状 ──
SHELL  = (65.0, 57.0, 16.0)     # 壳：圆心 + 半径
EYE_A, EYE_B     = (37.0, 33.5), (49.0, 33.5)
PUPIL_A, PUPIL_B = (37.8, 32.6), (49.8, 32.6)
STALK_A = "M39,44.5 L37,33.5"
STALK_B = "M48,44.5 L49,33.5"
BODY = ("M33,78.5 C28,78.5 26,75 26,71.5 C26,65 27.5,59 29.5,54 "
        "C31.5,47.5 36.5,42.5 44,42.5 C51.5,42.5 55.5,45.5 58.5,50 "
        "C61,54 64,57.5 68.5,58 C77,58.5 81,63 81,70 C81,74.5 79,78.5 74.5,78.5 Z")
SMILE = "M33.5,58.5 C37,62.5 41.5,62.5 44.5,59.5"
RECV_ARC = "M55.5,50 Q63.71,42.62 74.5,44.9"      # 听筒：-15°，两端压进壳缘
RECV_END_A, RECV_END_B = (55.5, 50.0), (74.5, 44.9)
SPIRAL = spiral(65, 57, 3.5, 12.5, 2.0, 10)       # 壳内螺线（2 圈）

# ── 配色 ──
C_BODY, C_SHELL, C_RIDGE = '#F0BCC9', '#5A4EA8', '#7A6EC6'
C_INK, C_STEEL, C_WHITE, C_MOUTH = '#23262E', '#CFD6DE', '#FBF8F4', '#8E4256'
# ── 居中与缩放（**实测**，别手调）──────────────────────────────
# 实测方法：渲染 volute-icon.svg（QuickLook）→ measure.py 量"着墨"包围盒
# 实测（2026-10-16，未平移版本）：设计空间着墨 bbox x[21.6,83.2] y[23.4,85.4] → 中心 (52.4, 54.4)？
#   注：下表是**定标后的最终值**，重新定标请重跑：measure.py <渲染图> <底色> <放大倍数>
ART_SCALE = 1.08                    # 安全区利用率（放大到 62×59，仍在 66×66 内）
ART_CENTER_DESIGN = (53.48, 52.49)  # 设计空间里的着墨中心（实测）
ART_NUDGE = (54 - ART_CENTER_DESIGN[0], 54 - ART_CENTER_DESIGN[1])   # → (0.52, 1.51)
NOTIF_SCALE = 0.355                 # 设计空间 → 24 视口（图形约占 20/24）
NOTIF_CENTER = 12.0                 # 24 视口的中心

BG = '#7CC5E4'   # 图标底色：晴空蓝（用户 2026-10-16 定；原作多数个体的环境色）


def spiral_points(step_deg=6):
    """螺线中心线的采样点（给"挖洞"用的带状路径）。"""
    import math
    cx, cy, r0, r1, turns = 65.0, 57.0, 3.5, 12.5, 2.0
    th_max = turns * 2 * math.pi
    b = (r1 - r0) / th_max
    pts, th = [], 0.0
    while th <= th_max + 1e-9:
        r = r0 + b * th
        pts.append((cx + r * math.cos(th), cy + r * math.sin(th)))
        th += math.radians(step_deg)
    return pts

INK = C_INK
