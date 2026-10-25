package com.termux.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 复制/分享时"把被 TUI 硬换行拆断的 URL 接回一行"的纯函数测试。
 *
 * <p>判据：只有**行尾是 URL 且该行写满终端列数（±1）**才与下一行拼接 —— 这样两行独立文本
 * 不会被误粘。对应实现 {@link TermuxUrlUtils#joinSplitUrls(String, int)}。
 */
public class JoinSplitUrlsTest {

    private static final int COLUMNS = 80;

    private static String join(String text) {
        return TermuxUrlUtils.joinSplitUrls(text, COLUMNS);
    }

    /** 构造"写满一整行"的字符串：前缀 + URL 片段，总长正好 columns。 */
    private static String fullLine(String urlPart) {
        return "x".repeat(Math.max(0, COLUMNS - urlPart.length())) + urlPart;
    }

    @Test
    public void singleLineUnchanged() {
        String s = "see https://example.com/a end";
        assertEquals(s, join(s));
    }

    @Test
    public void joinsHardWrappedUrl() {
        String first = fullLine("https://example.com/very/long");
        String merged = join(first + "\n/path?q=1");
        assertEquals(fullLine("https://example.com/very/long") + "/path?q=1", merged);
    }

    @Test
    public void joinsMultipleWraps() {
        String first = fullLine("https://example.com/aaaa");
        String second = "b".repeat(COLUMNS);          // 中间行也写满 → 继续拼
        String merged = join(first + "\n" + second + "\n/c");
        assertEquals(fullLine("https://example.com/aaaa") + second + "/c", merged);
    }

    @Test
    public void doesNotJoinTwoIndependentShortUrls() {
        String s = "see https://a.com\nand https://b.com";
        assertEquals(s, join(s));
    }

    @Test
    public void doesNotJoinWhenLineIsNotFull() {
        String s = "https://a.com\nhello";
        assertEquals(s, join(s));
    }

    @Test
    public void doesNotJoinWhenNextLineStartsWithUrl() {
        String first = fullLine("https://a.com/x");
        String s = first + "\nhttps://b.com/y";
        assertEquals(s, join(s));
    }

    @Test
    public void doesNotJoinWhenNextLineStartsWithWhitespace() {
        String first = fullLine("https://a.com/x");
        String s = first + "\n /continued";
        assertEquals(s, join(s));
    }
}
