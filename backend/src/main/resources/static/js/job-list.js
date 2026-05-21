(function (window, document) {
    const CARD_SELECTOR = ".job-card";
    const DEFAULT_REASON = "공고의 근무 조건과 지역 정보가 확인되어 추천합니다.";

    function getCards() {
        return Array.from(document.querySelectorAll(CARD_SELECTOR));
    }

    function toNumber(value) {
        const number = Number(value);
        return Number.isFinite(number) ? number : null;
    }

    function getPoint(card) {
        const lat = toNumber(card.dataset.lat);
        const lng = toNumber(card.dataset.lng);
        if (lat === null || lng === null) {
            return null;
        }
        return {lat, lng};
    }

    function hasExactPoint(card) {
        return card.dataset.exactLocation === "true" && getPoint(card) !== null;
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

    function formatDistance(km) {
        if (!Number.isFinite(km)) {
            return "거리 정보 없음";
        }
        if (km < 1) {
            return `${Math.round(km * 1000)}m`;
        }
        return `${km.toFixed(1)}km`;
    }

    function updateDistances(origin) {
        getCards().forEach((card) => {
            const value = card.querySelector(".distance-value");
            if (!origin || !hasExactPoint(card)) {
                card.dataset.distanceKm = "";
                card.dataset.distanceLabel = card.dataset.exactLocation === "true" ? "거리 계산 전" : "좌표 미등록";
            } else {
                const km = distanceKm(origin, getPoint(card));
                card.dataset.distanceKm = String(km);
                card.dataset.distanceLabel = formatDistance(km);
            }
            if (value) {
                value.textContent = card.dataset.distanceLabel;
            }
        });
    }

    function normalize(value) {
        return (value || "").trim();
    }

    function includesRegion(source, target) {
        const left = normalize(source);
        const right = normalize(target);
        return !!left && !!right && (left === right || left.includes(right) || right.includes(left));
    }

    function compactRegions(regions) {
        return (regions || []).filter((region) => (
            normalize(region.sido) || normalize(region.sigungu) || normalize(region.dong)
        ));
    }

    function matchesPreferredRegion(card, region) {
        if (!region) {
            return false;
        }

        const sido = normalize(region.sido);
        const sigungu = normalize(region.sigungu);
        const dong = normalize(region.dong);

        if (sido && normalize(card.dataset.regionSido) !== sido) {
            return false;
        }
        if (sigungu && normalize(card.dataset.regionSigungu) !== sigungu) {
            return false;
        }
        if (dong && !includesRegion(card.dataset.dong || card.dataset.location, dong)) {
            return false;
        }
        return !!(sido || sigungu || dong);
    }

    function matchesAnyPreferredRegion(card, regions) {
        const preferences = compactRegions(regions);
        return preferences.some((region) => matchesPreferredRegion(card, region));
    }

    function preferredRegionLabel(regions) {
        const labels = compactRegions(regions).map((region) => (
            [region.sido, region.sigungu, region.dong].map(normalize).filter(Boolean).join(" ")
        )).filter(Boolean);
        return labels.length ? labels.join(" / ") : "";
    }

    function clampScore(score) {
        return Math.max(20, Math.min(99, Math.round(score)));
    }

    function levelFor(score) {
        if (score >= 80) {
            return "high";
        }
        if (score >= 60) {
            return "medium";
        }
        return "low";
    }

    function setRecommendation(card, score, reason) {
        const finalScore = clampScore(score);
        card.dataset.recommendationScore = String(finalScore);
        card.dataset.recommendationLevel = levelFor(finalScore);
        card.dataset.contextReason = reason || card.dataset.aiReason || DEFAULT_REASON;

        const scoreElement = card.querySelector(".recommendation-score");
        const reasonElement = card.querySelector(".recommendation-reason-text");
        const tag = card.querySelector(".recommendation-tag");

        if (scoreElement) {
            scoreElement.textContent = String(finalScore);
        }
        if (reasonElement) {
            reasonElement.textContent = card.dataset.contextReason;
        }
        if (tag) {
            tag.classList.remove("high", "medium", "low");
            tag.classList.add(card.dataset.recommendationLevel);
        }
    }

    function reasonWithDistance(card, prefix) {
        const base = card.dataset.aiReason || DEFAULT_REASON;
        const distance = card.dataset.distanceLabel || "거리 정보 없음";
        return `${prefix}에서 ${distance} 거리에 있는 공고예요. ${base}`;
    }

    function applyRecommendationContext(context) {
        const cards = getCards();
        const regions = compactRegions(context.preferredRegions);

        cards.forEach((card) => {
            const baseScore = toNumber(card.dataset.baseScore) || 50;
            const status = normalize(card.dataset.status);
            let score = baseScore;
            let reason = card.dataset.aiReason || DEFAULT_REASON;

            if (context.type === "member" && regions.length > 0) {
                if (matchesAnyPreferredRegion(card, regions)) {
                    score += 25;
                    reason = `선호 지역과 일치하는 공고예요. ${reason}`;
                } else {
                    score -= 12;
                }
            } else if ((context.type === "user-location" || context.type === "default-location") && card.dataset.distanceKm) {
                const distance = toNumber(card.dataset.distanceKm);
                if (distance !== null && distance <= 1) {
                    score += 25;
                } else if (distance !== null && distance <= 3) {
                    score += 18;
                } else if (distance !== null && distance <= 5) {
                    score += 10;
                }
                reason = reasonWithDistance(card, context.type === "user-location" ? "현재 위치" : "기본 지역");
            } else if (context.type === "member") {
                score += 8;
                reason = `사용자 정보를 바탕으로 확인한 공고예요. ${reason}`;
            }

            if (status === "CLOSED") {
                score = Math.min(score, 38);
                reason = `마감된 공고입니다. ${reason}`;
            }

            setRecommendation(card, score, reason);
        });
    }

    function distanceSortValue(card) {
        const distance = toNumber(card.dataset.distanceKm);
        return distance === null ? Number.POSITIVE_INFINITY : distance;
    }

    function reorderCards(context) {
        const container = document.getElementById("jobCards");
        if (!container) {
            return;
        }

        const regions = compactRegions(context.preferredRegions);
        getCards()
            .sort((a, b) => {
                const aPreferred = matchesAnyPreferredRegion(a, regions) ? 1 : 0;
                const bPreferred = matchesAnyPreferredRegion(b, regions) ? 1 : 0;
                if (aPreferred !== bPreferred) {
                    return bPreferred - aPreferred;
                }

                const scoreDiff = (toNumber(b.dataset.recommendationScore) || 0) -
                    (toNumber(a.dataset.recommendationScore) || 0);
                if (scoreDiff !== 0) {
                    return scoreDiff;
                }

                const distanceDiff = distanceSortValue(a) - distanceSortValue(b);
                if (distanceDiff !== 0) {
                    return distanceDiff;
                }

                return normalize(a.dataset.title).localeCompare(normalize(b.dataset.title), "ko");
            })
            .forEach((card) => container.appendChild(card));
    }

    function selectCard(card) {
        getCards().forEach((item) => item.classList.toggle("active", item === card));
        card.scrollIntoView({block: "nearest"});
    }

    function setVisibleCount(count) {
        const visibleCount = document.getElementById("visibleCount");
        const filteredEmpty = document.getElementById("filteredEmpty");

        if (visibleCount) {
            visibleCount.textContent = String(count);
        }
        if (filteredEmpty) {
            filteredEmpty.hidden = count > 0 || getCards().length === 0;
        }
    }

    function updateSummary(context, count) {
        const message = document.getElementById("recommendationMessage");
        const label = document.getElementById("locationModeLabel");
        const regionLabel = preferredRegionLabel(context.preferredRegions);

        if (message) {
            if (context.type === "member" && regionLabel) {
                message.textContent = `선호 지역을 기준으로 추천 공고 ${count}개를 찾았어요!`;
            } else if (context.type === "member") {
                message.textContent = `사용자 정보를 바탕으로 ${count}개의 공고를 찾았어요!`;
            } else if (context.type === "user-location") {
                message.textContent = `현재 위치 주변 공고 ${count}개를 찾았어요!`;
            } else {
                message.textContent = `기본 지역 주변 공고 ${count}개를 찾았어요!`;
            }
        }

        if (label) {
            if (context.type === "member" && regionLabel) {
                label.textContent = `적용 지역: ${regionLabel}`;
            } else if (context.type === "user-location") {
                label.textContent = "기준 위치: 현재 위치";
            } else {
                label.textContent = `기준 위치: ${context.defaultRegionLabel || "단국대학교 죽전캠퍼스"}`;
            }
        }
    }

    function escapeHtml(value) {
        return String(value || "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    window.JobList = {
        getCards,
        getPoint,
        hasExactPoint,
        distanceKm,
        formatDistance,
        updateDistances,
        compactRegions,
        matchesAnyPreferredRegion,
        preferredRegionLabel,
        applyRecommendationContext,
        reorderCards,
        selectCard,
        setVisibleCount,
        updateSummary,
        escapeHtml
    };
})(window, document);
