(function (window) {
    function getToken() {
        const match = document.cookie.match(/(?:^|;\s*)accessToken=([^;]+)/);
        return match ? match[1] : (localStorage.getItem("accessToken") || "");
    }

    function authHeaders() {
        const headers = {"Content-Type": "application/json"};
        const token = getToken();
        if (token && token !== "undefined" && token !== "null") {
            headers.Authorization = `Bearer ${token}`;
        }
        return headers;
    }

    async function fetchMemberInfo() {
        const token = getToken();
        if (!token || token === "undefined" || token === "null") {
            return null;
        }

        try {
            const response = await fetch("/auth/members/me", {
                method: "GET",
                headers: authHeaders(),
                credentials: "same-origin"
            });
            if (!response.ok) {
                return null;
            }
            return await response.json();
        } catch (error) {
            console.warn("회원 선호 정보 조회 실패:", error);
            return null;
        }
    }

    function regionFrom(data, index) {
        if (index === 1) {
            return {
                sido: data.desired_region_sido || "",
                sigungu: data.desired_region_sigungu || "",
                dong: data.desired_region_dong || ""
            };
        }

        return {
            sido: data[`desired_region${index}_sido`] || "",
            sigungu: data[`desired_region${index}_sigungu`] || "",
            dong: data[`desired_region${index}_dong`] || ""
        };
    }

    function preferredRegions(data) {
        if (!data) {
            return [];
        }
        return JobList.compactRegions([
            regionFrom(data, 1),
            regionFrom(data, 2),
            regionFrom(data, 3)
        ]);
    }

    function browserLocation() {
        return new Promise((resolve) => {
            if (!navigator.geolocation) {
                resolve(null);
                return;
            }

            navigator.geolocation.getCurrentPosition(
                (position) => resolve({
                    lat: position.coords.latitude,
                    lng: position.coords.longitude
                }),
                () => resolve(null),
                {
                    enableHighAccuracy: false,
                    timeout: 5000,
                    maximumAge: 300000
                }
            );
        });
    }

    async function resolveInitialContext(defaultOrigin, defaultRegionLabel) {
        const member = await fetchMemberInfo();
        const regions = preferredRegions(member);

        if (member && regions.length > 0) {
            return {
                type: "member",
                origin: defaultOrigin,
                defaultRegionLabel,
                preferredRegions: regions,
                member
            };
        }

        const current = await browserLocation();
        if (current) {
            return {
                type: "user-location",
                origin: current,
                defaultRegionLabel,
                preferredRegions: [],
                member
            };
        }

        return {
            type: "default-location",
            origin: defaultOrigin,
            defaultRegionLabel,
            preferredRegions: [],
            member
        };
    }

    window.UserLocation = {
        resolveInitialContext
    };
})(window);
