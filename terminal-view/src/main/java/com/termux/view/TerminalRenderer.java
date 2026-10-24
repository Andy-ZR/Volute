package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Typeface;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {

    final int mTextSize;
    final Typeface mTypeface;
    private final Paint mTextPaint = new Paint();
    /** 框线/块元素专用画笔（不碰 mTextPaint 的 style/color，避免污染后续文字绘制） */
    private final Paint mBoxPaint = new Paint();
    /** Powerline 私用区 U+E0B0–U+E0B3：字体自带字形就不接管（避免覆盖用户字体设计） */
    private final boolean[] mPowerlineGlyphPresent = new boolean[4];

    /** The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'. */
    final float mFontWidth;
    /** The {@link Paint#getFontSpacing()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    final int mFontLineSpacing;
    /** The {@link Paint#ascent()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    private final int mFontAscent;
    /** The {@link #mFontLineSpacing} + {@link #mFontAscent}. */
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];

    public TerminalRenderer(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);

        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        for (int i = 0; i < 4; i++)
            mPowerlineGlyphPresent[i] = mTextPaint.hasGlyph(String.valueOf((char) (0xE0B0 + i)));

        mFontWidth = mTextPaint.measureText("X");

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    /** Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection. */
    public void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                             int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.isCursorEnabled();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;

            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            boolean lastRunIsBox = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighSurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighSurrogate ? 2 : 1;
                final int codePoint = charIsHighSurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);
                final boolean isBox = isGeometryCodePoint(codePoint);

                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaint.measureText(line,
                    currentCharIndex, charsForCodePoint);
                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;

                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch || isBox != lastRunIsBox) {
                    if (column == 0) {
                        // Skip first column as there is nothing to draw, just record the current style.
                    } else {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                            lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection, lastRunIsBox);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                    lastRunIsBox = isBox;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection, lastRunIsBox);
        }
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns,
                             int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle,
                             long textStyle, boolean reverseVideo, boolean boxRun) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            // Let bold have bright colors if applicable (one of the first 8):
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;

        mes = mes / mFontWidth;
        boolean savedMatrix = false;
        if (!boxRun && Math.abs(mes - runWidthColumns) > 0.01) {   // 几何段按字格精确绘制 → 不做宽度缩放
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right -= ((right - left) * 3) / 4.;
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);

            if (boxRun) {
                drawBoxRun(canvas, text, startCharIndex, runWidthChars, startColumn, y, foreColor, bold);
            } else {
                // The text alignment is the default Paint.Align.LEFT.
                canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, false, mTextPaint);
            }
        }

        if (savedMatrix) canvas.restore();
    }

    // ── 框线 / 块元素自绘 ────────────────────────────────────────────────
    // 为什么：字体里的框线字形往往填不满字格 → 相邻行之间留缝（竖线尤其明显）；
    // 桌面终端普遍不查字体、直接按字格画线。这里只接管「语义确定」的码位
    // （框线 U+2500–257F、块元素 U+2580–259F），其余（Powerline 私用区 U+E0A0+、
    // Nerd 图标、Braille 等，语义由字体决定）仍交给字体。

    private static final int BOX_U = 1, BOX_R = 2, BOX_D = 4, BOX_L = 8;

    /** 是否由我们自绘几何。 */
    static boolean isBoxDrawingCodePoint(int cp) {
        return boxMask(cp) >= 0 || (cp >= 0x2580 && cp <= 0x259F);
    }

    /** 从格中心朝哪些方向伸线；-1 = 不接管（交给字体）。 */
    private static int boxMask(int cp) {
        switch (cp) {
            // 单线：直 / 弯角 / 三通 / 十字
            case 0x2500: return BOX_L | BOX_R;
            case 0x2502: return BOX_U | BOX_D;
            case 0x250C: return BOX_R | BOX_D;
            case 0x2510: return BOX_L | BOX_D;
            case 0x2514: return BOX_R | BOX_U;
            case 0x2518: return BOX_L | BOX_U;
            case 0x251C: return BOX_U | BOX_R | BOX_D;
            case 0x2524: return BOX_U | BOX_L | BOX_D;
            case 0x252C: return BOX_L | BOX_R | BOX_D;
            case 0x2534: return BOX_L | BOX_R | BOX_U;
            case 0x253C: return BOX_U | BOX_R | BOX_D | BOX_L;
            // 粗线
            case 0x2501: return BOX_L | BOX_R;
            case 0x2503: return BOX_U | BOX_D;
            case 0x250F: return BOX_R | BOX_D;
            case 0x2513: return BOX_L | BOX_D;
            case 0x2517: return BOX_R | BOX_U;
            case 0x251B: return BOX_L | BOX_U;
            case 0x2523: return BOX_U | BOX_R | BOX_D;
            case 0x252B: return BOX_U | BOX_L | BOX_D;
            case 0x2533: return BOX_L | BOX_R | BOX_D;
            case 0x253B: return BOX_L | BOX_R | BOX_U;
            case 0x254B: return BOX_U | BOX_R | BOX_D | BOX_L;
            // 双线
            case 0x2550: return BOX_L | BOX_R;
            case 0x2551: return BOX_U | BOX_D;
            case 0x2554: return BOX_R | BOX_D;
            case 0x2557: return BOX_L | BOX_D;
            case 0x255A: return BOX_R | BOX_U;
            case 0x255D: return BOX_L | BOX_U;
            case 0x2560: return BOX_U | BOX_R | BOX_D;
            case 0x2563: return BOX_U | BOX_L | BOX_D;
            case 0x2566: return BOX_L | BOX_R | BOX_D;
            case 0x2569: return BOX_L | BOX_R | BOX_U;
            case 0x256C: return BOX_U | BOX_R | BOX_D | BOX_L;
            // 圆角
            case 0x256D: return BOX_R | BOX_D;
            case 0x256E: return BOX_L | BOX_D;
            case 0x256F: return BOX_L | BOX_U;
            case 0x2570: return BOX_R | BOX_U;
            // 对角
            case 0x2571: case 0x2572: case 0x2573: return 0;
            default: return -1;
        }
    }

    private static boolean isDoubleBox(int cp) {
        return cp >= 0x2550 && cp <= 0x256C;
    }

    private static boolean isHeavyBox(int cp) {
        switch (cp) {
            case 0x2501: case 0x2503: case 0x250F: case 0x2513: case 0x2517: case 0x251B:
            case 0x2523: case 0x252B: case 0x2533: case 0x253B: case 0x254B:
                return true;
            default:
                return false;
        }
    }

    private static boolean isArcCorner(int cp) {
        return cp >= 0x256D && cp <= 0x2570;
    }

    /** 是否由我们自绘几何字符（框线/块元素总是；Powerline 仅当字体缺字形）。 */
    private boolean isGeometryCodePoint(int cp) {
        if (isBoxDrawingCodePoint(cp)) return true;
        if (cp >= 0xE0B0 && cp <= 0xE0B3) return !mPowerlineGlyphPresent[cp - 0xE0B0];
        return false;
    }

    /** 一段连续的框线/块元素：按字格精确画几何（不叠加宽度缩放）。 */
    private void drawBoxRun(Canvas canvas, char[] text, int startCharIndex, int runWidthChars,
                            int startColumn, float y, int color, boolean bold) {
        final float cellW = mFontWidth;
        final float top = y - mFontLineSpacingAndAscent + mFontAscent;
        final float bottom = y;
        final float lineW = Math.max(1.5f, cellW * 0.09f) * (bold ? 1.4f : 1f);

        mBoxPaint.setAntiAlias(true);
        mBoxPaint.setColor(color);
        mBoxPaint.setStrokeWidth(lineW);
        mBoxPaint.setStrokeCap(Paint.Cap.BUTT);
        mBoxPaint.setStyle(Paint.Style.STROKE);

        for (int n = 0; n < runWidthChars; n++) {
            final int cp = text[startCharIndex + n];
            final float l = (startColumn + n) * cellW;
            final float r = l + cellW;
            final float cx = l + cellW / 2f;
            final float cy = top + (bottom - top) / 2f;

            if (cp >= 0x2580 && cp <= 0x259F) {
                mBoxPaint.setStyle(Paint.Style.FILL);
                drawBlock(canvas, cp, l, top, r, bottom, color);
                mBoxPaint.setStyle(Paint.Style.STROKE);
                continue;
            }

            mBoxPaint.setColor(color);   // 防上一格灰度块（░▒▓）的 alpha 泄漏到线条

            if (cp >= 0xE0B0 && cp <= 0xE0B3) {                  // Powerline 分隔符
                drawPowerline(canvas, cp, l, top, r, bottom, color);
                continue;
            }

            if (cp == 0x2571 || cp == 0x2572 || cp == 0x2573) {  // ╱ ╲ ╳
                mBoxPaint.setStrokeWidth(lineW);
                if (cp != 0x2572) canvas.drawLine(l, bottom, r, top, mBoxPaint);
                if (cp != 0x2571) canvas.drawLine(l, top, r, bottom, mBoxPaint);
                continue;
            }

            final int mask = boxMask(cp);
            if (mask < 0) continue;

            if (isArcCorner(cp)) {
                mBoxPaint.setStrokeWidth(lineW);
                drawArcCorner(canvas, mask, l, top, r, bottom, cx, cy);
                continue;
            }

            if (isDoubleBox(cp)) {
                drawDoubleMask(canvas, mask, l, top, r, bottom, cx, cy, lineW);
                continue;
            }

            mBoxPaint.setStrokeWidth(isHeavyBox(cp) ? lineW * 1.7f : lineW);
            if ((mask & BOX_L) != 0) canvas.drawLine(cx, cy, l, cy, mBoxPaint);
            if ((mask & BOX_R) != 0) canvas.drawLine(cx, cy, r, cy, mBoxPaint);
            if ((mask & BOX_U) != 0) canvas.drawLine(cx, cy, cx, top, mBoxPaint);
            if ((mask & BOX_D) != 0) canvas.drawLine(cx, cy, cx, bottom, mBoxPaint);
        }
    }

    /** 双线：两条平行细线（间距约 1.1 个线宽）。 */
    private void drawDoubleMask(Canvas canvas, int mask, float l, float top, float r, float bottom,
                                float cx, float cy, float lineW) {
        final float off = lineW * 1.1f;
        mBoxPaint.setStrokeWidth(Math.max(1.f, lineW * 0.75f));
        if ((mask & BOX_L) != 0 || (mask & BOX_R) != 0) {
            final float x0 = (mask & BOX_L) != 0 ? l : cx;
            final float x1 = (mask & BOX_R) != 0 ? r : cx;
            canvas.drawLine(x0, cy - off, x1, cy - off, mBoxPaint);
            canvas.drawLine(x0, cy + off, x1, cy + off, mBoxPaint);
        }
        if ((mask & BOX_U) != 0 || (mask & BOX_D) != 0) {
            final float y0 = (mask & BOX_U) != 0 ? top : cy;
            final float y1 = (mask & BOX_D) != 0 ? bottom : cy;
            canvas.drawLine(cx - off, y0, cx - off, y1, mBoxPaint);
            canvas.drawLine(cx + off, y0, cx + off, y1, mBoxPaint);
        }
    }

    /** 圆角角落：一小段圆弧 + 接到格边的直线段。 */
    private void drawArcCorner(Canvas canvas, int mask, float l, float top, float r, float bottom,
                               float cx, float cy) {
        final float rad = Math.min((r - l), (bottom - top)) * 0.42f;
        if ((mask & BOX_R) != 0 && (mask & BOX_D) != 0) {          // ╭
            canvas.drawLine(cx + rad, cy, r, cy, mBoxPaint);
            canvas.drawLine(cx, cy + rad, cx, bottom, mBoxPaint);
            canvas.drawArc(cx, cy, cx + 2 * rad, cy + 2 * rad, 180f, 90f, false, mBoxPaint);
        } else if ((mask & BOX_L) != 0 && (mask & BOX_D) != 0) {   // ╮
            canvas.drawLine(l, cy, cx - rad, cy, mBoxPaint);
            canvas.drawLine(cx, cy + rad, cx, bottom, mBoxPaint);
            canvas.drawArc(cx - 2 * rad, cy, cx, cy + 2 * rad, 270f, 90f, false, mBoxPaint);
        } else if ((mask & BOX_L) != 0 && (mask & BOX_U) != 0) {   // ╯
            canvas.drawLine(l, cy, cx - rad, cy, mBoxPaint);
            canvas.drawLine(cx, cy - rad, cx, top, mBoxPaint);
            canvas.drawArc(cx - 2 * rad, cy - 2 * rad, cx, cy, 0f, 90f, false, mBoxPaint);
        } else {                                                   // ╰
            canvas.drawLine(cx + rad, cy, r, cy, mBoxPaint);
            canvas.drawLine(cx, cy - rad, cx, top, mBoxPaint);
            canvas.drawArc(cx, cy - 2 * rad, cx + 2 * rad, cy, 90f, 90f, false, mBoxPaint);
        }
    }

    /** Powerline 分隔符（满格三角形 / 细人字形），颜色取该段前景色。 */
    private void drawPowerline(Canvas canvas, int cp, float l, float top, float r, float bottom, int color) {
        final float midY = (top + bottom) / 2f;
        mBoxPaint.setColor(color);
        if (cp == 0xE0B0 || cp == 0xE0B2) {
            final boolean right = cp == 0xE0B0;
            final Path p = new Path();
            p.moveTo(right ? l : r, top);
            p.lineTo(right ? r : l, midY);
            p.lineTo(right ? l : r, bottom);
            p.close();
            mBoxPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(p, mBoxPaint);
            mBoxPaint.setStyle(Paint.Style.STROKE);
        } else {
            final boolean right = cp == 0xE0B1;
            mBoxPaint.setStrokeWidth(Math.max(1.5f, mFontWidth * 0.12f));
            canvas.drawLine(right ? l : r, top, right ? r : l, midY, mBoxPaint);
            canvas.drawLine(right ? l : r, bottom, right ? r : l, midY, mBoxPaint);
        }
    }

    /** 块元素：整块 / 分数块 / 象限块 / 灰度块。 */
    private void drawBlock(Canvas canvas, int cp, float l, float top, float r, float bottom, int color) {
        final float w = r - l, h = bottom - top;
        switch (cp) {
            case 0x2580: fillBox(canvas, l, top, r, top + h / 2f, color); break;             // ▀
            case 0x2581: fillBox(canvas, l, top + h * 7 / 8f, r, bottom, color); break;      // ▁
            case 0x2582: fillBox(canvas, l, top + h * 3 / 4f, r, bottom, color); break;      // ▂
            case 0x2583: fillBox(canvas, l, top + h * 5 / 8f, r, bottom, color); break;      // ▃
            case 0x2584: fillBox(canvas, l, top + h / 2f, r, bottom, color); break;          // ▄
            case 0x2585: fillBox(canvas, l, top + h * 3 / 8f, r, bottom, color); break;      // ▅
            case 0x2586: fillBox(canvas, l, top + h / 4f, r, bottom, color); break;          // ▆
            case 0x2587: fillBox(canvas, l, top + h / 8f, r, bottom, color); break;          // ▇
            case 0x2588: fillBox(canvas, l, top, r, bottom, color); break;                   // █
            case 0x2589: fillBox(canvas, l, top, l + w * 7 / 8f, bottom, color); break;      // ▉
            case 0x258A: fillBox(canvas, l, top, l + w * 3 / 4f, bottom, color); break;      // ▊
            case 0x258B: fillBox(canvas, l, top, l + w * 5 / 8f, bottom, color); break;      // ▋
            case 0x258C: fillBox(canvas, l, top, l + w / 2f, bottom, color); break;          // ▌
            case 0x258D: fillBox(canvas, l, top, l + w * 3 / 8f, bottom, color); break;      // ▍
            case 0x258E: fillBox(canvas, l, top, l + w / 4f, bottom, color); break;          // ▎
            case 0x258F: fillBox(canvas, l, top, l + w / 8f, bottom, color); break;          // ▏
            case 0x2590: fillBox(canvas, l + w / 2f, top, r, bottom, color); break;          // ▐
            case 0x2591: fillBox(canvas, l, top, r, bottom, withAlpha(color, 0x33)); break;  // ░
            case 0x2592: fillBox(canvas, l, top, r, bottom, withAlpha(color, 0x66)); break;  // ▒
            case 0x2593: fillBox(canvas, l, top, r, bottom, withAlpha(color, 0x99)); break;  // ▓
            case 0x2594: fillBox(canvas, l, top, r, top + h / 8f, color); break;             // ▔
            case 0x2595: fillBox(canvas, l + w * 7 / 8f, top, r, bottom, color); break;      // ▕
            case 0x2596: fillBox(canvas, l, top + h / 2f, l + w / 2f, bottom, color); break; // ▖
            case 0x2597: fillBox(canvas, l + w / 2f, top + h / 2f, r, bottom, color); break; // ▗
            case 0x2598: fillBox(canvas, l, top, l + w / 2f, top + h / 2f, color); break;    // ▘
            case 0x2599:                                                                     // ▙
                fillBox(canvas, l, top, l + w / 2f, bottom, color);
                fillBox(canvas, l + w / 2f, top + h / 2f, r, bottom, color);
                break;
            case 0x259A:                                                                     // ▚
                fillBox(canvas, l, top, l + w / 2f, top + h / 2f, color);
                fillBox(canvas, l + w / 2f, top + h / 2f, r, bottom, color);
                break;
            case 0x259B:                                                                     // ▛
                fillBox(canvas, l, top, r, top + h / 2f, color);
                fillBox(canvas, l, top + h / 2f, l + w / 2f, bottom, color);
                break;
            case 0x259C:                                                                     // ▜
                fillBox(canvas, l, top, r, top + h / 2f, color);
                fillBox(canvas, l + w / 2f, top + h / 2f, r, bottom, color);
                break;
            case 0x259D: fillBox(canvas, l + w / 2f, top, r, top + h / 2f, color); break;    // ▝
            case 0x259E:                                                                     // ▞
                fillBox(canvas, l + w / 2f, top, r, top + h / 2f, color);
                fillBox(canvas, l, top + h / 2f, l + w / 2f, bottom, color);
                break;
            case 0x259F:                                                                     // ▟
                fillBox(canvas, l + w / 2f, top, r, bottom, color);
                fillBox(canvas, l, top + h / 2f, l + w / 2f, bottom, color);
                break;
            default: break;
        }
    }

    private void fillBox(Canvas canvas, float l, float top, float r, float bottom, int color) {
        if (r <= l || bottom <= top) return;
        mBoxPaint.setColor(color);
        canvas.drawRect(l, top, r, bottom, mBoxPaint);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}
