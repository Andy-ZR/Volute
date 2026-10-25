package com.termux.app;

/**
 * Ctrl+Alt 硬件键盘快捷键的**纯映射逻辑**（2026-10-25 抽出，便于单测）。
 *
 * <p>这段逻辑原本内联在 {@link TermuxTerminalViewClient#onKeyDown} 里，属于上游 Termux 原有功能：
 * 物理键盘按住 Ctrl+Alt 再按某个键，就等于执行对应的界面操作（切会话、开抽屉、新建、改字号…）。
 * 抽成不依赖 Android 类的纯函数后，可以在 JVM 单测里覆盖全部组合，不需要真键盘。
 *
 * <p>注意：软键盘的 extra-keys 发不出 metaState（`TermuxTerminalExtraKeys` 对普通键硬编码
 * false×4），所以这些组合**只可能来自物理/外接键盘**。
 */
public final class ShortcutMapper {

    /** 与 android.view.KeyEvent 的常量一致（此处自带，避免单测依赖 Android 类）。 */
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;

    public enum Action {
        /** 不属于任何快捷键，交回给终端（当普通按键处理）。 */
        NONE,
        NEXT_SESSION,
        PREV_SESSION,
        OPEN_DRAWER,
        CLOSE_DRAWER,
        NEW_SESSION,
        TOGGLE_KEYBOARD,
        SHOW_MENU,
        RENAME_SESSION,
        SHOW_URLS,
        PASTE,
        AUTOFILL,
        FONT_INCREASE,
        FONT_DECREASE,
        TOGGLE_FULLSCREEN,
        TOGGLE_TOOLBAR,
        /** 跳到指定会话，序号见 {@link Result#sessionIndex}（0 基）。 */
        JUMP_SESSION
    }

    /** 映射结果：动作 + （仅 JUMP_SESSION 有意义的）目标会话序号。 */
    public static final class Result {
        public final Action action;
        public final int sessionIndex;

        Result(Action action, int sessionIndex) {
            this.action = action;
            this.sessionIndex = sessionIndex;
        }
    }

    private static final Result NONE_RESULT = new Result(Action.NONE, -1);

    private ShortcutMapper() {
    }

    /**
     * @param keyCode            按键码（见 KEYCODE_* 常量）
     * @param unicodeChar        不带修饰键时的字符（{@code KeyEvent.getUnicodeChar(0)}）
     * @param unicodeCharShifted 带 Shift 时的字符（{@code KeyEvent.getUnicodeChar(META_SHIFT_ON)}）
     */
    public static Result map(int keyCode, int unicodeChar, int unicodeCharShifted) {
        if (keyCode == KEYCODE_DPAD_DOWN || unicodeChar == 'n') return of(Action.NEXT_SESSION);
        if (keyCode == KEYCODE_DPAD_UP || unicodeChar == 'p') return of(Action.PREV_SESSION);
        if (keyCode == KEYCODE_DPAD_RIGHT) return of(Action.OPEN_DRAWER);
        if (keyCode == KEYCODE_DPAD_LEFT) return of(Action.CLOSE_DRAWER);
        if (unicodeChar == 'c') return of(Action.NEW_SESSION);
        if (unicodeChar == 'k') return of(Action.TOGGLE_KEYBOARD);
        if (unicodeChar == 'm') return of(Action.SHOW_MENU);
        if (unicodeChar == 'r') return of(Action.RENAME_SESSION);
        if (unicodeChar == 'u') return of(Action.SHOW_URLS);
        if (unicodeChar == 'v') return of(Action.PASTE);
        if (unicodeChar == 'z') return of(Action.AUTOFILL);
        // Shift 才能打出 '+'，见 upstream termux-api#2
        if (unicodeChar == '+' || unicodeCharShifted == '+') return of(Action.FONT_INCREASE);
        if (unicodeChar == '-') return of(Action.FONT_DECREASE);
        if (unicodeChar == 'f') return of(Action.TOGGLE_FULLSCREEN);
        if (unicodeChar == 't') return of(Action.TOGGLE_TOOLBAR);
        if (unicodeChar >= '1' && unicodeChar <= '9') return new Result(Action.JUMP_SESSION, unicodeChar - '1');
        return NONE_RESULT;
    }

    private static Result of(Action action) {
        return new Result(action, -1);
    }
}
