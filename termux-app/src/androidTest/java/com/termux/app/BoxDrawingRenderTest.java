package com.termux.app;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.view.TerminalRenderer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 渲染金样回归（跑在 AVD / 真机上）。
 *
 * <p>为什么需要：框线/块元素是<b>自绘几何</b>（{@code TerminalRenderer.drawBoxRun}，2026-10-17 引入），
 * 不走字体。若这段被改坏或退回字体绘制，症状是 TUI 的框线断行 / 粗细不一 / 被字体样式带歪——
 * 纯逻辑测试看不出来，只能看像素。
 *
 * <p>为什么不存基准 PNG：换字体、换 DPI、换 AVD 图就全变，维护成本高、误报多。
 * 这里断言的是<b>几何性质</b>，任何设备上都成立：
 * <ul>
 *   <li>一整列 {@code │} 自身墨迹区间内连续（跨行接缝不能断）；</li>
 *   <li>一整行 {@code ─} 自身墨迹区间内连续（跨列接缝不能断）；</li>
 *   <li>{@code █} 铺满字格；</li>
 *   <li><b>同一条竖线换字体后几何不变</b>（偏移差 ≤0.5px 且各自都贴字格中线）——这条最能卡住"退回字体"。</li>
 * </ul>
 *
 * <p><b>哪条断言能卡住什么（AVD API 34 实测，字体 × 字号 24 组矩阵）：</b>
 * <ul>
 *   <li>连续性只能卡"字形填不满字格"的字体：本机 `casual` 覆盖 0.83、空白 4–15px；
 *       但 `monospace`/`sans-serif`/`serif` 在 12–48px 全部覆盖 1.000、空白 0px——
 *       <b>光靠连续性是抓不到"退回字体"的</b>（系统有兜底字形）。</li>
 *   <li>位置（偏移字格中线）在字体间差异很大：自绘恒定 −0.5px（像素栅格所致，与字体/字号无关）；
 *       走字体时 monospace +0.0→+2.0px、sans-serif −0.5→+1.5、serif −1.5→−3.5、casual +0.0→+2.0。
 *       注意小字号偏差小（monospace 12px 只有 0.0px）→ <b>只在单一大字号上断言位置有运气成分</b>，
 *       所以位置断言必须成对比较两种字体。</li>
 * </ul>
 *
 * <p>排查用：{@code bash scripts/test-l3.sh} 加环境变量 {@code DUMP_IMAGES=true} 时把渲染结果写到
 * App 私有外部目录，再 {@code adb pull} 出来看；默认不落盘。
 */
@RunWith(AndroidJUnit4.class)
public class BoxDrawingRenderTest {

    private static final int COLS = 4;
    private static final int ROWS = 4;
    private static final int TEXT_SIZE = 48;

    /** 关光标（DECSET 25 关）+ 可选 SGR 前缀，避免块状光标混进断言。 */
    private static final String NO_CURSOR = "\033[?25l";

    private static final class SilentOutput extends TerminalOutput {
        @Override public void write(byte[] data, int offset, int count) { }
        @Override public void titleChanged(String oldTitle, String newTitle) { }
        @Override public void onCopyTextToClipboard(String text) { }
        @Override public void onPasteTextFromClipboard() { }
        @Override public void onBell() { }
        @Override public void onColorsChanged() { }
    }

    private static final class Rendered {
        final Bitmap bitmap;
        final TerminalRenderer renderer;

        Rendered(Bitmap bitmap, TerminalRenderer renderer) {
            this.bitmap = bitmap;
            this.renderer = renderer;
        }
    }

    private static TerminalEmulator emulatorWith(String content) {
        TerminalEmulator emulator = new TerminalEmulator(new SilentOutput(), COLS, ROWS, 1, 1, 0, null);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
        return emulator;
    }

    private static Rendered render(String content) {
        return render(content, Typeface.MONOSPACE);
    }

    private static Rendered render(String content, Typeface typeface) {
        TerminalEmulator emulator = emulatorWith(content);
        TerminalRenderer renderer = new TerminalRenderer(TEXT_SIZE, typeface);

        int width = Math.round(renderer.getFontWidth() * COLS);
        int height = renderer.getFontLineSpacing() * ROWS;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        renderer.render(emulator, canvas, 0, -1, -1, -1, -1);
        dumpIfRequested(bitmap, content);
        return new Rendered(bitmap, renderer);
    }

