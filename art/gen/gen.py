#!/usr/bin/env python3
"""生成几何干净的图标用路径：
1) 阿基米德螺线（涡卷/壳内螺线）—— 采样后输出 SVG path（L 段，12° 一步）
2) 听筒（受话器）—— 经典两瓣 + 中杆的闭合剪影 path（圆角矩形并集近似）
输出直接可粘进 SVG / VectorDrawable。
"""
import math

def spiral(cx, cy, r0, r1, turns, deg_step=12, start=0.0):
    th_max = turns * 2 * math.pi
    b = (r1 - r0) / th_max
    pts = []
    th = 0.0
    while th <= th_max + 1e-9:
        r = r0 + b * th
        pts.append((cx + r * math.cos(th + start), cy + r * math.sin(th + start)))
        th += math.radians(deg_step)
    d = 'M%.2f,%.2f' % pts[0] + ''.join(' L%.2f,%.2f' % p for p in pts[1:])
    return d

def rr(cx, cy, w, h, r):
    """圆角矩形 path（左侧起，顺时针）"""
    x0, x1 = cx - w / 2, cx + w / 2
    y0, y1 = cy - h / 2, cy + h / 2
    return (f'M{x0 + r:.2f},{y0:.2f} L{x1 - r:.2f},{y0:.2f} '
            f'A{r:.2f},{r:.2f} 0 0 1 {x1:.2f},{y0 + r:.2f} L{x1:.2f},{y1 - r:.2f} '
            f'A{r:.2f},{r:.2f} 0 0 1 {x1 - r:.2f},{y1:.2f} L{x0 + r:.2f},{y1:.2f} '
            f'A{r:.2f},{r:.2f} 0 0 1 {x0:.2f},{y1 - r:.2f} L{x0:.2f},{y0 + r:.2f} '
            f'A{r:.2f},{r:.2f} 0 0 1 {x0 + r:.2f},{y0:.2f} Z')

if __name__ == '__main__':
    print('== 壳内螺线 中心(63,58) r 4.5→17 2.15圈')
    print(spiral(63, 58, 4.5, 17.0, 2.15))
    print()
    print('== 听筒 局部坐标（中心 0,0）：中杆 + 两瓣')
    print('bar   :', rr(0, 0, 24, 8, 4))
    print('lobeL :', rr(-12.5, 0, 8, 15, 4))
    print('lobeR :', rr(12.5, 0, 8, 15, 4))
