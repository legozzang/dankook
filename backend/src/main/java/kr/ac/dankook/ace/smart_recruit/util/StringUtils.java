package kr.ac.dankook.ace.smart_recruit.util;

public final class StringUtils {

    private StringUtils() {}

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String normalizeFilter(String value) {
        if (isBlank(value)) return null;
        String trimmed = value.trim();
        return "전체".equals(trimmed) || "all".equals(trimmed) ? null : trimmed;
    }
}
