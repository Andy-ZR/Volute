#!/usr/bin/env python3
"""把设计几何转成 Android VectorDrawable：
  1) ic_foreground.xml        —— 彩色前景（多色路径）
  2) ic_launcher_monochrome.xml —— 单色层（evenOdd 真挖洞：壳上开螺旋缺口、瞳孔挖洞）
  3) mono-preview.svg         —— 单色层的 SVG 预览（用于肉眼核对洞挖对没）

坐标全部走 Android <group> 变换（pivot 54,54 / scale 1.08 / translate -2.97,2.16），
与设计稿的 SVG 变换等价：p' = 1.08*p + (-7.29,-2.16)。
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_icon_geom as G   # 几何常量（见 gen-icon.py 同名值）

def circle_path(cx, cy, r):
    return (f"M{cx - r:.2f},{cy:.2f} "
            f"a{r:.2f},{r:.2f} 0 1 0 {2*r:.2f},0 "
            f"a{r:.2f},{r:.2f} 0 1 0 {-2*r:.2f},0 Z")

def ribbon(points, w):
    """把折线（螺线中心线）扩成闭合带状 path（用于单色层"挖洞"）。"""
    import math
    left, right = [], []
    n = len(points)
    for i, (x, y) in enumerate(points):
        if i == 0:
            dx, dy = points[1][0] - x, points[1][1] - y
        elif i == n - 1:
            dx, dy = x - points[i - 1][0], y - points[i - 1][1]
        else:
            dx, dy = points[i + 1][0] - points[i - 1][0], points[i + 1][1] - points[i - 1][1]
        L = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / L, dx / L
        left.append((x + nx * w / 2, y + ny * w / 2))
        right.append((x - nx * w / 2, y - ny * w / 2))
    d = 'M%.2f,%.2f ' % left[0] + ' '.join('L%.2f,%.2f' % p for p in left[1:])
    d += ' L%.2f,%.2f ' % right[-1] + ' '.join('L%.2f,%.2f' % p for p in reversed(right[:-1]))
    return d + ' Z'

def vector(body):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}
</vector>
'''

GROUP_OPEN = '''    <!-- 安全区：中心 66x66（21..87）＝OEM 掩码永不裁的区域；标志 62.6x59.4，在 48–66dp 之间 -->
    <group
        android:pivotX="54"
        android:pivotY="54"
        android:scaleX="1.08"
        android:scaleY="1.08"
        android:translateX="-2.97"
        android:translateY="2.16">'''
GROUP_CLOSE = '    </group>'

def path(d, fill=None, stroke=None, sw=None, cap='round', join='round', even_odd=False):
    a = []
    if even_odd: a.append('        android:fillType="evenOdd"')
    a.append('        android:pathData="%s"' % d)
    if fill:   a.append('        android:fillColor="%s"' % fill)
    else:      a.append('        android:fillColor="#00000000"')
    if stroke:
        a.append('        android:strokeColor="%s"' % stroke)
        a.append('        android:strokeWidth="%s"' % round(sw, 2))
        a.append('        android:strokeLineCap="%s"' % cap)
        a.append('        android:strokeLineJoin="%s"' % join)
    return '    <path\n' + '\n'.join(a) + ' />'

def build_foreground(u=2.2):
    G.set_u(u)
    K, D = G.K, G.D
    out = [GROUP_OPEN]
    out.append(path(G.STALK_A, stroke=G.C_BODY, sw=G.S))
    out.append(path(G.STALK_B, stroke=G.C_BODY, sw=G.S))
    out.append(path(G.BODY, fill=G.C_BODY, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.SHELL), fill=G.C_SHELL, stroke=G.INK, sw=K))
    out.append(path(G.SPIRAL, stroke=G.C_RIDGE, sw=D))
    out.append(path(G.SMILE, stroke=G.C_MOUTH, sw=D))
    out.append(path(G.RECV_ARC, stroke=G.C_STEEL, sw=G.RA))
    out.append(path(circle_path(*G.RECV_END_A, r=G.RR), fill=G.C_STEEL, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.RECV_END_B, r=G.RR), fill=G.C_STEEL, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.EYE_A, r=G.RE), fill=G.C_WHITE, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.EYE_B, r=G.RE), fill=G.C_WHITE, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.PUPIL_A, r=G.RP), fill=G.INK))
    out.append(path(circle_path(*G.PUPIL_B, r=G.RP), fill=G.INK))
    out.append(GROUP_CLOSE)
    return vector('\n'.join(out))

def build_monochrome(u=2.2, tint='#FFFFFFFF'):
    """单色层：无色也要读懂 → 所有"内部结构"必须是真的透明洞。
       ① 身体 = 身体轮廓 减去 壳圆（evenOdd）→ 壳的位置留白
       ② 壳   = 壳圆 减去 螺旋带（evenOdd）→ 壳上挖出螺旋缺口
       ③ 眼   = 眼球 减去 瞳孔
       ④ 眼柄/听筒 直接画（同色即融合）"""
    G.set_u(u)
    K = G.K
    pts = G.spiral_points()
    out = [GROUP_OPEN]
    cx, cy, r = G.SHELL
    d_body = G.BODY + ' ' + circle_path(cx, cy, r - 0.6)
    out.append(path(d_body, fill=tint, even_odd=True))
    d_shell = circle_path(*G.SHELL) + ' ' + ribbon(pts, G.D)
    out.append(path(d_shell, fill=tint, even_odd=True))
    out.append(path('M39,44.5 L38.07,39.4', stroke=tint, sw=G.S))
    out.append(path('M48,44.5 L48.46,39.47', stroke=tint, sw=G.S))
    out.append(path(G.RECV_ARC, stroke=tint, sw=G.RA))
    out.append(path(circle_path(*G.RECV_END_A, r=G.RR), fill=tint))
    out.append(path(circle_path(*G.RECV_END_B, r=G.RR), fill=tint))
    for e, p in ((G.EYE_A, G.PUPIL_A), (G.EYE_B, G.PUPIL_B)):
        out.append(path(circle_path(*e, r=G.RE) + ' ' + circle_path(*p, r=G.RP), fill=tint, even_odd=True))
    out.append(GROUP_CLOSE)
    return vector('\n'.join(out))

def build_preview_svg(u=2.2):
    """彩色预览 / 源文件预览（art/volute-icon.svg）——与 drawable 同几何、同颜色"""
    G.set_u(u)
    K, D = G.K, G.D
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108">
  <!-- Volute 应用图标源（由 art/gen/gen_android.py 生成；改参数请改生成器再重跑）
       u={u}：基准线宽 2.2；安全区＝中心 66x66（21..87），标志 62.6x59.4（48–66dp 之间）
       参考观感：Den Den Mushi Freed（粉身/靛蓝壳/眼白黑瞳/笑脸）；形状自绘 -->
  <rect width="108" height="108" fill="{G.BG}"/>
  <g transform="translate(54,54) scale(1.08) translate(-54,-54) translate(-2.75,2)" stroke-linejoin="round" stroke-linecap="round">
    <path d="{G.STALK_A}" fill="none" stroke="{G.C_BODY}" stroke-width="{G.S}"/>
    <path d="{G.STALK_B}" fill="none" stroke="{G.C_BODY}" stroke-width="{G.S}"/>
    <path d="{G.BODY}" fill="{G.C_BODY}" stroke="{G.INK}" stroke-width="{K}"/>
    <circle cx="{G.SHELL[0]}" cy="{G.SHELL[1]}" r="{G.SHELL[2]}" fill="{G.C_SHELL}" stroke="{G.INK}" stroke-width="{K}"/>
    <path d="{G.SPIRAL}" fill="none" stroke="{G.C_RIDGE}" stroke-width="{D}"/>
    <path d="{G.SMILE}" fill="none" stroke="{G.C_MOUTH}" stroke-width="{D}"/>
    <path d="{G.RECV_ARC}" fill="none" stroke="{G.C_STEEL}" stroke-width="{G.RA}"/>
    <circle cx="{G.RECV_END_A[0]}" cy="{G.RECV_END_A[1]}" r="{G.RR:.2f}" fill="{G.C_STEEL}" stroke="{G.INK}" stroke-width="{K}"/>
    <circle cx="{G.RECV_END_B[0]}" cy="{G.RECV_END_B[1]}" r="{G.RR:.2f}" fill="{G.C_STEEL}" stroke="{G.INK}" stroke-width="{K}"/>
    <circle cx="{G.EYE_A[0]}" cy="{G.EYE_A[1]}" r="{G.RE}" fill="{G.C_WHITE}" stroke="{G.INK}" stroke-width="{K}"/>
    <circle cx="{G.EYE_B[0]}" cy="{G.EYE_B[1]}" r="{G.RE}" fill="{G.C_WHITE}" stroke="{G.INK}" stroke-width="{K}"/>
    <circle cx="{G.PUPIL_A[0]}" cy="{G.PUPIL_A[1]}" r="{G.RP}" fill="{G.INK}"/>
    <circle cx="{G.PUPIL_B[0]}" cy="{G.PUPIL_B[1]}" r="{G.RP}" fill="{G.INK}"/>
  </g>
</svg>
'''


def mono_preview_svg(u=2.2):
    G.set_u(u)
    K, pts = G.K, G.spiral_points()
    d_body = G.BODY + ' ' + circle_path(*G.SHELL)
    d_shell = circle_path(*G.SHELL) + ' ' + ribbon(pts, G.D)
    eyes = ''
    for e, p in ((G.EYE_A, G.PUPIL_A), (G.EYE_B, G.PUPIL_B)):
        eyes += '  <path fill-rule="evenodd" fill="#FFFFFF" d="%s %s"/>\n' % (
            circle_path(*e, r=G.RE), circle_path(*p, r=G.RP))
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108">
  <rect width="108" height="108" fill="{G.BG}"/>
  <g transform="translate(54,54) scale(1.08) translate(-54,-54) translate(-2.75,2)">
    <path fill-rule="evenodd" fill="#FFFFFF" d="{d_body}"/>
    <path fill-rule="evenodd" fill="#FFFFFF" d="{d_shell}"/>
    <path fill="none" stroke="#FFFFFF" stroke-width="{G.S}" stroke-linecap="round" d="M39,44.5 L38.07,39.4"/>
    <path fill="none" stroke="#FFFFFF" stroke-width="{G.S}" stroke-linecap="round" d="M48,44.5 L48.46,39.47"/>
    <path fill="none" stroke="#FFFFFF" stroke-width="{G.RA}" stroke-linecap="round" d="{G.RECV_ARC}"/>
    <circle cx="{G.RECV_END_A[0]}" cy="{G.RECV_END_A[1]}" r="{G.RR:.2f}" fill="#FFFFFF"/>
    <circle cx="{G.RECV_END_B[0]}" cy="{G.RECV_END_B[1]}" r="{G.RR:.2f}" fill="#FFFFFF"/>
{eyes}  </g>
</svg>
'''


def build_shortcut(u=2.2):
    """快捷方式图标：深色圆底 + 同一套图形（短路图标不能自适应，所以要一个完整圆底）"""
    G.set_u(u)
    K = G.K
    out = ['    <!-- 深色圆底（r=36＝72dp 可视区）＋ 与 ic_foreground.xml 同源的图形；改一处要同步另一处 -->',
           '    <path', '        android:pathData="%s"' % circle_path(54, 54, 36),
           f'        android:fillColor="{G.BG}" />']
    out.append(path(G.STALK_A, stroke=G.C_BODY, sw=G.S))
    out.append(path(G.STALK_B, stroke=G.C_BODY, sw=G.S))
    out.append(path(G.BODY, fill=G.C_BODY, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.SHELL), fill=G.C_SHELL, stroke=G.INK, sw=K))
    out.append(path(G.SPIRAL, stroke=G.C_RIDGE, sw=G.D))
    out.append(path(G.SMILE, stroke=G.C_MOUTH, sw=G.D))
    out.append(path(G.RECV_ARC, stroke=G.C_STEEL, sw=G.RA))
    out.append(path(circle_path(*G.RECV_END_A, r=G.RR), fill=G.C_STEEL, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.RECV_END_B, r=G.RR), fill=G.C_STEEL, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.EYE_A, r=G.RE), fill=G.C_WHITE, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.EYE_B, r=G.RE), fill=G.C_WHITE, stroke=G.INK, sw=K))
    out.append(path(circle_path(*G.PUPIL_A, r=G.RP), fill=G.INK))
    out.append(path(circle_path(*G.PUPIL_B, r=G.RP), fill=G.INK))
    body = GROUP_OPEN.join(['', '']) + '\n'  # 占位，实际用下方 group
    body = GROUP_OPEN + '\n' + '\n'.join(out) + '\n' + GROUP_CLOSE
    return vector(body)


def build_notification(u=2.2):
    """状态栏 / 通知小图标（24dp，白色单色，系统会强制上色）。
       与启动图标的差别（刻意的）：
         · 画布填满（24 视口里占 21），不留启动图标那种 18dp 掩码边距
         · 细节简化：壳改"圆环"（洞够大，24dp 下读得出是壳），去掉细螺线
       why：细线在 24dp 只有 0.7px，会糊成灰；通知图标按 Material system icon 规矩应极简。"""
    G.set_u(u)
    S = 0.355           # 设计空间 → 24 视口（图形占约 20/24）
    px, py = 54, 54
    tx, ty = -41.83, -41.43   # 使图形中心 (53.5,52.5) 落到 (12,12)
    cx, cy, r = G.SHELL
    d_body = G.BODY + ' ' + circle_path(cx, cy, r + 2.4)   # 与壳留 2.4 的缝（24dp 下约 0.9px）→ 壳才读得出来
    d_shell = circle_path(cx, cy, r) + ' ' + circle_path(cx, cy, 6.0)
    out = ['    <!-- Volute 通知图标：蜗牛剪影 + 圆环壳（由 art/gen/gen_android.py 生成）-->',
           '    <group',
           f'        android:pivotX="{px}"',
           f'        android:pivotY="{py}"',
           f'        android:scaleX="{S}"',
           f'        android:scaleY="{S}"',
           f'        android:translateX="{tx}"',
           f'        android:translateY="{ty}">',
           path(d_body, fill='#FFFFFFFF', even_odd=True),
           path(d_shell, fill='#FFFFFFFF', even_odd=True),
           path(G.STALK_A, stroke='#FFFFFFFF', sw=G.S),
           path(G.STALK_B, stroke='#FFFFFFFF', sw=G.S),
           path(G.RECV_ARC, stroke='#FFFFFFFF', sw=G.RA),
           path(circle_path(*G.RECV_END_A, r=G.RR), fill='#FFFFFFFF'),
           path(circle_path(*G.RECV_END_B, r=G.RR), fill='#FFFFFFFF'),
           '    </group>']
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n'
            '    android:height="24dp"\n'
            '    android:viewportWidth="24"\n'
            '    android:viewportHeight="24">\n'
            + '\n'.join(out) + '\n</vector>\n')


def notification_preview_svg(u=2.2):
    G.set_u(u)
    cx, cy, r = G.SHELL
    d_body = G.BODY + ' ' + circle_path(cx, cy, r + 2.4)   # 与壳留 2.4 的缝（24dp 下约 0.9px）→ 壳才读得出来
    d_shell = circle_path(cx, cy, r) + ' ' + circle_path(cx, cy, 6.0)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 24 24">
  <rect width="24" height="24" fill="#0E1117"/>  <!-- 预览底：保持深色，白剪影才看得清 -->
  <g transform="translate(-7,-6.6) scale(0.355)">
    <path fill-rule="evenodd" fill="#fff" d="{d_body}"/>
    <path fill-rule="evenodd" fill="#fff" d="{d_shell}"/>
    <path fill="none" stroke="#fff" stroke-width="{G.S}" stroke-linecap="round" d="M39,44.5 L38.07,39.4"/>
    <path fill="none" stroke="#fff" stroke-width="{G.S}" stroke-linecap="round" d="M48,44.5 L48.46,39.47"/>
    <path fill="none" stroke="#fff" stroke-width="{G.RA}" stroke-linecap="round" d="{G.RECV_ARC}"/>
    <circle cx="{G.RECV_END_A[0]}" cy="{G.RECV_END_A[1]}" r="{G.RR:.2f}" fill="#fff"/>
    <circle cx="{G.RECV_END_B[0]}" cy="{G.RECV_END_B[1]}" r="{G.RR:.2f}" fill="#fff"/>
  </g>
</svg>
'''


if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else 'all'
    if mode in ('all', 'svg'):
        open('volute-icon.svg', 'w').write(build_preview_svg())
        print('wrote volute-icon.svg')
    if mode in ('all', 'notif'):
        open('ic_notification.xml', 'w').write(build_notification())
        open('notification-preview.svg', 'w').write(notification_preview_svg())
        print('wrote ic_notification.xml + notification-preview.svg')
    if mode in ('all', 'shortcut'):
        open('ic_shortcut_icon.xml', 'w').write(build_shortcut())
        print('wrote ic_shortcut_icon.xml')
    if mode in ('all', 'fg'):
        open('ic_foreground.xml', 'w').write(build_foreground())
        print('wrote ic_foreground.xml')
    if mode in ('all', 'mono'):
        open('mono-preview.svg', 'w').write(mono_preview_svg())
        print('wrote mono-preview.svg')
        open('ic_launcher_monochrome.xml', 'w').write(build_monochrome())
        print('wrote ic_launcher_monochrome.xml')