    /** 与背景色的差异是否足够大（抗锯齿边缘也算"有墨迹"）。 */
    private static boolean isInk(int pixel, int bg) {
        int dr = Math.abs(Color.red(pixel) - Color.red(bg));
        int dg = Math.abs(Color.green(pixel) - Color.green(bg));
        int db = Math.abs(Color.blue(pixel) - Color.blue(bg));
        return dr + dg + db > 60;
    }

    /** 右上角是空格，必为背景色。 */
    private static int background(Bitmap bitmap) {
        return bitmap.getPixel(bitmap.getWidth() - 2, 2);
    }

    private static int bestColumn(Bitmap bitmap, int bg) {
        int bestX = 0, best = -1;
        for (int x = 0; x < bitmap.getWidth(); x++) {
            int ink = 0;
            for (int y = 0; y < bitmap.getHeight(); y++) if (isInk(bitmap.getPixel(x, y), bg)) ink++;
            if (ink > best) { best = ink; bestX = x; }
        }
        return bestX;
    }

    private static int bestRow(Bitmap bitmap, int bg) {
        int bestY = 0, best = -1;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            int ink = 0;
            for (int x = 0; x < bitmap.getWidth(); x++) if (isInk(bitmap.getPixel(x, y), bg)) ink++;
            if (ink > best) { best = ink; bestY = y; }
        }
        return bestY;
    }

    /** 一列竖线：自身墨迹区间内应当连续，跨行接缝不能断。 */
    @Test
    public void verticalLineIsContinuous() {
        Rendered r = render(NO_CURSOR + "│\r\n│\r\n│\r\n│");
        Bitmap bitmap = r.bitmap;
        int bg = background(bitmap);
        int x = bestColumn(bitmap, bg);
        int h = bitmap.getHeight();

        int first = -1, last = -1;
        for (int y = 0; y < h; y++) {
            if (isInk(bitmap.getPixel(x, y), bg)) { if (first < 0) first = y; last = y; }
        }
        assertTrue("整列都没有墨迹（竖线没画）", first >= 0);

        int ink = 0, gap = 0, maxGap = 0;
        for (int y = first; y <= last; y++) {
            if (isInk(bitmap.getPixel(x, y), bg)) { ink++; maxGap = Math.max(maxGap, gap); gap = 0; }
            else gap++;
        }
        int span = last - first + 1;
        assertTrue(fmt("竖线在自身区间内覆盖率过低：%.3f", (double) ink / span),
                (double) ink / span > 0.98);
        assertTrue("竖线中间有 " + maxGap + "px 空白（断行）", maxGap <= 2);
    }

    /** 一行横线：自身墨迹区间内应当连续，跨列接缝不能断。 */
    @Test
    public void horizontalLineIsContinuous() {
        Rendered r = render(NO_CURSOR + "────");
        Bitmap bitmap = r.bitmap;
        int bg = background(bitmap);
        int y = bestRow(bitmap, bg);
        int w = bitmap.getWidth();

        int first = -1, last = -1;
        for (int x = 0; x < w; x++) {
            if (isInk(bitmap.getPixel(x, y), bg)) { if (first < 0) first = x; last = x; }
        }
        assertTrue("整行都没有墨迹（横线没画）", first >= 0);

        int ink = 0, gap = 0, maxGap = 0;
        for (int x = first; x <= last; x++) {
            if (isInk(bitmap.getPixel(x, y), bg)) { ink++; maxGap = Math.max(maxGap, gap); gap = 0; }
            else gap++;
        }
        int span = last - first + 1;
        assertTrue(fmt("横线在自身区间内覆盖率过低：%.3f", (double) ink / span),
                (double) ink / span > 0.98);
        assertTrue("横线中间有 " + maxGap + "px 空白（断列）", maxGap <= 2);

        // 横线要贯穿所有 4 个字符格（宽度不足说明 run 宽度算错）
        assertTrue("横线没铺满 4 个字符格（" + span + "px）",
                span >= Math.round(r.renderer.getFontWidth() * COLS * 0.95));
    }

    /** 块元素 █ 应铺满整个字格（退回字体绘制时四周会留白）。 */
    @Test
    public void fullBlockFillsCell() {
        Rendered r = render(NO_CURSOR + "█");
        Bitmap bitmap = r.bitmap;
        int bg = background(bitmap);

        int minX = Integer.MAX_VALUE, maxX = -1, minY = Integer.MAX_VALUE, maxY = -1;
        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                if (isInk(bitmap.getPixel(x, y), bg)) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
        }
        int w = maxX - minX + 1, h = maxY - minY + 1;
        int fontWidth = Math.round(r.renderer.getFontWidth());
        int lineSpacing = r.renderer.getFontLineSpacing();
        assertTrue("█ 宽度不足字格（" + w + "/" + fontWidth + "）", w >= fontWidth * 0.9);
        assertTrue("█ 高度不足字格（" + h + "/" + lineSpacing + "）", h >= lineSpacing * 0.9);
    }

    /**
     * 竖线必须贴字格中线（±1px），并且<b>换字体后几何不变</b>（两字体偏移差 ≤0.5px）。
     *
     * <p>为什么必须是"成对比较"：单独看一种字体、一个字号，字体画的线也可能恰好落在中线上
     * （实测 monospace 12px 偏移 0.0px）→ 单点断言会漏掉"退回字体"。自绘的偏移与字体完全无关
     * （24 组矩阵里恒为 −0.5px），所以"两字体一致"这条既准确又不受字号/字体选择影响。
     */
    @Test
    public void boxGeometryDoesNotDependOnFont() {
        double monospaceDrift = verticalLineDriftPx(Typeface.MONOSPACE);
        double serifDrift = verticalLineDriftPx(Typeface.SERIF);

        assertTrue(fmt("monospace 竖线偏离字格中线 %.2fpx", Math.abs(monospaceDrift)),
                Math.abs(monospaceDrift) <= 1.0);
        assertTrue(fmt("serif 竖线偏离字格中线 %.2fpx", Math.abs(serifDrift)),
                Math.abs(serifDrift) <= 1.0);
        assertTrue(fmt("框线几何随字体变了（monospace %.2fpx vs serif %.2fpx）——说明没走自绘",
                monospaceDrift, serifDrift), Math.abs(monospaceDrift - serifDrift) <= 0.5);
    }

    /** 渲染一列竖线，返回墨迹水平质心相对字格中线的偏移（正 = 偏右）。 */
    private static double verticalLineDriftPx(Typeface typeface) {
        Rendered r = render(NO_CURSOR + "│\r\n│\r\n│\r\n│", typeface);
        int bg = background(r.bitmap);
        int x = bestColumn(r.bitmap, bg);
        double centroid = inkCentroidX(r.bitmap, bg, x, 0, r.bitmap.getHeight() - 1);
        return centroid - r.renderer.getFontWidth() * 0.5f;
    }

    /** 指定行区间内、x 附近墨迹的水平质心。 */
    private static double inkCentroidX(Bitmap bitmap, int bg, int centerX, int fromY, int toY) {
        double sum = 0;
        int count = 0;
        for (int y = Math.max(0, fromY); y <= Math.min(bitmap.getHeight() - 1, toY); y++) {
            for (int x = Math.max(0, centerX - 8); x <= Math.min(bitmap.getWidth() - 1, centerX + 8); x++) {
                if (isInk(bitmap.getPixel(x, y), bg)) { sum += x; count++; }
            }
        }
        return count == 0 ? centerX : sum / count;
    }

    /** 普通文字不该被几何绘制误伤。 */
    @Test
    public void normalTextStillRendered() {
        Rendered r = render(NO_CURSOR + "X");
        int bg = background(r.bitmap);
        int ink = 0;
        for (int x = 0; x < r.bitmap.getWidth(); x++)
            for (int y = 0; y < r.bitmap.getHeight(); y++)
                if (isInk(r.bitmap.getPixel(x, y), bg)) ink++;
        assertTrue("普通文字应有墨迹", ink > 10);
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(Locale.US, pattern, args);
    }

    /** 排查用：`DUMP_IMAGES=true bash scripts/test-l3.sh` 时落盘到 App 私有外部目录。 */
    private static void dumpIfRequested(Bitmap bitmap, String content) {
        if (!"true".equals(androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("dumpImages"))) return;
        java.io.File dir = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getExternalFilesDir(null);
        if (dir == null) return;
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(dir, "box-render-" + Math.abs(content.hashCode()) + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Throwable ignored) { }
    }
}
