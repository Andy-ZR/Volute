#!/usr/bin/env python3
"""量 SVG 渲染图里的"着墨"包围盒（用于核对图形是否居中）。
用法: measure.py <png> <bg-hex> <放大倍数>   例: measure.py png/x.svg.png 7CC5E4 10
"""
import sys, zlib, struct

def load_png(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n'
    i, idat, w, h, ct, bd = 8, b'', 0, 0, 0, 0
    while i < len(d):
        ln = struct.unpack('>I', d[i:i+4])[0]
        typ, data = d[i+4:i+8], d[i+8:i+8+ln]
        if typ == b'IHDR':
            w, h, bd, ct, _, _, inter = struct.unpack('>IIBBBBB', data)
        elif typ == b'IDAT':
            idat += data
        elif typ == b'IEND':
            break
        i += 12 + ln
    raw = zlib.decompress(idat)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    stride = w * ch
    rows, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        ft = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        for x in range(stride):
            a = line[x-ch] if x >= ch else 0
            b = prev[x]
            c = prev[x-ch] if x >= ch else 0
            if ft == 1: line[x] = (line[x]+a) & 255
            elif ft == 2: line[x] = (line[x]+b) & 255
            elif ft == 3: line[x] = (line[x]+(a+b)//2) & 255
            elif ft == 4:
                pp = a+b-c
                pa, pb, pc = abs(pp-a), abs(pp-b), abs(pp-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x]+pr) & 255
        rows.append(bytes(line)); prev = line
    return w, h, ch, rows

def main(path, bg, zoom):
    w, h, ch, rows = load_png(path)
    bgc = tuple(int(bg[i:i+2], 16) for i in (0, 2, 4))
    x0 = y0 = 10**9; x1 = y1 = -1
    for y in range(h):
        r = rows[y]
        for x in range(w):
            o = x*ch
            px = (r[o], r[o+1], r[o+2]) if ch >= 3 else (r[o],)*3
            if sum(abs(px[k]-bgc[k]) for k in range(3)) > 24:   # 容差：抗锯齿边缘不算
                x0 = min(x0, x); x1 = max(x1, x)
                y0 = min(y0, y); y1 = max(y1, y)
    z = float(zoom)
    vx0, vx1, vy0, vy1 = x0/z, (x1+1)/z, y0/z, (y1+1)/z
    print(f'{path}')
    print(f'  viewBox 单位: x[{vx0:.1f},{vx1:.1f}] y[{vy0:.1f},{vy1:.1f}]')
    print(f'  尺寸: {vx1-vx0:.1f} x {vy1-vy0:.1f}   中心: ({(vx0+vx1)/2:.2f}, {(vy0+vy1)/2:.2f})   视口中心 54,54')
    print(f'  边距: 左 {vx0:.1f} / 右 {108-vx1:.1f} / 上 {vy0:.1f} / 下 {108-vy1:.1f}')
    print(f'  若裁到中心 72x72(18..90): 左 {vx0-18:.1f} / 右 {90-vx1:.1f} / 上 {vy0-18:.1f} / 下 {90-vy1:.1f}')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
