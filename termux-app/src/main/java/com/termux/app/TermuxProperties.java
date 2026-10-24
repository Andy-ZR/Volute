package com.termux.app;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

public final class TermuxProperties {

    private final Properties properties = new Properties();

    public static final String EXTRA_KEYS_DEFAULT = "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]";
    public static final String EXTRA_KEYS_STYLE_DEFAULT = "default";

    void reloadProperties(TermuxActivity activity) {
        properties.clear();
        try {
            for (String subPath : new String[]{".termux/termux.properties", ".config/termux/termux.properties"}) {
                File propertiesFile = new File(TermuxConstants.HOME_PATH + '/' + subPath);
                if (propertiesFile.isFile()) {
                    try (FileInputStream in = new FileInputStream(propertiesFile)) {
                        try {
                            properties.load(in);
                        } catch (Exception e) {
                            Log.e(TermuxConstants.LOG_TAG, "Error reading termux properties", e);
                            activity.showTransientMessage("Cannot read termux.properties - check syntax", true);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TermuxConstants.LOG_TAG, "Failed to reload properties", e);
        }
    }

    boolean isBackKeyTheEscapeKey() {
        return properties.getProperty("back-key", "back").equalsIgnoreCase("escape");
    }

    boolean isEnforcingCharBasedInput() {
        return properties.getProperty("enforce-char-based-input", "false").equalsIgnoreCase("true");
    }

    boolean areVirtualVolumeKeysDisabled() {
        return properties.getProperty("volume-keys", "normal").equalsIgnoreCase("volume");
    }

    public String getExtraKeys() {
        return properties.getProperty("extra-keys", EXTRA_KEYS_DEFAULT);
    }

    /** 复制/分享时是否把被 TUI 硬换行拆断的 URL 接回一行（默认开）。 */
    public boolean shouldJoinSplitUrls() {
        return !"false".equalsIgnoreCase(properties.getProperty("join-url-on-copy", "true").trim());
    }

    /** 会话数上限；未配置返回 null（由调用方按设备内存自适应）。 */
    public String getMaxSessions() {
        return properties.getProperty("max-sessions");
    }

    /**
     * 切会话时是否弹底部提示（会话标题黑框）。
     * 默认 **关**：切会话只能从抽屉 tab 列表发生（横划/⏮⏭ 已撤），提示是纯噪音；
     * 需要上游行为的用户把它设成 true。
     */
    public boolean shouldShowSessionChangeToast() {
        return properties.getProperty("session-change-toast", "false").equalsIgnoreCase("true");
    }

    public String getExtraKeysStyle() {
        return properties.getProperty("extra-keys-style", EXTRA_KEYS_STYLE_DEFAULT);
    }

    public enum BellBehaviour {
        VIBRATE, BEEP, IGNORE
    }

    public BellBehaviour getBellBehaviour() {
        var prop = properties.getProperty("bell-character", "vibrate").trim().toLowerCase(Locale.ROOT);
        return switch (prop) {
            case "vibrate" -> BellBehaviour.VIBRATE;
            case "beep" -> BellBehaviour.BEEP;
            case "ignore" -> BellBehaviour.IGNORE;
            default -> {
                Log.w(TermuxConstants.LOG_TAG, "Invalid 'bell-character' value: '" + prop + "'");
                yield BellBehaviour.VIBRATE;
            }
        };
    }

}
