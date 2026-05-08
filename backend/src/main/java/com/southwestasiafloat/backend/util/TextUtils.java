package com.southwestasiafloat.backend.util;

/**
 * 文本处理工具类。
 */

public final class TextUtils {

    private TextUtils() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }
}

