/* ═══════════════════════════════════════════════════════════════════════════
   인증 네비게이션
   getToken() / authHeaders() → auth-utils.js 전역 함수 사용
═══════════════════════════════════════════════════════════════════════════ */
function updateAuthNav() {
        const token = getToken(); // auth-utils.js
        document.getElementById("guestMenu").style.display = token ? "none" : "";
        document.getElementById("userMenu").style.display = token ? "flex" : "none";
        // 추천 탭은 로그인 상태에서만 활성화
        const recommendationsTab = document.getElementById("recommendationsTab");
        if (recommendationsTab) {
            recommendationsTab.disabled = !token;
            recommendationsTab.style.opacity = token ? "1" : "0.45";
            recommendationsTab.title = token ? "" : "로그인이 필요합니다";
        }
    }

    function initAuthUI() {
        updateAuthNav();
    }

    function handleLogout() {
        if (confirm("로그아웃 하시겠습니까?")) {
            localStorage.removeItem("accessToken");
            localStorage.removeItem("memberId");
            document.cookie = "accessToken=; path=/; max-age=0";
            location.href = "/auth/login";
        }
    }

/* ═══════════════════════════════════════════════════════════════════════════
   [내부 헬퍼] 회원 선호 정보를 userPreferences에 파싱
   - camelCase / snake_case 양쪽 방어 (?? 연산자)
═══════════════════════════════════════════════════════════════════════════ */
    async function _fetchAndSetPreferences(token) {
        const response = await fetch("/auth/members/me", {
            headers: {"Authorization": "Bearer " + token}
        });
        if (!response.ok) throw new Error("프로필 조회 실패: " + response.status);

            const profile = await response.json();
            const desiredSido = profile.desiredRegionSido ?? profile.desired_region_sido;
            const desiredSigungu = profile.desiredRegionSigungu ?? profile.desired_region_sigungu;
            const desiredRegion2Sido = profile.desiredRegion2Sido ?? profile.desired_region2_sido;
            const desiredRegion2Sigungu = profile.desiredRegion2Sigungu ?? profile.desired_region2_sigungu;
            const desiredRegion3Sido = profile.desiredRegion3Sido ?? profile.desired_region3_sido;
            const desiredRegion3Sigungu = profile.desiredRegion3Sigungu ?? profile.desired_region3_sigungu;
            const preferredMajor = profile.preferredJobTypeMajor ?? profile.preferred_job_type_major;
            const preferredMid = profile.preferredJobTypeMid ?? profile.preferred_job_type_mid;
            const preferredPayType = profile.preferredPayType ?? profile.preferred_pay_type;
            const minPayAmount    = profile.minPayAmount    ?? profile.min_pay_amount;
            const homeLat = profile.homeLatitude  ?? profile.home_latitude;
            const homeLng = profile.homeLongitude ?? profile.home_longitude;

            userPreferences = {
                regions: [
                    {sido: desiredSido || "", sigungu: desiredSigungu || ""},
                    {sido: desiredRegion2Sido || "", sigungu: desiredRegion2Sigungu || ""},
                    {sido: desiredRegion3Sido || "", sigungu: desiredRegion3Sigungu || ""}
                ],
                jobMajor:     preferredMajor    || "",
                jobMid:       preferredMid      || "",
                payType:      preferredPayType  || "",
                minPayAmount: Number(minPayAmount) > 0 ? Number(minPayAmount) : 0,
                homeLat:      (homeLat != null && Number.isFinite(Number(homeLat))) ? Number(homeLat) : null,
                homeLng:      (homeLng != null && Number.isFinite(Number(homeLng))) ? Number(homeLng) : null
            };
    }

