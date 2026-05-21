(function (window, document) {
    const markers = new Map();
    let map = null;

    function statusLabel(card) {
        return card.dataset.status === "CLOSED" ? "마감" : "진행중";
    }

    function jobTypeLabel(card) {
        return (card.dataset.jobType || "").trim() || "직종 미등록";
    }

    function markerSymbol(card) {
        const label = jobTypeLabel(card).replace(/\s+/g, "");
        return label ? label.slice(0, 1) : "일";
    }

    function markerIcon(card) {
        const level = card.dataset.recommendationLevel || "medium";
        const closed = card.dataset.status === "CLOSED" ? "marker-closed" : "";
        const soft = card.dataset.exactLocation === "true" ? "" : "marker-soft";

        return L.divIcon({
            className: `job-marker marker-${level} ${closed} ${soft}`,
            html: `<div class="marker-pin"><span>${JobList.escapeHtml(markerSymbol(card))}</span></div>`,
            iconSize: [34, 34],
            iconAnchor: [17, 34],
            popupAnchor: [0, -28]
        });
    }

    function popupHtml(card) {
        const level = card.dataset.recommendationLevel || "medium";
        const statusClass = card.dataset.status === "CLOSED" ? "closed" : "open";
        const reason = card.dataset.contextReason || card.dataset.aiReason || "공고 조건을 바탕으로 추천합니다.";

        return `
            <p class="popup-title">${JobList.escapeHtml(card.dataset.title)}</p>
            <div class="popup-tags">
                <span class="job-tag recommendation-tag ${level}">추천도 ${JobList.escapeHtml(card.dataset.recommendationScore || "")}</span>
                <span class="job-tag">${JobList.escapeHtml(jobTypeLabel(card))}</span>
                <span class="job-tag status-tag ${statusClass}">${statusLabel(card)}</span>
            </div>
            <p class="popup-meta">
                <span><strong>회사</strong> ${JobList.escapeHtml(card.dataset.company)}</span>
                <span><strong>지역</strong> ${JobList.escapeHtml(card.dataset.location)}</span>
                <span><strong>급여</strong> ${JobList.escapeHtml(card.dataset.salary)}</span>
                <span><strong>거리</strong> ${JobList.escapeHtml(card.dataset.distanceLabel || "거리 정보 없음")}</span>
                <span><strong>마감</strong> ${JobList.escapeHtml(card.dataset.deadline)}</span>
            </p>
            <p class="popup-reason"><strong>AI 추천 사유</strong><br>${JobList.escapeHtml(reason)}</p>
        `;
    }

    function refreshMarkers() {
        markers.forEach((marker, id) => {
            const card = JobList.getCards().find((item) => item.dataset.id === id);
            if (!card) {
                return;
            }
            marker.setIcon(markerIcon(card));
            marker.setPopupContent(popupHtml(card));
        });
    }

    function initMap(origin) {
        map = L.map("jobMap", {scrollWheelZoom: true}).setView([origin.lat, origin.lng], 14);
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 19,
            attribution: "&copy; OpenStreetMap"
        }).addTo(map);

        JobList.getCards().forEach((card) => {
            if (!JobList.hasExactPoint(card)) {
                return;
            }

            const point = JobList.getPoint(card);
            const marker = L.marker([point.lat, point.lng], {
                icon: markerIcon(card)
            });

            marker.bindPopup(popupHtml(card));
            marker.on("click", () => JobList.selectCard(card));
            markers.set(card.dataset.id, marker);
            marker.addTo(map);
        });
    }

    function bindCardClicks() {
        JobList.getCards().forEach((card) => {
            card.addEventListener("click", (event) => {
                if (event.target.closest("a")) {
                    return;
                }

                JobList.selectCard(card);
                const marker = markers.get(card.dataset.id);
                if (marker) {
                    marker.openPopup();
                    map.setView(marker.getLatLng(), 15);
                }
            });
        });
    }

    async function boot() {
        const config = window.JobMapConfig || {};
        const defaultOrigin = config.defaultOrigin || {lat: 37.3216, lng: 127.1267};
        const defaultRegionLabel = config.defaultRegionLabel || "단국대학교 죽전캠퍼스";

        if (!window.L) {
            return;
        }

        JobList.updateDistances(defaultOrigin);
        initMap(defaultOrigin);
        bindCardClicks();

        JobFilters.init({
            map,
            origin: defaultOrigin,
            getMarker: (id) => markers.get(id)
        });

        const context = await UserLocation.resolveInitialContext(defaultOrigin, defaultRegionLabel);
        JobList.updateDistances(context.origin);
        JobList.applyRecommendationContext(context);
        JobList.reorderCards(context);
        refreshMarkers();
        JobFilters.setContext(context);
        JobFilters.apply();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", boot);
    } else {
        boot();
    }
})(window, document);
