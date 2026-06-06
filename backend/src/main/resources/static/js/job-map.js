function updateAuthNav() {
        const token = localStorage.getItem("accessToken");
        document.getElementById("guestMenu").style.display = token ? "none" : "";
        document.getElementById("userMenu").style.display = token ? "flex" : "none";
    }

    function handleLogout() {
        if (confirm("로그아웃 하시겠습니까?")) {
            localStorage.removeItem("accessToken");
            localStorage.removeItem("memberId");
            document.cookie = "accessToken=; path=/; max-age=0";
            location.href = "/auth/login";
        }
    }

    async function applyUserPreferences() {
        const token = localStorage.getItem("accessToken");
        if (!token) {
            return;
        }

        try {
            const response = await fetch("/auth/members/me", {
                headers: {"Authorization": "Bearer " + token}
            });
            if (!response.ok) {
                return;
            }

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

            userPreferences = {
                regions: [
                    {sido: desiredSido || "", sigungu: desiredSigungu || ""},
                    {sido: desiredRegion2Sido || "", sigungu: desiredRegion2Sigungu || ""},
                    {sido: desiredRegion3Sido || "", sigungu: desiredRegion3Sigungu || ""}
                ],
                jobMajor: preferredMajor || "",
                jobMid: preferredMid || "",
                payType: preferredPayType || ""
            };

            if (shouldApplyInitialPreferences()) {
                applyInitialPreferenceFilters();
                await runSearch();
                initialPreferenceFiltersApplied = true;
                return;
            }

            if (map) {
                renderMarkers();
                applyFilters();
            }
        } catch (error) {
            // 프로필을 불러오지 못하면 기본 목록을 그대로 보여준다.
        }
    }

    document.addEventListener("DOMContentLoaded", updateAuthNav);
    document.addEventListener("DOMContentLoaded", applyUserPreferences);

    const JOB_MAP_CONFIG = window.JobMapConfig || {};
    const SIGUNGU_BY_SIDO = JOB_MAP_CONFIG.sigunguBySido || {};
    const JOB_MID_BY_MAJOR = JOB_MAP_CONFIG.jobMidByMajor || {};
    const campus = JOB_MAP_CONFIG.defaultCenter || {lat: 37.3216, lng: 127.1267};
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
    let selectedRadius = "all";
    let selectedSido = "all";
    let selectedSigungu = "all";
    let selectedJobMajor = "all";
    let selectedPayType = "all";
    let selectedSort = "distance";
    let selectedJobMid = "all";
    let selectedDong = "all";
    let initialPreferenceFiltersApplied = false;
    let userPreferences = {
        regions: [],
        jobMajor: "",
        jobMid: "",
        payType: ""
    };
    let customCenter = null;
    let customMarker = null;
    let map;
    let radiusCircle = null;
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

    function selectCard(card) {
        cards.forEach((item) => item.classList.toggle("active", item === card));
        card.scrollIntoView({block: "nearest"});
    }

    function escapeHtml(value) {
        return String(value ?? "").replace(/[&<>"']/g, (char) => ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            "\"": "&quot;",
            "'": "&#39;"
        }[char]));
    }

    function field(source, camelName, snakeName, fallback = "") {
        return source[camelName] ?? source[snakeName] ?? fallback;
    }

    function fillSelect(select, placeholder, values) {
        select.innerHTML = `<option value="all">${placeholder}</option>` +
            values.map((value) => `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`).join("");
    }

    function shouldApplyInitialPreferences() {
        // 초기 필터 적용 로직
        // 1. 선호 지역이 존재하는지 체크
        const hasRegionPreference = userPreferences.regions && 
                                    userPreferences.regions.length > 0 && 
                                    Boolean(userPreferences.regions[0].sido);

        // 2. 선호 직종이 존재하는지 체크
        const hasJobPreference = Boolean(userPreferences.jobMajor);

        // 3. 둘 중 하나라도 설정되어 있다면 초기 필터 대상임
        const hasAnyPreference = hasRegionPreference || hasJobPreference;

        return !initialPreferenceFiltersApplied &&
            hasAnyPreference &&
            selectedRadius === "all" &&
            selectedSido === "all" &&
            selectedSigungu === "all" &&
            selectedJobMajor === "all" &&
            selectedJobMid === "all" &&
            selectedPayType === "all" &&
            selectedDong === "all" &&
            document.getElementById("keywordInput").value.trim() === "";
    }

    function applyInitialPreferenceFilters() {
        // [1] 지역 설정 바인딩 (존재할 때만)
        if (userPreferences.regions && userPreferences.regions.length > 0) {
            const primaryRegion = userPreferences.regions[0];
            if (primaryRegion.sido) {
                selectedSido = primaryRegion.sido;
                filterSido.value = primaryRegion.sido;
                fillSelect(filterSigungu, "시/군/구 전체", SIGUNGU_BY_SIDO[selectedSido] || []);
                filterSigungu.disabled = false;

                if (primaryRegion.sigungu) {
                    selectedSigungu = primaryRegion.sigungu;
                    filterSigungu.value = primaryRegion.sigungu;
                }
            }
        }

        // [2] 직종 설정 바인딩 (존재할 때만)
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

        // [3] 급여 유형 연동
        if (userPreferences.payType) {
            selectedPayType = userPreferences.payType;
            filterPayType.value = userPreferences.payType;
            sortSalaryBtn.disabled = false;
            sortSalaryBtn.style.opacity = "1";
            sortSalaryBtn.style.cursor = "pointer";
            sortSalaryBtn.title = "";
        }
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
            status: field(job, "status", "status"),
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
        const summaryHtml = Array.isArray(summaryLines)
            ? summaryLines.map((line) => `<li>${escapeHtml(line)}</li>`).join("")
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
                     data-status="${escapeHtml(data.status)}"
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
                    <a class="detail-link" href="/jobpostings/${escapeHtml(data.id)}">상세 보기</a>
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
        const lines = Array.from(card.querySelectorAll(".summary li"))
            .map((item) => item.textContent.trim())
            .filter(Boolean);

        // TODO: AI 추천 사유(recommendation_reason 등)가 API/DTO에 노출되면 summaryLines보다 우선 표시한다.
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

        if (Number.isNaN(lat) || Number.isNaN(lng)) {
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
            const allFiltersDefault = !hasServerFilter();

            if (allFiltersDefault && resultLimit.value === "10" && selectedSort !== "distance") {
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
    
function applyFilters() {
    let count = 0;
    const visibleMarkers = [];

    cards.forEach((card) => {
        const isExactLocation = card.dataset.exactLocation === "true";
        const lat = Number(card.dataset.lat);
        const lng = Number(card.dataset.lng);
        const hasValidPoint = isExactLocation && !Number.isNaN(lat) && !Number.isNaN(lng);

        const point = {lat, lng};

        const inRadius =
            selectedRadius === "all" ||
            (hasValidPoint && distanceKm(searchCenter(), point) <= Number(selectedRadius));

        const inSido =
            selectedSido === "all" || card.dataset.sido === selectedSido;

        const inSigungu =
            selectedSigungu === "all" || card.dataset.sigungu === selectedSigungu;

        const inDong =
            selectedDong === "all" || card.dataset.dong === selectedDong;

        const inJobMajor =
            selectedJobMajor === "all" || card.dataset.jobMajor === selectedJobMajor;

        const inJobMid =
            selectedJobMid === "all" || card.dataset.jobMid === selectedJobMid;

        const visible = inRadius && inSido && inSigungu && inDong && inJobMajor && inJobMid;
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

    if (visibleMarkers.length > 0) {
        map.fitBounds(L.featureGroup(visibleMarkers).getBounds().pad(0.22), {maxZoom: 15});
    }
}

    function addMarkerLegend() {
        const legend = L.control({position: "bottomleft"});
        legend.onAdd = () => {
            const container = L.DomUtil.create("div", "marker-legend");
            container.innerHTML = `
                <div><span class="marker-legend-dot marker-legend-dot--closing"></span>마감 임박</div>
                <div><span class="marker-legend-dot marker-legend-dot--recommended"></span>추천 공고</div>
                <div><span class="marker-legend-dot marker-legend-dot--default"></span>일반 공고</div>
                <div><span class="marker-legend-dot marker-legend-dot--inferred"></span>위치 정보 부족</div>
            `;
            return container;
        };
        legend.addTo(map);
    }

    function initMap() {
        map = L.map("jobMap", {scrollWheelZoom: true}).setView([campus.lat, campus.lng], 14);
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 19,
            attribution: "&copy; OpenStreetMap"
        }).addTo(map);
        addMarkerLegend();

        map.on("click", async (e) => {
            customCenter = {lat: e.latlng.lat, lng: e.latlng.lng};
            if (customMarker) {
                customMarker.remove();
            }
            customMarker = L.marker([e.latlng.lat, e.latlng.lng], {
                icon: L.divIcon({className: "custom-center-icon", html: "📍", iconSize: [24, 24]})
            }).addTo(map).bindPopup("검색 중심").openPopup();
            if (resetCenterButton) {
                resetCenterButton.style.display = "";
            }
            if (selectedRadius === "all") {
                const btn = radiusButtons.find((button) => button.dataset.radius === "1");
                if (btn) {
                    selectedRadius = "1";
                    setActive(radiusButtons, btn);
                }
            }
            updateRadiusCircle();
            await runSearch();
        });

        syncCardsFromDom();
        applyFilters();
    }

    radiusButtons.forEach((button) => {
        button.addEventListener("click", async () => {
            selectedRadius = button.dataset.radius;
            setActive(radiusButtons, button);
            if (selectedRadius !== "all") {
                selectedSido = "all";
                selectedSigungu = "all";
                filterSido.value = "all";
                fillSelect(filterSigungu, "시/군/구 전체", []);
                filterSigungu.disabled = true;
            }
            updateRadiusCircle();
            await runSearch();
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
    });

    resetBtn.addEventListener("click", async () => {
        filterSido.value = "all";
        fillSelect(filterSigungu, "시/군/구 전체", []);
        filterSigungu.disabled = true;
        filterJobMajor.value = "all";
        fillSelect(filterJobMid, "중분류 전체", []);
        filterJobMid.disabled = true;
        filterPayType.value = "all";
        resultLimit.value = "10";
        document.getElementById("keywordInput").value = "";

        selectedSido = "all";
        selectedSigungu = "all";
        selectedJobMajor = "all";
        selectedJobMid = "all";
        selectedPayType = "all";
        selectedSort = "distance";
        selectedRadius = "all";
        selectedDong = "all";

        setActive(radiusButtons, radiusButtons.find(b => b.dataset.radius === "all"));
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

        resetToInitialCards();
        applyFilters();
    });

    dongButtons.forEach((button) => {
        button.addEventListener("click", () => {
            selectedDong = button.dataset.dong;
            setActive(dongButtons, button);
            applyFilters();
        });
    });

    if (resetCenterButton) {
        resetCenterButton.addEventListener("click", () => {
            customCenter = null;
            if (customMarker) {
                customMarker.remove();
                customMarker = null;
            }
            resetCenterButton.style.display = "none";
            updateRadiusCircle();
            applyFilters();
        });
    }

    searchBtn.addEventListener("click", async () => {
        await runSearch();
    });

    document.getElementById("keywordInput").addEventListener("keydown", async (e) => {
        if (e.key === "Enter") await runSearch();
    });

    if (window.L) {
        initMap();
    }

function getToken() {
    const match = document.cookie.match(/(?:^|;\s*)accessToken=([^;]+)/);
    return match ? match[1] : (localStorage.getItem("accessToken") || "");
}

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
    }
});

document.addEventListener("DOMContentLoaded", loadScrappedIds);
