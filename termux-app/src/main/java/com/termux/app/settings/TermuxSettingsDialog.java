package com.termux.app.settings;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;

import com.termux.BuildConfig;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxProperties;
import com.termux.app.extrakeys.ExtraKeysConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置入口（抽屉底部 ⚙ 打开）。**只有本项目的设置**，全部落 `~/.termux/termux.properties`
 * 并立即生效（走 {@link TermuxActivity#reloadSettingsFromProperties()}）。
 *
 * 为什么用弹框而不是 Activity：本仓沿用的是 Play 线上"无设置页"的裁剪版，
 * 加一个轻量弹框即可，不引入新 Activity / 新主题 / 返回栈。
 */
public final class TermuxSettingsDialog {

    public static final String PROP_EXTRA_KEYS = "extra-keys";
    public static final String PROP_SESSION_TOAST = "session-change-toast";

    /** 预设：默认（2 行 × 7） */
    private static final String PRESET_DEFAULT = TermuxProperties.EXTRA_KEYS_DEFAULT;
    /** 预设：紧凑（2 行 × 6） */
    private static final String PRESET_COMPACT =
        "[['ESC','/','-','HOME','END','PGUP'],['TAB','CTRL','ALT','LEFT','RIGHT','PGDN']]";
    /** 预设：单行 8 键 */
    private static final String PRESET_SINGLE =
        "[['ESC','TAB','CTRL','ALT','LEFT','DOWN','UP','RIGHT']]";
    /** 预设：隐藏 */
    private static final String PRESET_NONE = "[]";

    private TermuxSettingsDialog() {}

    // ── 顶层设置 ────────────────────────────────────────────────

    public static void show(TermuxActivity activity) {
        activity.getDrawer().closeDrawers();
        boolean toastOn = activity.mProperties.shouldShowSessionChangeToast();
        String[] items = {
            activity.getString(R.string.settings_style),
            activity.getString(R.string.settings_extra_keys),
            activity.getString(toastOn ? R.string.settings_session_toast_on : R.string.settings_session_toast_off),
            activity.getString(R.string.settings_about),
        };
        new AlertDialog.Builder(activity)
            .setTitle(R.string.settings_title)
            .setItems(items, (dialog, which) -> {
                switch (which) {
                    case 0: activity.openStylingDialog(); break;
                    case 1: showExtraKeysMenu(activity); break;
                    case 2: toggleSessionToast(activity); break;
                    default: showAbout(activity); break;
                }
            })
            .show();
    }

    private static void toggleSessionToast(TermuxActivity activity) {
        boolean newValue = !activity.mProperties.shouldShowSessionChangeToast();
        if (write(activity, PROP_SESSION_TOAST, newValue ? "true" : "false")) show(activity);
    }

    private static void showAbout(TermuxActivity activity) {
        String text = activity.getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            + "\n\n" + activity.getString(R.string.about_text);
        new AlertDialog.Builder(activity)
            .setTitle(R.string.settings_about)
            .setMessage(text)
            .setPositiveButton(R.string.ok, null)
            .show();
    }

    // ── Extra keys ──────────────────────────────────────────────

    private static void showExtraKeysMenu(TermuxActivity activity) {
        List<List<Object>> matrix = parseMatrix(activity.mProperties.getExtraKeys());
        String[] items = {
            activity.getString(R.string.extra_keys_edit_cells),
            activity.getString(R.string.extra_keys_preset_default),
            activity.getString(R.string.extra_keys_preset_compact),
            activity.getString(R.string.extra_keys_preset_single),
            activity.getString(R.string.extra_keys_preset_none),
        };
        new AlertDialog.Builder(activity)
            .setTitle(R.string.settings_extra_keys)
            .setItems(items, (dialog, which) -> {
                switch (which) {
                    case 0: showCellList(activity, matrix); break;
                    case 1: applyMatrix(activity, parseMatrix(PRESET_DEFAULT)); break;
                    case 2: applyMatrix(activity, parseMatrix(PRESET_COMPACT)); break;
                    case 3: applyMatrix(activity, parseMatrix(PRESET_SINGLE)); break;
                    default: applyMatrix(activity, new ArrayList<>()); break;
                }
            })
            .show();
    }

    /** 逐格列表：每格点开选键；底部是"加一格 / 加一行 / 删一行"。 */
    private static void showCellList(TermuxActivity activity, List<List<Object>> matrix) {
        List<String> items = new ArrayList<>();
        List<int[]> positions = new ArrayList<>();
        for (int row = 0; row < matrix.size(); row++) {
            List<Object> cells = matrix.get(row);
            for (int col = 0; col < cells.size(); col++) {
                items.add(activity.getString(R.string.extra_keys_cell, row + 1, col + 1, cellLabel(cells.get(col))));
                positions.add(new int[]{row, col});
            }
        }
        int actionStart = items.size();
        items.add(activity.getString(R.string.extra_keys_add_cell));
        items.add(activity.getString(R.string.extra_keys_add_row));
        items.add(activity.getString(R.string.extra_keys_remove_row));

        new AlertDialog.Builder(activity)
            .setTitle(R.string.settings_extra_keys)
            .setItems(items.toArray(new String[0]), (dialog, which) -> {
                if (which < actionStart) {
                    int[] pos = positions.get(which);
                    showKeyPicker(activity, matrix, pos[0], pos[1]);
                } else if (which == actionStart) {
                    if (matrix.isEmpty()) matrix.add(new ArrayList<>());
                    matrix.get(matrix.size() - 1).add("ESC");
                    applyMatrix(activity, matrix);
                } else if (which == actionStart + 1) {
                    List<Object> row = new ArrayList<>();
                    row.add("ESC");
                    matrix.add(row);
                    applyMatrix(activity, matrix);
                } else {
                    if (!matrix.isEmpty()) matrix.remove(matrix.size() - 1);
                    applyMatrix(activity, matrix);
                }
            })
            .show();
    }

    private static void showKeyPicker(TermuxActivity activity, List<List<Object>> matrix, int row, int col) {
        List<String> choices = keyChoices();
        List<String> items = new ArrayList<>(choices.size() + 2);
        for (String key : choices) items.add(cellLabel(key));
        items.add(activity.getString(R.string.extra_keys_clear));
        items.add(activity.getString(R.string.extra_keys_type_char));

        new AlertDialog.Builder(activity)
            .setTitle(R.string.extra_keys_pick)
            .setItems(items.toArray(new String[0]), (dialog, which) -> {
                if (which < choices.size()) {
                    Object old = matrix.get(row).get(col);
                    matrix.get(row).set(col, keepPopupIfSameKey(old, choices.get(which)));
                    applyMatrix(activity, matrix);
                } else if (which == choices.size()) {
                    matrix.get(row).remove(col);
                    if (matrix.get(row).isEmpty()) matrix.remove(row);
                    applyMatrix(activity, matrix);
                } else {
                    promptCharacter(activity, matrix, row, col);
                }
            })
            .show();
    }

    private static void promptCharacter(TermuxActivity activity, List<List<Object>> matrix, int row, int col) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(activity)
            .setTitle(R.string.extra_keys_type_char)
            .setView(input)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                String text = input.getText().toString();
                if (text.codePointCount(0, text.length()) != 1) {
                    activity.showTransientMessage(activity.getString(R.string.extra_keys_char_invalid), true);
                    return;
                }
                matrix.get(row).set(col, text);
                applyMatrix(activity, matrix);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    // ── 读写 ────────────────────────────────────────────────────

    private static void applyMatrix(TermuxActivity activity, List<List<Object>> matrix) {
        if (write(activity, PROP_EXTRA_KEYS, toJson(matrix))) {
            // 写成功后回到逐格列表，便于连续编辑（reload 后属性已刷新，重新解析一次）
            showCellList(activity, parseMatrix(activity.mProperties.getExtraKeys()));
        }
    }

    private static boolean write(TermuxActivity activity, String key, String value) {
        if (TermuxPropertiesWriter.setProperty(key, value)) {
            activity.reloadSettingsFromProperties();
            activity.showTransientMessage(activity.getString(R.string.settings_saved), false);
            return true;
        }
        activity.showTransientMessage(activity.getString(R.string.settings_write_failed), true);
        return false;
    }

    // ── 解析 / 序列化 ──────────────────────────────────────────

    static List<List<Object>> parseMatrix(String json) {
        List<List<Object>> matrix = new ArrayList<>();
        JSONArray rows;
        try {
            rows = new JSONArray(json);
        } catch (JSONException e) {
            try {
                rows = new JSONArray(TermuxProperties.EXTRA_KEYS_DEFAULT);
            } catch (JSONException ignored) {
                return matrix;
            }
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i);
            List<Object> cells = new ArrayList<>();
            if (row != null) {
                for (int j = 0; j < row.length(); j++) {
                    Object cell = row.opt(j);
                    if (cell != null) cells.add(cell);
                }
            }
            matrix.add(cells);
        }
        return matrix;
    }

    private static String toJson(List<List<Object>> matrix) {
        JSONArray rows = new JSONArray();
        for (List<Object> cells : matrix) {
            JSONArray row = new JSONArray();
            for (Object cell : cells) {
                if (cell instanceof JSONObject) row.put(cell);
                else row.put(String.valueOf(cell));
            }
            rows.put(row);
        }
        return rows.toString();
    }

    private static Object keepPopupIfSameKey(Object old, String newKey) {
        if (old instanceof JSONObject) {
            JSONObject object = (JSONObject) old;
            String oldKey = object.optString("key", object.optString("macro", object.optString("display", "")));
            if (newKey.equals(oldKey)) return object;
        }
        return newKey;
    }

    // ── 显示 ────────────────────────────────────────────────────

    private static String cellLabel(Object cell) {
        if (cell instanceof JSONObject) {
            JSONObject object = (JSONObject) cell;
            String key = object.optString("key", object.optString("macro", object.optString("display", "?")));
            String display = object.optString("display", "");
            return display.isEmpty() ? labelFor(key) : display;
        }
        return labelFor(String.valueOf(cell));
    }

    private static String labelFor(String key) {
        String glyph = ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.get(key);
        return glyph == null ? key : key + " " + glyph;
    }

    /** 候选键：与 {@link ExtraKeysConstants} 现存清单一致（不含已删的 PREV/NEXT_SESSION）。 */
    private static List<String> keyChoices() {
        String[] order = {
            "ESC", "TAB", "CTRL", "ALT", "SHIFT", "FN",
            "HOME", "END", "PGUP", "PGDN", "INS", "DEL", "BKSP", "ENTER", "SPACE",
            "UP", "LEFT", "DOWN", "RIGHT",
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
            "KEYBOARD", "DRAWER", "PASTE", "SCROLL",
        };
        List<String> out = new ArrayList<>(order.length);
        for (String key : order) {
            if (ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.containsKey(key)
                || "CTRL".equals(key) || "ALT".equals(key) || "SHIFT".equals(key) || "FN".equals(key)
                || "KEYBOARD".equals(key) || "DRAWER".equals(key) || "PASTE".equals(key) || "SCROLL".equals(key)) {
                out.add(key);
            }
        }
        return out;
    }
}
