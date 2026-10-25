package com.termux.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.Gravity;
import android.view.WindowInsets;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * L3 回归的第一批 UI 用例（`connectedDebugAndroidTest`，跑 AVD / 真机）。
 *
 * <p>覆盖这半个月改动最集中、最容易静默回归的三件事：
 * 点终端能不能弹软键盘 · 键盘收起时 BACK 开抽屉（切会话唯一入口）· 抽屉 ⌨ 已删除。
 *
 * <p>交互统一走 {@link UiDevice}（系统级注入），比 Instrumentation 的指针注入稳。
 */
@RunWith(AndroidJUnit4.class)
public class TerminalUiTest {

    private static final long TIMEOUT_MS = 30_000;

    private Activity activity;
    private UiDevice device;

    @Before
    public void setUp() throws Exception {
        var instr = InstrumentationRegistry.getInstrumentation();
        device = UiDevice.getInstance(instr);
        // 首启会弹「允许发送通知」权限框，抢走焦点 → 先授权，否则点击/BACK 全打到弹窗上
        try {
            instr.getUiAutomation().grantRuntimePermission("com.termux", "android.permission.POST_NOTIFICATIONS");
        } catch (Throwable ignored) {
            // API < 33 没有该权限
        }
        ActivityScenario<TermuxActivity> scenario = ActivityScenario.launch(TermuxActivity.class);
        Activity[] holder = new Activity[1];
        scenario.onActivity(a -> holder[0] = a);
        activity = holder[0];
        // 等终端视图挂上（首次启动要解压 bootstrap）
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (activity.findViewById(R.id.terminal_view) != null
                && activity.findViewById(R.id.terminal_view).getWidth() > 0) break;
            Thread.sleep(300);
        }
        device.waitForIdle();
    }

    private boolean imeVisible() {
        WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        return insets != null && insets.isVisible(WindowInsets.Type.ime());
    }

    private boolean waitForIme(boolean expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (imeVisible() == expected) return true;
            Thread.sleep(200);
        }
        return false;
    }

    private void tapTerminalCenter() {
        var view = activity.findViewById(R.id.terminal_view);
        int[] loc = new int[2];
        activity.runOnUiThread(() -> view.getLocationOnScreen(loc));
        device.click(loc[0] + view.getWidth() / 2, loc[1] + view.getHeight() / 2);
    }

    /** 收起键盘后单击终端 → 键盘弹出（2026-10-15 的原始修复）。 */
    @Test
    public void tapTerminalShowsKeyboard() throws Exception {
        // 先确保收起
        activity.runOnUiThread(() -> activity.getSystemService(android.view.inputmethod.InputMethodManager.class)
            .hideSoftInputFromWindow(activity.findViewById(R.id.terminal_view).getWindowToken(), 0));
        assertTrue("前置条件：键盘应收起", waitForIme(false));

        tapTerminalCenter();
        assertTrue("点终端后键盘应弹出", waitForIme(true));
    }

    // 注：曾有一条「键盘已显示时 BACK 先收起键盘」的用例，在 AVD 上不成立（BACK 被
    // App 的 OnBackPressedCallback 消费去开抽屉），真机行为也依赖输入法实现 →
    // 改为人工验证项，不放进自动化矩阵。

    /** 键盘收起时 BACK 打开抽屉（Play 分支语义）。 */
    @Test
    public void backOpensDrawerWhenKeyboardHidden() throws Exception {
        activity.runOnUiThread(() -> activity.getSystemService(android.view.inputmethod.InputMethodManager.class)
            .hideSoftInputFromWindow(activity.findViewById(R.id.terminal_view).getWindowToken(), 0));
        assertTrue(waitForIme(false));

        device.pressBack();
        Thread.sleep(800);
        boolean open = ((TermuxActivity) activity).getDrawer().isDrawerOpen(Gravity.LEFT);
        assertTrue("键盘收起时 BACK 应打开抽屉", open);
    }

    /** 抽屉底部 ⌨ 按钮已删除（0.7.0）。 */
    @Test
    public void drawerHasNoKeyboardButton() {
        int id = activity.getResources().getIdentifier("toggle_keyboard_button", "id", activity.getPackageName());
        assertFalse("⌨ 按钮应已删除（资源 id 不应再存在）", id != 0);
    }
}
