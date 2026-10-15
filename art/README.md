# art/ — 品牌资产

## 本项目的标记

| 文件 | 说明 |
|---|---|
| `volute-mark.svg` | **本项目标记的源文件**（几何螺旋＝涡卷）。与 `termux-app/src/main/res/drawable/ic_foreground.xml` **同源**，改一处要同步另一处 |

- 配色：描边 `#5EF0C8`（亮青绿）· 底色 `#0E1117`（深墨蓝，落在 `termux-app/src/main/res/values/colors.xml` 的 `ic_launcher_background`）。
- 形态：自适应图标层（`termux-app/src/main/res/mipmap-anydpi/ic_launcher*.xml`）引用 `@drawable/ic_foreground`；本项目 `minSdk = 30`，**只需矢量自适应图标，无需各密度 PNG**。

## 上游原始资产（历史留存，**app 已不再使用**）

`ic_launcher.svg`（上游"屏幕 + 光标块"图标）· `feature-graphic.svg` · `tv-banner.svg` 及其生成脚本 —— 均为**上游 `termux-play-store/termux-apps` 带进来的原始文件**，保留以见来历；本项目图标已换成上面的涡卷标记。

> ⚠️ 这些生成脚本（`generate-*.sh` / `copy-to-other-apps.sh`）里的路径仍是**上游布局**（`../app/src/main/...`），而本仓 app 模块叫 `termux-app/` → **在本仓直接跑会失败**，未做适配（本仓当前也不需要它们：图标是纯矢量）。

## 许可

本目录随本仓继承上游许可 **GPLv3**（例外见仓库根说明）。
