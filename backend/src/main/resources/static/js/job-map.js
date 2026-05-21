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
            const preferredMajor = profile.preferredJobTypeMajor ?? profile.preferred_job_type_major;
            const preferredMid = profile.preferredJobTypeMid ?? profile.preferred_job_type_mid;

            if (desiredSido) {
                selectedSido = desiredSido;
                filterSido.value = desiredSido;
                fillSelect(filterSigungu, "시/군/구 전체", SIGUNGU_BY_SIDO[selectedSido] || []);
                filterSigungu.disabled = false;
                if (desiredSigungu) {
                    selectedSigungu = desiredSigungu;
                    filterSigungu.value = desiredSigungu;
                }
            }

            if (preferredMajor) {
                selectedJobMajor = preferredMajor;
                filterJobMajor.value = preferredMajor;
                fillSelect(filterJobMid, "중분류 전체", JOB_MID_BY_MAJOR[selectedJobMajor] || []);
                filterJobMid.disabled = false;
                if (preferredMid) {
                    selectedJobMid = preferredMid;
                    filterJobMid.value = preferredMid;
                }
            }

            if (desiredSido || preferredMajor) {
                await runSearch();
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
    let customCenter = null;
    let customMarker = null;
    let map;
    let radiusCircle = null;
    const markers = new Map();

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
        if (selectedRadius !== "all") {
            const center = searchCenter();
            params.lat = center.lat;
            params.lng = center.lng;
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
            lat: field(job, "latitude", "latitude", 0),
            lng: field(job, "longitude", "longitude", 0),
            exactLocation: field(job, "exactLocation", "exact_location", false)
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
                     data-lat="${escapeHtml(data.lat)}"
                     data-lng="${escapeHtml(data.lng)}"
                     data-exact-location="${exactLocation}">
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

                <a class="detail-link" href="/jobpostings/${escapeHtml(data.id)}">상세 보기</a>
            </article>
        `;
    }

    function clearMarkers() {
        markers.forEach((marker) => marker.remove());
        markers.clear();
    }

    function createMarker(card) {
        const isExactLocation = card.dataset.exactLocation === "true";
        const lat = Number(card.dataset.lat);
        const lng = Number(card.dataset.lng);

        if (!isExactLocation || Number.isNaN(lat) || Number.isNaN(lng)) {
            return null;
        }

        const marker = L.marker([lat, lng]);
        marker.bindPopup(`
            <p class="popup-title">${escapeHtml(card.dataset.title)}</p>
            <p class="popup-meta">
                ${escapeHtml(card.dataset.company)}<br>
                ${escapeHtml(card.dataset.location)}<br>
                ${escapeHtml(card.dataset.salary)}<br>
                ${escapeHtml(card.dataset.deadline)}<br>
                실제 위치
            </p>
        `);
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
                if (event.target.closest("a")) {
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

            if (allFiltersDefault && resultLimit.value === "10") {
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

    function initMap() {
        map = L.map("jobMap", {scrollWheelZoom: true}).setView([campus.lat, campus.lng], 14);
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 19,
            attribution: "&copy; OpenStreetMap"
        }).addTo(map);

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

    if (window.L) {
        initMap();
    }
