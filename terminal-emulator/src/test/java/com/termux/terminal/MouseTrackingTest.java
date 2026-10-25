package com.termux.terminal;

/**
 * 鼠标上报（DECSET 1000/1002/1003/1006）的状态机测试。
 *
 * <p>为什么测这个：`TerminalViewClient.onSingleTapUp` 的唤键盘判据直接读
 * {@link TerminalEmulator#isMouseTrackingActive()}；opencode / tmux 都会把这些序列发给终端。
 * 这层一旦被改坏（比如新增的 DECSET 处理清了别的 bit），键盘行为会静默回归。
 *
 * <p>已知实现细节（有意固化，别当 bug 改）：
 * <ul>
 *   <li>只认 1000 / 1002；<b>1003（any-event）不识别</b>；</li>
 *   <li>1000 与 1002 <b>互斥</b>：设一个会清另一个；</li>
 *   <li>1006 只是协议格式（SGR），本身不开启上报。</li>
 * </ul>
 */
public class MouseTrackingTest extends TerminalTestCase {

    private static final String ENABLE_1000 = "\033[?1000h";
    private static final String DISABLE_1000 = "\033[?1000l";
    private static final String ENABLE_1002 = "\033[?1002h";
    private static final String DISABLE_1002 = "\033[?1002l";
    private static final String ENABLE_1003 = "\033[?1003h";
    private static final String ENABLE_1006 = "\033[?1006h";

    public void testDisabledByDefault() {
        withTerminalSized(10, 5);
        assertFalse(mTerminal.isMouseTrackingActive());
    }

    public void testEnable1000() {
        withTerminalSized(10, 5);
        enterString(ENABLE_1000);
        assertTrue(mTerminal.isMouseTrackingActive());
        enterString(DISABLE_1000);
        assertFalse(mTerminal.isMouseTrackingActive());
    }

    public void testEnable1002() {
        withTerminalSized(10, 5);
        enterString(ENABLE_1002);
        assertTrue(mTerminal.isMouseTrackingActive());
        enterString(DISABLE_1002);
        assertFalse(mTerminal.isMouseTrackingActive());
    }

    /** 1000 与 1002 互斥：开 1002 会清掉 1000，关 1000 不会连带关掉 1002。 */
    public void test1000And1002AreMutuallyExclusive() {
        withTerminalSized(10, 5);
        enterString(ENABLE_1000);
        assertTrue(mTerminal.isMouseTrackingActive());
        enterString(ENABLE_1002);
        assertTrue(mTerminal.isMouseTrackingActive());
        enterString(DISABLE_1000);           // 1000 已被清，关它不影响 1002
        assertTrue(mTerminal.isMouseTrackingActive());
        enterString(DISABLE_1002);
        assertFalse(mTerminal.isMouseTrackingActive());
    }

    /** 1003（any-event）当前不识别 —— 若将来支持，这个用例要一起改。 */
    public void test1003IsNotTracked() {
        withTerminalSized(10, 5);
        enterString(ENABLE_1003);
        assertFalse("1003 不在 isMouseTrackingActive() 的判据里", mTerminal.isMouseTrackingActive());
    }

    /** 1006 只是 SGR 协议开关，单独开它不算"开启上报"。 */
    public void test1006AloneDoesNotEnableTracking() {
        withTerminalSized(10, 5);
        enterString(ENABLE_1006);
        assertFalse(mTerminal.isMouseTrackingActive());
    }

    /** tmux `mouse on` 实际发的组合：1000h + 1002h + 1006h。 */
    public void testTmuxLikeSequence() {
        withTerminalSized(10, 5);
        enterString(ENABLE_1000 + ENABLE_1002 + ENABLE_1006);
        assertTrue(mTerminal.isMouseTrackingActive());
        // tmux detach 时的清理序列
        enterString(DISABLE_1000 + DISABLE_1002);
        assertFalse(mTerminal.isMouseTrackingActive());
    }
}
