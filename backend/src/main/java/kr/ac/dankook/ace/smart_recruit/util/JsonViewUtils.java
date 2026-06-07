package kr.ac.dankook.ace.smart_recruit.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thymeleaf 뷰에 삽입할 인라인 JSON 문자열 생성 유틸리티
 *
 * 동일한 toJson / escapeJson 로직이
 * JobPostingViewController, AdminViewController 에 복붙되어 있던 것을 통합합니다.
 */
public final class JsonViewUtils {

    private JsonViewUtils() {
        // 유틸리티 클래스 — 인스턴스화 방지
    }

    /**
     * {@code Map<String, List<String>>}을 JSON 객체 문자열로 변환합니다.
     *
     * <pre>
     * 예) {"서울": ["강남구", "강동구"], "경기": ["수원시"]}
     * </pre>
     *
     * @param map 시/도 → 시/군/구 목록, 대분류 → 중분류 목록 등
     * @return JSON 문자열 (null-safe, map이 null이면 "{}" 반환)
     */
    public static String toJson(Map<String, List<String>> map) {
        if (map == null || map.isEmpty()) return "{}";

        return map.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":" + toJsonArray(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private static String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";

        return list.stream()
                .map(item -> "\"" + escape(item) + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * JSON 문자열 값에서 이스케이프해야 하는 문자를 처리합니다.
     * \\, ", \r, \n 을 각 JSON 이스케이프 시퀀스로 변환합니다.
     */
    private static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
