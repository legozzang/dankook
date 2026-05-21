(function (window, document) {
    const state = {
        map: null,
        origin: null,
        radiusCircle: null,
        selectedRadius: "all",
        selectedDong: "all",
        context: {type: "default-location"},
        getMarker: () => null
    };

    function radiusButtons() {
        return Array.from(document.querySelectorAll("[data-radius]"));
    }

    function dongButtons() {
        return Array.from(document.querySelectorAll("[data-dong]"));
    }

    function setActive(buttons, activeButton) {
        buttons.forEach((button) => button.classList.toggle("active", button === activeButton));
    }

    function setRadius(radius) {
        state.selectedRadius = radius || "all";
        radiusButtons().forEach((button) => {
            button.classList.toggle("active", button.dataset.radius === state.selectedRadius);
        });
        updateRadiusCircle();
    }

    function updateRadiusCircle() {
        if (!state.map) {
            return;
        }

        if (state.radiusCircle) {
            state.radiusCircle.remove();
            state.radiusCircle = null;
        }

        if (state.selectedRadius === "all" || !state.origin) {
            return;
        }

        state.radiusCircle = L.circle([state.origin.lat, state.origin.lng], {
            radius: Number(state.selectedRadius) * 1000,
            color: "#1769aa",
            fillColor: "#1769aa",
            fillOpacity: 0.12,
            weight: 1
        }).addTo(state.map);
    }

    function setContext(context) {
        state.context = context || {type: "default-location"};
        state.origin = state.context.origin || state.origin;

        if (state.context.type === "user-location" || state.context.type === "default-location") {
            setRadius("5");
        } else {
            setRadius("all");
        }
    }

    function passesPreferredRegion(card) {
        const regions = JobList.compactRegions(state.context.preferredRegions);
        if (state.context.type !== "member" || regions.length === 0) {
            return true;
        }
        return JobList.matchesAnyPreferredRegion(card, regions);
    }

    function passesRadius(card) {
        if (state.selectedRadius === "all") {
            return true;
        }
        if (!state.origin || !JobList.hasExactPoint(card)) {
            return false;
        }
        return JobList.distanceKm(state.origin, JobList.getPoint(card)) <= Number(state.selectedRadius);
    }

    function passesDong(card) {
        return state.selectedDong === "all" || card.dataset.dong === state.selectedDong;
    }

    function isVisible(card) {
        return passesPreferredRegion(card) && passesRadius(card) && passesDong(card);
    }

    function fitVisibleMarkers(markers) {
        if (!state.map) {
            return;
        }
        if (markers.length > 0) {
            state.map.fitBounds(L.featureGroup(markers).getBounds().pad(0.22), {maxZoom: 15});
        } else if (state.origin) {
            state.map.setView([state.origin.lat, state.origin.lng], 14);
        }
    }

    function apply() {
        let count = 0;
        const visibleMarkers = [];

        JobList.getCards().forEach((card) => {
            const visible = isVisible(card);
            const marker = state.getMarker(card.dataset.id);

            card.hidden = !visible;

            if (marker) {
                if (visible) {
                    marker.addTo(state.map);
                    visibleMarkers.push(marker);
                } else {
                    marker.remove();
                }
            }

            if (visible) {
                count += 1;
            }
        });

        JobList.setVisibleCount(count);
        JobList.updateSummary(state.context, count);
        fitVisibleMarkers(visibleMarkers);
        return count;
    }

    function init(options) {
        state.map = options.map;
        state.origin = options.origin;
        state.getMarker = options.getMarker;

        radiusButtons().forEach((button) => {
            button.addEventListener("click", () => {
                state.selectedRadius = button.dataset.radius;
                setActive(radiusButtons(), button);
                updateRadiusCircle();
                apply();
            });
        });

        dongButtons().forEach((button) => {
            button.addEventListener("click", () => {
                state.selectedDong = button.dataset.dong;
                setActive(dongButtons(), button);
                apply();
            });
        });
    }

    window.JobFilters = {
        init,
        setContext,
        setRadius,
        apply,
        updateRadiusCircle
    };
})(window, document);
