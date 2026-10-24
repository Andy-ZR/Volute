package com.termux.app.settings;

import android.util.Log;

import com.termux.app.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 只改 `~/.termux/termux.properties` 里的**一个键**，其余内容（注释、别的键）原样保留。
 *
 * 两条硬约束（都踩过）：
 *   1. **值必须写单行** —— Java Properties 遇到没有行尾 `\` 的多行值会解析失败，且**静默回落默认值**；
 *   2. 写之前先读全文再整体写回，避免把用户其他配置抹掉。
 */
public final class TermuxPropertiesWriter {

    private TermuxPropertiesWriter() {}

    public static File propertiesFile() {
        return new File(TermuxConstants.HOME_PATH, ".termux/termux.properties");
    }

    /** 成功返回 true；失败已记日志，调用方负责提示用户。 */
    public static boolean setProperty(String key, String value) {
        File file = propertiesFile();
        File dir = file.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            Log.e(TermuxConstants.LOG_TAG, "Cannot create directory for " + file);
            return false;
        }

        List<String> lines = new ArrayList<>();
        boolean replaced = false;
        if (file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                boolean skipContinuation = false;
                while ((line = reader.readLine()) != null) {
                    if (skipContinuation) {                      // 上一行是本键的多行续行
                        skipContinuation = line.trim().endsWith("\\");
                        continue;
                    }
                    String trimmed = line.trim();
                    if (isKeyLine(trimmed, key)) {
                        if (!replaced) {
                            lines.add(key + " = " + value);
                            replaced = true;
                        }
                        skipContinuation = trimmed.endsWith("\\");
                        continue;
                    }
                    lines.add(line);
                }
            } catch (IOException e) {
                Log.e(TermuxConstants.LOG_TAG, "Cannot read " + file, e);
                return false;
            }
        }
        if (!replaced) lines.add(key + " = " + value);

        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append('\n');
        File tmp = new File(dir, "termux.properties.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TermuxConstants.LOG_TAG, "Cannot write " + tmp, e);
            return false;
        }
        if (!tmp.renameTo(file)) {
            Log.e(TermuxConstants.LOG_TAG, "Cannot replace " + file);
            return false;
        }
        return true;
    }

    private static boolean isKeyLine(String trimmed, String key) {
        if (!trimmed.startsWith(key)) return false;
        if (trimmed.length() == key.length()) return true;
        char next = trimmed.charAt(key.length());
        return next == '=' || next == ':' || Character.isWhitespace(next);
    }
}
