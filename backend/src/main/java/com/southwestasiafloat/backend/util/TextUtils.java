package com.southwestasiafloat.backend.util;

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

