# art/ — 品牌资产

## 应用图标（2026-10-16 起）

| 文件 | 说明 |
|---|---|
| `volute-icon.svg` | **应用图标源**（蜗牛 + 螺旋壳 + 壳上听筒）。**由生成器产出，别手改**——改参数请改 `gen/` 再重跑 |
| `gen/gen_icon_geom.py` | **几何与配色的单一来源**（形状坐标 / 基准线宽 u / 颜色 / 安全区）。要调形或调线宽只改这里 |
| `gen/gen_android.py` | 生成器：产出 `termux-app` 的三个 drawable + 两张预览 SVG |
| `gen/gen.py` | 通用几何辅助（阿基米德螺线采样、圆角矩形路径） |

用法（在 `art/gen/` 下跑）：

```sh
python3 gen_android.py all     # 生成 ic_foreground.xml / ic_launcher_monochrome.xml / ic_shortcut_icon.xml
                               #        + volute-icon.svg（彩色预览）+ mono-preview.svg（单色层预览）
```

生成物与落点（**改一处必须同步另一处**）：

| 生成物 | 落点 |
|---|---|
| `ic_foreground.xml` | `termux-app/src/main/res/drawable/ic_foreground.xml` |
| `ic_launcher_monochrome.xml` | `termux-app/src/main/res/drawable/ic_launcher_monochrome.xml`（**单色层**，`ic_launcher*.xml` 的 `<monochrome>` 指向它） |
| `ic_shortcut_icon.xml` | `termux-app/src/main/res/drawable/ic_shortcut_icon.xml`（快捷方式，深色圆底 + 同套图形） |
| `ic_service_notification.xml` | `termux-app/src/main/res/drawable/ic_service_notification.xml`（**状态栏/通知小图标**，24dp 白色单色） |

**通知小图标是单独一张图，不能复用启动图标**（2026-10-16 用户指出状态栏还是 Termux 之后补的）：它 24dp、单色、**画布要填满**（启动图标的 66dp 安全区留白在这里会显得很小），而且**细线在 24dp 只有 0.7px 会糊**。所以 `build_notification()` 特意做了简化：壳改**圆环**（洞够大），并让身体与壳之间留 2.4 单位缝（24dp 下约 0.9px）——否则身体与壳同色会糊成一坨。预览见 `notification-preview.svg`。

## 各模块怎么共享（**软链约定**）

`termux-style` / `termux-tasker` 的 `ic_foreground.xml` 与 `mipmap-anydpi/ic_launcher*.xml` **是链到 `termux-app` 的符号链接**（git 里存的就是 symlink）→ 改 `termux-app` 即全模块生效；但**新增 drawable 必须补链**，否则那两个模块编译时会找不到资源。`termux-api` 是例外：它是真实文件（manifest 直接指 `@drawable/ic_launcher`，非自适应）。

## 设计依据（画之前先看这些，别凭感觉）

- **安全区**：108×108dp 画布，中心 **66×66dp**（21..87）＝OEM 掩码永不裁的区域；标志须 **48–66dp**。出处：Android 官方 *Adaptive icons*。
- **线宽**：基准 `u`（当前 **2.2**，用户 2026-10-16 定），其余线宽全是 u 的倍数（见 `gen_icon_geom.py` 的 `set_u`）。
  换算基准：108 单位 → 48dp 时 1 单位 = 0.444px。细线与小尺寸可读性互斥，u=2.2 ≈ 0.98px @48dp。
- **单色层**（Android 13+ 主题图标）：不能用"浅色螺线"表达结构，必须**真挖洞**（evenOdd）——壳上螺旋缺口 + 瞳孔洞，无色也读得出"壳"。见 `mono-preview.svg`。
- **关键线 / 正交线**：壳径 ≈ 36–44dp 档；听筒倾角吸附 -15°。出处：Google Design *Designing Adaptive Icons*、图标网格通用实践。
- **居中与缩放（必须实测，别目测）**：`gen_icon_geom.py` 里 `ART_SCALE` / `ART_CENTER_DESIGN` / `ART_NUDGE` 三个常量决定图形大小与居中，**改完必须重新量**：
  ```sh
  qlmanage -t -s 1024 -o /tmp/m volute-icon.svg && python3 gen/measure.py /tmp/m/volute-icon.svg.png 7CC5E4 9.4815
  ```
  要看到 `中心 ≈ (54,54)`、掩码内边距左右/上下对称。
  ⚠️ **两条踩过的坑**：① **预览必须按"中心 72×72 + 掩码"裁切后再核对居中** —— 只看 108 整块方形**看不出偏移**（真机偏左 3.5 单位，纸面上完全正常）；② **居中平移要在缩放之后反算** —— 先目测平移、再绕中心放大 1.08，会把已有偏移等比放大（本次就是这么偏的）。
- **安全区余量（诚实说明）**：着墨 62×59 落在中心 66×66 内；但**严格圆形掩码**只保证距中心 **33** 以内不被裁，本图形最外侧（眼柄左上 / 身体左下）约 38–40 → 圆形桌面会轻切那两个角（MIUI、Play 都是圆角方形，不受影响）。要更保守就调小 `ART_SCALE`（如 0.95）重跑，居中和通知图标会跟着自动走。
- **通知小图标**：尺寸/居中由 `NOTIF_SCALE` / `NOTIF_CENTER` 推出，同样是生成器算的，别手改生成物。
- **观感参考**：One Piece 的 電伝虫（Den Den Mushi）**Freed** 个体（粉身 `#F0BCC9` / 靛蓝壳 `#5A4EA8` / 眼白黑瞳 / 笑脸）——**只借通用意象与配色，不用官方造型/画风/专有名词**（品牌规范：**只借通用意象与配色**，不用官方造型 / 画风 / 专有名词）。参考图只在本地看过，不随包分发、不进仓。

## 抽象标记（与图标分开）

| 文件 | 说明 |
|---|---|
| `volute-mark.svg` | 抽象标记（几何涡卷），供文档/品牌物料使用；**不再是应用图标源** |

## 上游原始资产（历史留存，**app 已不再使用**）

`ic_launcher.svg`（上游"屏幕 + 光标块"图标）· `feature-graphic.svg` · `tv-banner.svg` 及其生成脚本 —— 均为**上游 `termux-play-store/termux-apps` 带进来的原始文件**，保留以见来历。

> ⚠️ 这些生成脚本（`generate-*.sh` / `copy-to-other-apps.sh`）里的路径仍是**上游布局**（`../app/src/main/...`），而本仓 app 模块叫 `termux-app/` → **在本仓直接跑会失败**，未做适配（本仓当前也不需要它们：图标是纯矢量）。

## 许可

本目录随本仓继承上游许可 **GPLv3**（例外见仓库根说明）。
