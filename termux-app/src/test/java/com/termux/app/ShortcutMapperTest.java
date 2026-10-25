package com.termux.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Ctrl+Alt 硬件快捷键映射的单测（不需要真键盘、不需要设备）。
 *
 * <p>覆盖上游 {@code TermuxTerminalViewClient.onKeyDown} 里 Ctrl+Alt 分支的全部组合。
 */
public class ShortcutMapperTest {

    private static ShortcutMapper.Result map(int keyCode, char c) {
        return ShortcutMapper.map(keyCode, c, 0);
    }

    @Test
    public void sessionsWithArrows() {
        assertEquals(ShortcutMapper.Action.NEXT_SESSION, map(ShortcutMapper.KEYCODE_DPAD_DOWN, '\0').action);
        assertEquals(ShortcutMapper.Action.PREV_SESSION, map(ShortcutMapper.KEYCODE_DPAD_UP, '\0').action);
    }

    @Test
    public void sessionsWithLetters() {
        assertEquals(ShortcutMapper.Action.NEXT_SESSION, map(0, 'n').action);
        assertEquals(ShortcutMapper.Action.PREV_SESSION, map(0, 'p').action);
    }

    @Test
    public void drawer() {
        assertEquals(ShortcutMapper.Action.OPEN_DRAWER, map(ShortcutMapper.KEYCODE_DPAD_RIGHT, '\0').action);
        assertEquals(ShortcutMapper.Action.CLOSE_DRAWER, map(ShortcutMapper.KEYCODE_DPAD_LEFT, '\0').action);
    }

    @Test
    public void letters() {
        assertEquals(ShortcutMapper.Action.NEW_SESSION, map(0, 'c').action);
        assertEquals(ShortcutMapper.Action.TOGGLE_KEYBOARD, map(0, 'k').action);
        assertEquals(ShortcutMapper.Action.SHOW_MENU, map(0, 'm').action);
        assertEquals(ShortcutMapper.Action.RENAME_SESSION, map(0, 'r').action);
        assertEquals(ShortcutMapper.Action.SHOW_URLS, map(0, 'u').action);
        assertEquals(ShortcutMapper.Action.PASTE, map(0, 'v').action);
        assertEquals(ShortcutMapper.Action.AUTOFILL, map(0, 'z').action);
        assertEquals(ShortcutMapper.Action.TOGGLE_FULLSCREEN, map(0, 'f').action);
        assertEquals(ShortcutMapper.Action.TOGGLE_TOOLBAR, map(0, 't').action);
    }

    @Test
    public void fontSize() {
        assertEquals(ShortcutMapper.Action.FONT_INCREASE, map(0, '+').action);
        // Shift + '=' 才能出 '+'：unicodeChar 是 '='，shifted 才是 '+'
        assertEquals(ShortcutMapper.Action.FONT_INCREASE,
            ShortcutMapper.map(0, '=', '+').action);
        assertEquals(ShortcutMapper.Action.FONT_DECREASE, map(0, '-').action);
    }

    @Test
    public void jumpToSession() {
        assertEquals(ShortcutMapper.Action.JUMP_SESSION, map(0, '1').action);
        assertEquals(0, map(0, '1').sessionIndex);
        assertEquals(8, map(0, '9').sessionIndex);
    }

    @Test
    public void unrelatedKeysPassThrough() {
        // 未定义的组合必须交回终端，不能被吃掉
        assertEquals(ShortcutMapper.Action.NONE, map(0, 'a').action);
        assertEquals(ShortcutMapper.Action.NONE, map(0, '0').action);
        assertEquals(ShortcutMapper.Action.NONE, map(29 /* KEYCODE_A */, '\0').action);
    }
}
