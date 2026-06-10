/**
 * auth-utils.js — 인증·공통 유틸리티 함수 모음
 *
 * getToken / authHeaders / escapeHtml 이 mypage.html, edit-profile.html,
 * admin/*.html, job-map.js 등 여러 파일에 중복 정의되어 있던 것을 통합합니다.
 *
 * 로드 순서: 이 파일을 job-map.js 보다 먼저 <script> 태그로 참조해야 합니다.
 */

/**
 * 쿠키 → localStorage 순으로 accessToken을 조회합니다.
 * @returns {string} 토큰 문자열 (없으면 빈 문자열)
 */
function getToken() {
    const cookieMatch = document.cookie.match(/(?:^|;\s*)accessToken=([^;]+)/);
    const token = cookieMatch ? cookieMatch[1] : (localStorage.getItem("accessToken") || "");
    if (!token) return "";

    try {
        const payloadPart = token.split(".")[1];
        if (!payloadPart) return "";

        const base64 = payloadPart.replace(/-/g, "+").replace(/_/g, "/");
        const padded = base64.padEnd(base64.length + ((4 - base64.length % 4) % 4), "=");
        const payload = JSON.parse(atob(padded));
        if (payload.exp && Math.floor(Date.now() / 1000) > payload.exp) {
            localStorage.removeItem("accessToken");
            localStorage.removeItem("memberId");
            document.cookie = "accessToken=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; SameSite=Strict";
            return "";
        }
    } catch (e) {
        return "";
    }

    return token;
}

/**
 * JSON 요청에 필요한 기본 헤더 객체를 반환합니다.
 * 토큰이 있으면 Authorization 헤더를 포함합니다.
 * @returns {Record<string, string>}
 */
function authHeaders() {
    const headers = { "Content-Type": "application/json" };
    const token = getToken();
    if (token) headers["Authorization"] = "Bearer " + token;
    return headers;
}

/**
 * HTML 특수문자를 이스케이프합니다.
 * innerHTML에 사용자 데이터를 삽입할 때 XSS를 방지합니다.
 * @param {*} value
 * @returns {string}
 */
function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    }[c]));
}