/* ═══════════════════════════════════════════════════════════════════════════
   ★ 3-시나리오 초기화 함수
   경쟁 상태(Race Condition) 방지: 통합 진입점에서 await으로 직렬 호출됨
═══════════════════════════════════════════════════════════════════════════ */

    /**
     * [시나리오 1] 마이페이지 '자세히 보기' → focusId 강조 표시 (최고 우선순위)
     * - 반경 필터 강제 해제(all): focusId 공고가 3km 밖이어도 숨겨지지 않음
     * - applyFilters()가 해당 카드만 표시하고 지도 마커에 핀포인트·팝업 적용
     */
    async function initScenarioFocus(focusId) {
        setRadius("all");
        updateRadiusCircle();
        // 컨트롤러가 해당 카드를 HTML에 이미 포함했으므로 API 재호출 없이 DOM 필터링
        renderMarkers();
        applyFilters(); // 내부에서 URL의 focusId를 읽어 해당 카드만 표시 + 마커 포커싱
    }

    /**
     * [시나리오 2] 로그인 회원 → 선호 지역·직종 기반 초기 필터
     * - 선호 정보 완전 조회(await) 후 필터 적용 → 화면 덮어쓰기 방지
     * - 선호 설정 없는 회원은 시나리오 3(단국대 기본)으로 폴백
     * - 반경 제한은 'all'로 열어줌 (선호 지역이 단국대 외곽일 수 있음)
     */
    async function initScenarioLoggedIn(token) {
        try {
            await _fetchAndSetPreferences(token);

            const hasHomeLocation = userPreferences.homeLat != null && userPreferences.homeLng != null;
            const hasRegionPref   = Boolean(userPreferences.regions?.[0]?.sido);
            const hasJobPref      = Boolean(userPreferences.jobMajor);

            if (hasHomeLocation) {
                // [우선] 거주지 좌표 기준 반경 필터 — 내 위치 중심 3km
                initialResultsLoaded = true;
                setHomeCenter(userPreferences.homeLat, userPreferences.homeLng);
                applyInitialPreferenceFilters(false); // 지역은 반경으로 대체, 직종·급여만 반영
                await runSearch();
            } else if (hasRegionPref || hasJobPref) {
                // 거주지 좌표가 없으면 기존 시/군/구 이름 기반 필터
                setRadius("all");
                updateRadiusCircle();
                applyInitialPreferenceFilters();
                await runSearch();
            } else {
                // 선호 설정 없는 로그인 회원 → 단국대 기본 필터로 폴백
                await initScenarioCampus();
            }
        } catch (e) {
            console.warn("[시나리오2] 선호 정보 조회 실패, 단국대 기본 필터 적용:", e);
            await initScenarioCampus();
        }
    }

    /**
     * 거주지 좌표를 지도 검색 중심(customCenter)으로 설정하고 반경 3km로 맞춘다.
     * 지도 클릭 핸들러와 동일하게 📍 마커·반경 원·중심 초기화 버튼을 표시한다.
     */
    function setHomeCenter(lat, lng) {
        customCenter = {lat, lng};
        if (map) {
            if (customMarker) customMarker.remove();
            customMarker = L.marker([lat, lng], {
                icon: L.divIcon({className: "custom-center-icon", html: "📍", iconSize: [24, 24]})
            }).addTo(map);
            map.setView([lat, lng], 14);
        }
        if (resetCenterButton) resetCenterButton.style.display = "";
        setRadius(DEFAULT_INITIAL_RADIUS); // "3"km
        updateRadiusCircle();
    }

    /**
     * [시나리오 3] 비로그인 사용자 (기본 백업)
     * - 단국대 좌표 기준 반경 3km 필터 자동 적용
     * - 로그인 회원이더라도 선호 정보가 없으면 이쪽으로 폴백
     */
    async function initScenarioCampus() {
        if (initialResultsLoaded) return; // 이중 호출 방어
        initialResultsLoaded = true;

        setRadius(DEFAULT_INITIAL_RADIUS); // "3"km
        updateRadiusCircle();
        await runSearch();
    }

    const JOB_MAP_CONFIG = window.JobMapConfig || {};
    const SIGUNGU_BY_SIDO = JOB_MAP_CONFIG.sigunguBySido || {};
    const JOB_MID_BY_MAJOR = JOB_MAP_CONFIG.jobMidByMajor || {};
    const campus = JOB_MAP_CONFIG.defaultCenter || {lat: 37.3216, lng: 127.1267};
    const DEFAULT_INITIAL_RADIUS = "3";
    const cardsContainer = document.querySelector(".cards");
    const initialCardsHtml = cardsContainer.innerHTML;
    let cards = Array.from(document.querySelectorAll(".job-card"));
    const radiusButtons = Array.from(document.querySelectorAll("[data-radius]"));
    const dongButtons = Array.from(document.querySelectorAll("[data-dong]"));
    const visibleCount = document.getElementById("visibleCount");
    const resetCenterButton = document.getElementById("resetCenter");
    const filterSido = document.getElementById("filterSido");
    const filterSigungu = document.getElementById("filterSigungu");
    const filterJobMajor = document.getElementById("filterJobMajor");
    const filterJobMid = document.getElementById("filterJobMid");
    const filterPayType = document.getElementById("filterPayType");
    const sortDistanceBtn = document.getElementById("sortDistanceBtn");
    const sortSalaryBtn = document.getElementById("sortSalaryBtn");
    const filterToggle = document.getElementById("filterToggle");
    const filtersBody = document.getElementById("filtersBody");
    const filterToggleIcon = document.getElementById("filterToggleIcon");
    const resultLimit = document.getElementById("resultLimit");
    const searchBtn = document.getElementById("searchBtn");
    const resetBtn = document.getElementById("resetBtn");
    const allJobsTab = document.getElementById("allJobsTab");
    const recommendationsTab = document.getElementById("recommendationsTab");
    const keywordInput = document.getElementById("keywordInput");
    const tabButtons = [allJobsTab, recommendationsTab].filter(Boolean);
    let selectedRadius = "all";
    let selectedSido = "all";
    let selectedSigungu = "all";
    let selectedJobMajor = "all";
    let selectedPayType = "all";
    let selectedSort = "distance";
    let selectedJobMid = "all";
    let selectedDong = "all";
    let recommendationMode = false;
    let initialResultsLoaded = false;
    let userPreferences = {
        regions: [],
        jobMajor:     "",
        jobMid:       "",
        payType:      "",
        minPayAmount: 0,
        homeLat:      null,
        homeLng:      null
    };
    let customCenter = null;
    let customMarker = null;
    let map;
    let radiusCircle = null;
    let mapRadiusButtons = [];
    let mapRadiusControlElement = null;
    const markers = new Map();
    const scrappedIds = new Set();
    const MARKER_STATUS = {
        INFERRED_LOCATION: "inferred-location",
        CLOSING_SOON: "closing-soon",
        RECOMMENDED: "recommended",
        DEFAULT: "default"
    };
    const MARKER_COLORS = {
        [MARKER_STATUS.INFERRED_LOCATION]: "#6b7280",
        [MARKER_STATUS.CLOSING_SOON]: "#dc2626",
        [MARKER_STATUS.RECOMMENDED]: "#FFC800FF",
        [MARKER_STATUS.DEFAULT]: "#2563eb"
    };

    function searchCenter() {
        return customCenter || campus;
    }

    function distanceKm(a, b) {
        const earthRadius = 6371;
        const dLat = (b.lat - a.lat) * Math.PI / 180;
        const dLng = (b.lng - a.lng) * Math.PI / 180;
        const lat1 = a.lat * Math.PI / 180;
        const lat2 = b.lat * Math.PI / 180;
        const value = Math.sin(dLat / 2) ** 2 +
            Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
        return earthRadius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    function setActive(buttons, activeButton) {
        buttons.forEach((button) => button.classList.toggle("active", button === activeButton));
    }

    function syncRadiusControls() {
        [...radiusButtons, ...mapRadiusButtons].forEach((button) => {
            button.classList.toggle("active", button.dataset.radius === selectedRadius);
        });
    }

    function setRadius(radius) {
        selectedRadius = radius;
        syncRadiusControls();
    }

    function selectCard(card) {
        cards.forEach((item) => item.classList.toggle("active", item === card));
        card.scrollIntoView({block: "nearest"});
    }

    // escapeHtml() → auth-utils.js 전역 함수 사용 (중복 제거)

    function field(source, camelName, snakeName, fallback = "") {
        return source[camelName] ?? source[snakeName] ?? fallback;
    }

    function safeOriginalUrl(value) {
        const url = String(value ?? "").trim();
        if (!url) {
            return "";
        }

        try {
            const parsed = new URL(url);
            return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : "";
        } catch (error) {
            return "";
        }
    }

    function fillSelect(select, placeholder, values) {
        select.innerHTML = `<option value="all">${placeholder}</option>` +
            values.map((value) => `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`).join("");
    }

    /**
     * 회원 선호 정보를 필터 UI에 반영한다.
     * @param {boolean} includeRegion 거주지 반경 모드에서는 false로 호출하여 시/도·시/군/구는 건드리지 않는다.
     */
    function applyInitialPreferenceFilters(includeRegion = true) {
        // [1] 선호 지역 → 시/도 + 시/군/구 드롭다운 세팅 (반경 모드에서는 생략)
        if (includeRegion && userPreferences.regions?.length > 0) {
            const primary = userPreferences.regions[0];
            if (primary.sido) {
                selectedSido = primary.sido;
                filterSido.value = primary.sido;
                fillSelect(filterSigungu, "시/군/구 전체", SIGUNGU_BY_SIDO[selectedSido] || []);
                filterSigungu.disabled = false;
                if (primary.sigungu) {
                    selectedSigungu = primary.sigungu;
                    filterSigungu.value = primary.sigungu;
                }
            }
        }
        // [2] 선호 직종 → 대분류 + 중분류 드롭다운 세팅
        if (userPreferences.jobMajor) {
            selectedJobMajor = userPreferences.jobMajor;
            filterJobMajor.value = userPreferences.jobMajor;
            fillSelect(filterJobMid, "중분류 전체", JOB_MID_BY_MAJOR[selectedJobMajor] || []);
            filterJobMid.disabled = false;
            if (userPreferences.jobMid) {
                selectedJobMid = userPreferences.jobMid;
                filterJobMid.value = userPreferences.jobMid;
            }
        }
        // [3] 선호 급여 유형
        if (userPreferences.payType) {
            selectedPayType = userPreferences.payType;
            filterPayType.value = userPreferences.payType;
            sortSalaryBtn.disabled = false;
            sortSalaryBtn.style.opacity = "1";
            sortSalaryBtn.style.cursor = "pointer";
            sortSalaryBtn.title = "";
        }
    }

    // ── 추천 탭 모드 토글 (Gemini AI 추천 기능) ──────────────────────────────
    function setTabActive(activeTab) {
        tabButtons.forEach((button) => button.classList.toggle("active", button === activeTab));
    }

    function setRecommendationMode(enabled) {
        recommendationMode = enabled;
        setTabActive(enabled ? recommendationsTab : allJobsTab);

        if (keywordInput)       keywordInput.style.display       = enabled ? "none" : "";
        if (filterToggle)       filterToggle.style.display       = enabled ? "none" : "";
        if (filtersBody)        filtersBody.style.display        = enabled ? "none" : "";
        if (resetCenterButton)  resetCenterButton.style.display  = enabled || !customCenter ? "none" : "";
        if (mapRadiusControlElement) mapRadiusControlElement.hidden = enabled;
    }

    function updateRadiusCircle() {
        if (!map) {
            return;
        }

        if (radiusCircle) {
            radiusCircle.remove();
            radiusCircle = null;
        }

        if (selectedRadius === "all") {
            return;
        }

        radiusCircle = L.circle([searchCenter().lat, searchCenter().lng], {
            radius: Number(selectedRadius) * 1000,
            color: "#1769aa",
            fillColor: "#1769aa",
            fillOpacity: 0.12,
            weight: 1
        }).addTo(map);
    }

    function hasServerFilter() {
        return selectedRadius !== "all" ||
            selectedSido !== "all" ||
            selectedSigungu !== "all" ||
            selectedJobMajor !== "all" ||
            selectedJobMid !== "all" ||
            selectedPayType !== "all" ||
            document.getElementById("keywordInput").value.trim() !== "";
    }

    function serverFilterParams() {
        const params = {
            sido: selectedSido,
            sigungu: selectedSigungu,
            jobTypeMajor: selectedJobMajor,
            jobTypeMid: selectedJobMid,
            limit: resultLimit.value,
            keyword: document.getElementById("keywordInput").value.trim(),
            payType: selectedPayType,
            sort: selectedSort
        };
        if (selectedRadius !== "all" || selectedSort === "distance") {
            const center = searchCenter();
            params.lat = center.lat;
            params.lng = center.lng;
        }
        if (selectedRadius !== "all") {
            params.radius = selectedRadius;
        }
        return params;
    }

    function cardDataset(job) {
        return {
            id: field(job, "id", "id"),
            title: field(job, "title", "title"),
            company: field(job, "companyName", "company_name"),
            location: field(job, "location", "location"),
            salary: field(job, "salary", "salary"),
            deadline: field(job, "deadline", "deadline"),
            dong: field(job, "dong", "dong"),
            sido: field(job, "sido", "sido"),
            sigungu: field(job, "sigungu", "sigungu"),
            jobMajor: field(job, "jobTypeMajor", "job_type_major"),
            jobMid: field(job, "jobTypeMid", "job_type_mid"),
            recommendationReason: field(job, "recommendationReason", "recommendation_reason"),
            status: field(job, "status", "status"),
            externalUrl: field(job, "externalUrl", "external_url"),
            lat: field(job, "latitude", "latitude", 0),
            lng: field(job, "longitude", "longitude", 0),
            exactLocation: field(job, "exactLocation", "exact_location", false),
            scraped: field(job, "scraped", "scraped", false)
        };
    }

    function cardHtml(job) {
        const data = cardDataset(job);
        const workTime = field(job, "workTime", "work_time", "근무시간 협의");
        const summaryLines = field(job, "summaryLines", "summary_lines", []);
        const exactLocation = data.exactLocation === true || data.exactLocation === "true";
        const recommendationHtml = data.recommendationReason
            ? `<li>추천 이유: ${escapeHtml(data.recommendationReason)}</li>`
            : "";
        const originalUrl = safeOriginalUrl(data.externalUrl);
        const detailHref = originalUrl || `/jobpostings/${encodeURIComponent(data.id)}`;
        const detailTarget = originalUrl ? ` target="_blank" rel="noopener"` : "";
        const summaryHtml = Array.isArray(summaryLines)
            ? recommendationHtml + summaryLines.map((line) => `<li>${escapeHtml(line)}</li>`).join("")
            : "";

        return `
            <article class="job-card"
                     id="job-${escapeHtml(data.id)}"
                     data-id="${escapeHtml(data.id)}"
                     data-title="${escapeHtml(data.title)}"
                     data-company="${escapeHtml(data.company)}"
                     data-location="${escapeHtml(data.location)}"
                     data-salary="${escapeHtml(data.salary)}"
                     data-deadline="${escapeHtml(data.deadline)}"
                     data-dong="${escapeHtml(data.dong)}"
                     data-sido="${escapeHtml(data.sido)}"
                     data-sigungu="${escapeHtml(data.sigungu)}"
                     data-job-major="${escapeHtml(data.jobMajor)}"
                     data-job-mid="${escapeHtml(data.jobMid)}"
                     data-recommendation-reason="${escapeHtml(data.recommendationReason)}"
                     data-status="${escapeHtml(data.status)}"
                     data-external-url="${escapeHtml(data.externalUrl)}"
                     data-lat="${escapeHtml(data.lat)}"
                     data-lng="${escapeHtml(data.lng)}"
                     data-exact-location="${exactLocation}"
                     data-scraped="${data.scraped}">
                <div>
                    <p class="company">${escapeHtml(data.company)}</p>
                    <h2 class="job-title">${escapeHtml(data.title)}</h2>
                </div>

                <div class="meta-grid">
                    <div class="meta-item">
                        <span class="meta-label">위치</span>
                        <span class="meta-value">${escapeHtml(data.location)}</span>
                    </div>
                    <div class="meta-item">
                        <span class="meta-label">시급/급여</span>
                        <span class="meta-value">${escapeHtml(data.salary)}</span>
                    </div>
                    <div class="meta-item">
                        <span class="meta-label">근무시간</span>
                        <span class="meta-value">${escapeHtml(workTime)}</span>
                    </div>
                    <div class="meta-item">
                        <span class="meta-label">마감일</span>
                        <span class="meta-value">${escapeHtml(data.deadline)}</span>
                    </div>
                </div>

                <ul class="summary" aria-label="AI 요약">${summaryHtml}</ul>

                <span class="location-badge${exactLocation ? "" : " pending"}">
                    ${exactLocation ? "실제 위치" : "좌표 미등록 - 임시 위치"}
                </span>

                <div class="card-actions">
                    <!-- 외부 공고는 새 탭, 내부 공고는 상세 페이지 이동 -->
                    <a class="detail-link" href="${escapeHtml(detailHref)}"${detailTarget}>상세 보기</a>
                    <button class="scrap-btn${data.scraped === true || data.scraped === 'true' ? ' scrapped' : ''}"
                            type="button"
                            data-id="${escapeHtml(data.id)}"
                            data-scraped="${data.scraped}"
                            aria-label="스크랩">${data.scraped === true || data.scraped === 'true' ? '★' : '☆'}</button>
                </div>
            </article>`;
    }

    function clearMarkers() {
        markers.forEach((marker) => marker.remove());
        markers.clear();
    }

    function popupRow(label, value) {
        const text = String(value ?? "").trim();
        if (!text) {
            return "";
        }

        return `<span class="popup-label">${escapeHtml(label)}: </span><span class="popup-value">${escapeHtml(text)}</span><br>`;
    }

    function summaryText(card) {
        const recommendationReason = String(card.dataset.recommendationReason ?? "").trim();
        if (recommendationReason) {
            return "추천 이유: " + recommendationReason;
        }

        const lines = Array.from(card.querySelectorAll(".summary li"))
            .map((item) => item.textContent.trim())
            .filter(Boolean);

        return lines.length > 0 ? lines.join(" / ") : "";
    }

    function distanceLabel(lat, lng) {
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
            return "";
        }

        return `${distanceKm(searchCenter(), {lat, lng}).toFixed(1)}km`;
    }

    function parseDeadlineDate(value) {
        const text = String(value ?? "").trim();
        if (!text || text === "마감일 미정") {
            return null;
        }

        const numericMatch = text.match(/(\d{4})[.\-/년\s]+(\d{1,2})[.\-/월\s]+(\d{1,2})/);
        if (!numericMatch) {
            // TODO: API/DTO에서 deadline을 ISO 날짜로 내려주면 문자열 추정 대신 해당 필드로 판정한다.
            return null;
        }

        const [, year, month, day] = numericMatch;
        const date = new Date(Number(year), Number(month) - 1, Number(day));
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function isClosingSoon(card) {
        const deadlineDate = parseDeadlineDate(card.dataset.deadline);
        if (!deadlineDate) {
            return false;
        }

        const today = new Date();
        today.setHours(0, 0, 0, 0);
        deadlineDate.setHours(0, 0, 0, 0);

        const daysLeft = (deadlineDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24);
        return daysLeft >= 0 && daysLeft <= 3;
    }

    function matchesPreference(value, preference) {
        return Boolean(preference) && value === preference;
    }

    function matchesPreferredRegion(card) {
        return userPreferences.regions.some((region) => {
            return matchesPreference(card.dataset.sido, region.sido) &&
                (!region.sigungu || card.dataset.sigungu === region.sigungu);
        });
    }

    function isRecommendedJob(card) {
        const matchesRegion = matchesPreferredRegion(card);
        const matchesJobType = matchesPreference(card.dataset.jobMajor, userPreferences.jobMajor) &&
            (!userPreferences.jobMid || card.dataset.jobMid === userPreferences.jobMid);

        // TODO: 추천 여부 전용 필드(recommended, recommendationScore 등)가 API/DTO에 노출되면 이 조건보다 우선 사용한다.
        return matchesRegion || matchesJobType;
    }

    function getMarkerStatus(card, lat, lng) {
        const isExactLocation = card.dataset.exactLocation === "true";
        if (!isExactLocation || !Number.isFinite(lat) || !Number.isFinite(lng)) {
            return MARKER_STATUS.INFERRED_LOCATION;
        }

        if (isClosingSoon(card)) {
            return MARKER_STATUS.CLOSING_SOON;
        }

        if (isRecommendedJob(card)) {
            return MARKER_STATUS.RECOMMENDED;
        }

        return MARKER_STATUS.DEFAULT;
    }

    function getMarkerColor(status) {
        return MARKER_COLORS[status] || MARKER_COLORS[MARKER_STATUS.DEFAULT];
    }

    function createColoredMarkerIcon(status) {
        return L.divIcon({
            className: `job-marker-icon job-marker-icon--${status}`,
            html: `<span style="background:${getMarkerColor(status)}"></span>`,
            iconSize: [36, 44],
            iconAnchor: [18, 40],
            popupAnchor: [0, -40]
        });
    }

    function markerPopupHtml(card, lat, lng) {
        const rows = [
            popupRow("회사명", card.dataset.company),
            popupRow("지역", card.dataset.location),
            popupRow("급여", card.dataset.salary),
            popupRow("마감일", card.dataset.deadline),
            popupRow("AI 요약", summaryText(card)),
            popupRow("거리", distanceLabel(lat, lng))
        ].join("");

        return `
            <div class="popup-job">
                <p class="popup-title">${escapeHtml(card.dataset.title)}</p>
                <p class="popup-meta">${rows}</p>
            </div>
        `;
    }

    function createMarker(card) {
        const lat = Number(card.dataset.lat);
        const lng = Number(card.dataset.lng);

        if (Number.isNaN(lat) || Number.isNaN(lng) || (lat === 0 && lng === 0)) {
            return null;
        }

        const status = getMarkerStatus(card, lat, lng);
        const marker = L.marker([lat, lng], {
            icon: createColoredMarkerIcon(status)
        });
        marker.bindPopup(markerPopupHtml(card, lat, lng));
        marker.on("click", () => selectCard(card));
        return marker;
    }

    function renderMarkers() {
        if (!map) {
            return;
        }

        clearMarkers();
        cards.forEach((card) => {
            const marker = createMarker(card);
            if (marker) {
                markers.set(card.dataset.id, marker);
            }
        });
    }

    function bindCardEvents() {
        cards.forEach((card) => {
            card.addEventListener("click", (event) => {
                if (event.target.closest("a") || event.target.closest("button")) {
                    return;
                }
                selectCard(card);
                const marker = markers.get(card.dataset.id);
                if (marker) {
                    marker.openPopup();
                    map.setView(marker.getLatLng(), 15);
                }
            });
        });
    }

    function syncCardsFromDom() {
        cards = Array.from(document.querySelectorAll(".job-card"));
        bindCardEvents();
        renderMarkers();
        renderScrapButtons();
    }

    function renderCards(jobs) {
        if (jobs.length === 0) {
            cardsContainer.innerHTML = `<p class="empty">조건에 맞는 채용 공고가 없습니다.</p>`;
        } else {
            cardsContainer.innerHTML = jobs.map(cardHtml).join("");
        }
        syncCardsFromDom();
    }

    function resetToInitialCards() {
        cardsContainer.innerHTML = initialCardsHtml;
        syncCardsFromDom();
    }

    async function fetchAndRender(params) {
        try {
            const query = new URLSearchParams(params);
            const response = await fetch(`/api/job-postings/search?${query.toString()}`);

            if (!response.ok) {
                throw new Error(`채용 공고 검색 실패: ${response.status}`);
            }

            const jobs = await response.json();
            renderCards(jobs);
        } catch (error) {
            throw error;
        }
    }

    async function fetchAndRenderRecommendations() {
        const token = getToken();
        if (!token) {
            cardsContainer.innerHTML = `<p class="empty">맞춤 추천은 로그인이 필요합니다.</p>`;
            syncCardsFromDom();
            visibleCount.textContent = "0";
            return;
        }

        const response = await fetch("/api/job-postings/recommendations", {
            headers: authHeaders()
        });
        if (!response.ok) {
            throw new Error(`맞춤 추천 조회 실패: ${response.status}`);
        }

        const jobs = await response.json();
        if (jobs.length === 0) {
            cardsContainer.innerHTML = `<p class="empty">아직 생성된 맞춤 추천 공고가 없습니다.</p>`;
            syncCardsFromDom();
            visibleCount.textContent = "0";
            return;
        }

        renderCards(jobs);
        showAllCards();
    }

    async function refreshServerResults() {
        try {
            if (hasServerFilter()) {
                await fetchAndRender(serverFilterParams());
            } else {
                resetToInitialCards();
            }
            applyFilters();
        } catch (error) {
            console.error(error);
            visibleCount.textContent = "0";
        }
    }

    async function runSearch() {
        try {
            if (recommendationMode) {
                await fetchAndRenderRecommendations();
                return;
            }

            const allFiltersDefault = !hasServerFilter();

            if (allFiltersDefault && resultLimit.value === "50" && selectedSort !== "distance") {
                resetToInitialCards();
                applyFilters();
                return;
            }

            if (allFiltersDefault) {
                await fetchAndRender(serverFilterParams());
                applyFilters();
                return;
            }

            await refreshServerResults();
        } catch (error) {
            console.error(error);
            visibleCount.textContent = "0";
        }
    }

    // loadDefaultCampusResults() → initScenarioCampus() 로 통합됨 (통합 진입점 참조)

function applyFilters() {
    let count = 0;
    const visibleMarkers = [];

    // focusId가 URL에 있으면 해당 카드만 표시하는 포커스 모드로 동작
    const urlParams = new URLSearchParams(window.location.search);
    const rawFocus  = urlParams.get("focusId");
    const focusId   = (rawFocus && rawFocus !== "undefined" && rawFocus.trim() !== "")
        ? rawFocus.trim()
        : null;

    cards.forEach((card) => {
        const isExactLocation = card.dataset.exactLocation === "true";
        const lat = Number(card.dataset.lat);
        const lng = Number(card.dataset.lng);
        const hasValidPoint = isExactLocation && !Number.isNaN(lat) && !Number.isNaN(lng) && !(lat === 0 && lng === 0);

        const point = {lat, lng};

        // 기존 필터 조건들
        const inRadius = selectedRadius === "all" || (hasValidPoint && distanceKm(searchCenter(), point) <= Number(selectedRadius));
        const inSido = selectedSido === "all" || card.dataset.sido === selectedSido;
        const inSigungu = selectedSigungu === "all" || card.dataset.sigungu === selectedSigungu;
        const inDong = selectedDong === "all" || card.dataset.dong === selectedDong;
        const inJobMajor = selectedJobMajor === "all" || card.dataset.jobMajor === selectedJobMajor;
        const inJobMid = selectedJobMid === "all" || card.dataset.jobMid === selectedJobMid;

        let visible = inRadius && inSido && inSigungu && inDong && inJobMajor && inJobMid;

        const cardId = (card.dataset.id ?? "").trim();

        // focusId 모드: 해당 카드만 표시, 나머지 숨김
        if (focusId) {
            visible = (cardId === focusId);
        }

        const marker = markers.get(card.dataset.id);
        card.hidden = !visible;

        if (marker) {
            if (visible) {
                marker.addTo(map);
                visibleMarkers.push(marker);
            } else {
                marker.remove();
            }
        }

        if (visible) {
            count += 1;
        }
    });

    visibleCount.textContent = count;

    // 지도 카메라 처리
    if (visibleMarkers.length > 0) {
        if (focusId) {
            // focusId 모드: 해당 마커로 핀포인트 이동 + 팝업 자동 오픈
            const targetMarker = visibleMarkers[0];
            map.setView(targetMarker.getLatLng(), 17);
            setTimeout(() => targetMarker.openPopup(), 150);

            const targetCard = document.querySelector(".job-card:not([hidden])");
            if (targetCard) selectCard(targetCard);
        } else {
            // 일반 모드: 모든 가시 마커가 화면에 들어오도록 자동 줌
            map.fitBounds(L.featureGroup(visibleMarkers).getBounds().pad(0.22), {maxZoom: 15});
        }
    } else if (focusId) {
        console.warn("[focusId 매칭 실패] DOM에서 찾을 수 없는 ID:", focusId,
            "— 컨트롤러의 focusId 주입 로직을 확인하세요.");
    }
}

    function showAllCards() {
        let count = 0;
        const visibleMarkers = [];

        cards.forEach((card) => {
            card.hidden = false;
            count += 1;

            const marker = markers.get(card.dataset.id);
            if (marker && map) {
                marker.addTo(map);
                visibleMarkers.push(marker);
            }
        });

        visibleCount.textContent = count;

        if (map && visibleMarkers.length > 0) {
            map.fitBounds(L.featureGroup(visibleMarkers).getBounds().pad(0.22), {maxZoom: 15});
        }
    }

    function addMarkerLegend() {
        const legend = L.control({position: "bottomleft"});
        legend.onAdd = () => {
            const container = L.DomUtil.create("div", "marker-legend");
            container.innerHTML = `
                <div><span class="marker-legend-dot marker-legend-dot--recommended"></span>추천 공고</div>
                <div><span class="marker-legend-dot marker-legend-dot--default"></span>일반 공고</div>
            `;
            return container;
        };
        legend.addTo(map);
    }

    function addRadiusControl() {
        const radiusControl = L.control({position: "topright"});
        const options = [
            {value: "all", label: "전체"},
            {value: "1", label: "1km"},
            {value: "3", label: "3km"},
            {value: "5", label: "5km"}
        ];

        radiusControl.onAdd = () => {
            const container = L.DomUtil.create("div", "map-radius-control");
            mapRadiusControlElement = container;
            container.setAttribute("aria-label", "지도 반경 필터");
            container.innerHTML = `
                <p>거리</p>
                <div>
                    ${options.map((option) => `
                        <button type="button" data-radius="${option.value}">${option.label}</button>
                    `).join("")}
                </div>
            `;

            L.DomEvent.disableClickPropagation(container);
            L.DomEvent.disableScrollPropagation(container);

            mapRadiusButtons = Array.from(container.querySelectorAll("[data-radius]"));
            mapRadiusButtons.forEach((button) => {
                button.addEventListener("click", async () => {
                    await applyRadius(button.dataset.radius);
                });
            });
            syncRadiusControls();

            return container;
        };

        radiusControl.addTo(map);
    }

    async function applyRadius(radius) {
        setRadius(radius);
        if (selectedRadius !== "all") {
            selectedSido = "all";
            selectedSigungu = "all";
            filterSido.value = "all";
            fillSelect(filterSigungu, "시/군/구 전체", []);
            filterSigungu.disabled = true;
        }
        updateRadiusCircle();
        await runSearch();
    }

    function initMap() {
        map = L.map("jobMap", {scrollWheelZoom: true}).setView([campus.lat, campus.lng], 14);
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 19,
            attribution: "&copy; OpenStreetMap"
        }).addTo(map);
        addRadiusControl();
        addMarkerLegend();

        map.on("click", async (e) => {
            customCenter = {lat: e.latlng.lat, lng: e.latlng.lng};
            if (customMarker) {
                customMarker.remove();
            }
            customMarker = L.marker([e.latlng.lat, e.latlng.lng], {
                icon: L.divIcon({className: "custom-center-icon", html: "📍", iconSize: [24, 24]})
            }).addTo(map);
            if (resetCenterButton) {
                resetCenterButton.style.display = "";
            }
            if (selectedRadius === "all") {
                setRadius("1");
            }
            updateRadiusCircle();
            await runSearch();
        });

        syncCardsFromDom();
        updateRadiusCircle();
        // ★ 데이터 로딩은 통합 진입점(DOMContentLoaded)에서 시나리오별로 처리
        //    여기서 직접 호출하지 않음 → 경쟁 상태 방지
    }

    radiusButtons.forEach((button) => {
        button.addEventListener("click", async () => {
            await applyRadius(button.dataset.radius);
        });
    });

    filterSido.addEventListener("change", (event) => {
        selectedSido = event.target.value;
        fillSelect(filterSigungu, "시/군/구 전체", SIGUNGU_BY_SIDO[selectedSido] || []);
        filterSigungu.disabled = selectedSido === "all";
        selectedSigungu = "all";
    });

    filterSigungu.addEventListener("change", (event) => {
        selectedSigungu = event.target.value;
    });

    filterJobMajor.addEventListener("change", (event) => {
        selectedJobMajor = event.target.value;
        fillSelect(filterJobMid, "중분류 전체", JOB_MID_BY_MAJOR[selectedJobMajor] || []);
        filterJobMid.disabled = selectedJobMajor === "all";
        selectedJobMid = "all";
    });

    filterJobMid.addEventListener("change", (event) => {
        selectedJobMid = event.target.value;
    });

    filterPayType.addEventListener("change", (event) => {
        selectedPayType = event.target.value;
        const enabled = selectedPayType !== "all";
        sortSalaryBtn.disabled = !enabled;
        sortSalaryBtn.style.opacity = enabled ? "1" : "0.4";
        sortSalaryBtn.style.cursor = enabled ? "pointer" : "not-allowed";
        sortSalaryBtn.title = enabled ? "" : "급여유형을 선택하세요";
        if (!enabled && selectedSort === "salary") {
            selectedSort = "distance";
            sortDistanceBtn.classList.add("active");
            sortSalaryBtn.classList.remove("active");
        }
    });

    sortDistanceBtn.addEventListener("click", () => {
        selectedSort = "distance";
        sortDistanceBtn.classList.add("active");
        sortSalaryBtn.classList.remove("active");
    });

    sortSalaryBtn.addEventListener("click", () => {
        if (sortSalaryBtn.disabled) return;
        selectedSort = "salary";
        sortSalaryBtn.classList.add("active");
        sortDistanceBtn.classList.remove("active");
    });

    filterToggle.addEventListener("click", () => {
        const collapsed = filtersBody.classList.toggle("collapsed");
        filterToggleIcon.textContent = collapsed ? "▼" : "▲";
        filterToggle.setAttribute("aria-expanded", String(!collapsed));
    });

    resetBtn.addEventListener("click", async () => {
        filterSido.value = "all";
        fillSelect(filterSigungu, "시/군/구 전체", []);
        filterSigungu.disabled = true;
        filterJobMajor.value = "all";
        fillSelect(filterJobMid, "중분류 전체", []);
        filterJobMid.disabled = true;
        filterPayType.value = "all";
        resultLimit.value = "50";
        document.getElementById("keywordInput").value = "";

        selectedSido = "all";
        selectedSigungu = "all";
        selectedJobMajor = "all";
        selectedJobMid = "all";
        selectedPayType = "all";
        selectedSort = "distance";
        setRadius(DEFAULT_INITIAL_RADIUS);
        selectedDong = "all";

        setActive(dongButtons, dongButtons.find(b => b.dataset.dong === "all"));
        updateRadiusCircle();

        sortDistanceBtn.classList.add("active");
        sortSalaryBtn.classList.remove("active");
        sortSalaryBtn.disabled = true;
        sortSalaryBtn.style.opacity = "0.4";
        sortSalaryBtn.style.cursor = "not-allowed";
        sortSalaryBtn.title = "급여유형을 선택하세요";

        if (customMarker) { customMarker.remove(); customMarker = null; }
        customCenter = null;
        if (resetCenterButton) resetCenterButton.style.display = "none";

        await runSearch();
    });

    dongButtons.forEach((button) => {
        button.addEventListener("click", () => {
            selectedDong = button.dataset.dong;
            setActive(dongButtons, button);
            applyFilters();
        });
    });

    if (resetCenterButton) {
        resetCenterButton.addEventListener("click", async () => {
            customCenter = null;
            if (customMarker) {
                customMarker.remove();
                customMarker = null;
            }
            resetCenterButton.style.display = "none";
            updateRadiusCircle();
            await runSearch();
        });
    }

    searchBtn.addEventListener("click", async () => {
        await runSearch();
    });

    document.getElementById("keywordInput").addEventListener("keydown", async (e) => {
        if (e.key === "Enter") await runSearch();
    });

    // ── 추천 탭 이벤트 (Gemini AI 추천 기능) ─────────────────────────────
    if (allJobsTab) {
        allJobsTab.addEventListener("click", async () => {
            setRecommendationMode(false);
            await runSearch();
        });
    }

    if (recommendationsTab) {
        recommendationsTab.addEventListener("click", async () => {
            if (!getToken()) {
                cardsContainer.innerHTML = `<p class="empty">맞춤 추천은 로그인이 필요합니다.</p>`;
                syncCardsFromDom();
                visibleCount.textContent = "0";
                return;
            }
            setRecommendationMode(true);
            updateRadiusCircle();
            await fetchAndRenderRecommendations();
        });
    }

    // ★ initMap()은 통합 진입점(DOMContentLoaded)에서 호출 — 여기서 직접 호출 제거

