package kr.ac.dankook.ace.smart_recruit.config;

/**
 * 애플리케이션 전역 상수 — 단일 진실의 원천(SSOT)
 *
 * 여러 클래스에 흩어져 있던 하드코딩 값을 이곳에서 통합 관리합니다.
 * 좌표를 변경해야 할 때 이 파일만 수정하면 전체에 반영됩니다.
 */
public final class AppConstants {

    // ── 단국대학교 죽전캠퍼스 기준 좌표 ─────────────────────────────────────
    // JS의 JobMapConfig.defaultCenter 값과 반드시 동일해야 합니다.
    public static final double DANKOOK_LATITUDE  = 37.3216;
    public static final double DANKOOK_LONGITUDE = 127.1267;

    // ── 기본 반경 (km) ─────────────────────────────────────────────────────
    public static final double DEFAULT_RADIUS_KM = 3.0;

    private AppConstants() {
        // 유틸리티 클래스 — 인스턴스화 방지
    }
}