// getToken() → auth-utils.js 전역 함수 사용 (중복 제거)

/**
 * 버튼 한 개의 시각 상태(텍스트·클래스·title)를 scraped 값에 맞게 갱신한다.
 */
function applyScrapState(btn, scraped) {
    btn.textContent = scraped ? "★" : "☆";
    btn.classList.toggle("scrapped", scraped);
    btn.title = scraped ? "스크랩 해제" : "스크랩";
    btn.dataset.scraped = String(scraped);
}

/**
 * 현재 DOM에 있는 모든 .scrap-btn 을 scrappedIds Set 기준으로 동기화한다.
 */
function renderScrapButtons() {
    document.querySelectorAll(".scrap-btn").forEach((btn) => {
        applyScrapState(btn, scrappedIds.has(Number(btn.dataset.id)));
    });
}

/**
 * 페이지 로드 시:
 *  1) Thymeleaf가 서버에서 이미 data-scraped 를 채워 놓은 경우 DOM에서 초기화
 *  2) 서버 확인용 /api/scraps/my/ids 로 검증·동기화
 */
async function loadScrappedIds() {
    // ① Thymeleaf 서버 렌더링 결과로 scrappedIds 초기화 (플래시 없음)
    document.querySelectorAll(".scrap-btn[data-scraped='true']").forEach((btn) => {
        scrappedIds.add(Number(btn.dataset.id));
    });
    renderScrapButtons();

    // ② 로그인 상태면 서버와 동기화
    const token = getToken();
    if (!token) return;
    try {
        const res = await fetch("/api/scraps/my/ids", {
            headers: { "Authorization": "Bearer " + token }
        });
        if (!res.ok) return;
        const ids = await res.json();
        scrappedIds.clear();
        ids.forEach((id) => scrappedIds.add(Number(id)));
        renderScrapButtons();
    } catch (e) {
        console.warn("스크랩 목록 로드 실패", e);
    }
}

// 이벤트 위임 — 동적으로 추가된 버튼도 동작
document.querySelector(".cards").addEventListener("click", async (event) => {
    const btn = event.target.closest(".scrap-btn");
    if (!btn) return;

    const token = getToken();
    if (!token) {
        alert("로그인이 필요한 서비스입니다.");
        return;
    }

    const id = Number(btn.dataset.id);
    btn.disabled = true;
    try {
        const res = await fetch(`/api/scraps/toggle?jobPostingId=${id}`, {
            method: "POST",
            headers: { "Authorization": "Bearer " + token }
        });
        if (res.status === 401) { alert("로그인이 필요한 서비스입니다."); return; }
        if (!res.ok) throw new Error(res.status);
        const data = await res.json();          // { "scraped": true/false }
        if (data.scraped) {
            scrappedIds.add(id);
        } else {
            scrappedIds.delete(id);
        }
        applyScrapState(btn, data.scraped);     // 해당 버튼만 즉시 갱신
    } catch (e) {
        alert("스크랩 처리에 실패했습니다.");
    } finally {
        btn.disabled = false;
    }
});

/* ═══════════════════════════════════════════════════════════════════════════
   ★★★ 통합 진입점 — DOMContentLoaded 리스너는 이 하나뿐 ★★★
   경쟁 상태(Race Condition) 원천 차단:
     - async/await 직렬화로 시나리오 간 덮어쓰기 불가
     - initMap() → loadScrappedIds() → 시나리오 초기화 순으로 완벽하게 교통정리
═══════════════════════════════════════════════════════════════════════════ */
document.addEventListener("DOMContentLoaded", async () => {

    // ── 1. 인증 네비게이션 (동기, 즉시 반영) ─────────────────────────────
    initAuthUI();

    // ── 2. 지도 초기화 (Leaflet 없으면 스킵) ─────────────────────────────
    if (window.L) {
        initMap();
    }

    // ── 3. 스크랩 상태 로드 (독립적 — 필터링 로직과 무관, fire-and-forget) ─
    loadScrappedIds(); // await 없음: 스크랩 표시 지연이 초기 필터에 영향 없음

    // ── 4. URL 파라미터 파싱 ──────────────────────────────────────────────
    const rawFocusId = new URLSearchParams(window.location.search).get("focusId");
    const focusId    = (rawFocusId && rawFocusId !== "undefined" && rawFocusId.trim() !== "")
        ? rawFocusId.trim()
        : null;
    const token = localStorage.getItem("accessToken");

    // ── 5. 시나리오 라우팅 (await → 직렬 실행, 경쟁 상태 원천 차단) ───────
    //
    //  [우선순위 1] focusId 존재 → 스크랩 공고 강조 모드
    //  [우선순위 2] 토큰 존재   → 로그인 회원 선호 필터
    //  [우선순위 3] 그 외       → 비로그인, 단국대 3km 기본 필터
    //
    if (focusId) {
        await initScenarioFocus(focusId);
    } else if (token) {
        await initScenarioLoggedIn(token);
    } else {
        await initScenarioCampus();
    }
});

window.addEventListener("pageshow", (event) => {
    if (event.persisted) {
        initAuthUI();
    }
});
