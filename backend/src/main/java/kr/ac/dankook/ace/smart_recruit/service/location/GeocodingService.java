package kr.ac.dankook.ace.smart_recruit.service.location;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeocodingService {

    private static final Pattern KAKAO_X_PATTERN = Pattern.compile("\"x\"\\s*:\\s*\"([0-9.\\-]+)\"");
    private static final Pattern KAKAO_Y_PATTERN = Pattern.compile("\"y\"\\s*:\\s*\"([0-9.\\-]+)\"");
    private static final Pattern NOMINATIM_LAT_PATTERN = Pattern.compile("\"lat\"\\s*:\\s*\"([0-9.\\-]+)\"");
    private static final Pattern NOMINATIM_LON_PATTERN = Pattern.compile("\"lon\"\\s*:\\s*\"([0-9.\\-]+)\"");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private final String kakaoRestApiKey;

    public GeocodingService(@Value("${geocoding.kakao.rest-api-key:}") String kakaoRestApiKey) {
        this.kakaoRestApiKey = kakaoRestApiKey;
    }

    public Optional<Coordinate> geocode(String address) {
        if (isBlank(address)) {
            return Optional.empty();
        }

        List<String> queries = geocodingQueries(address);

        for (String query : queries) {
            if (!isBlank(kakaoRestApiKey)) {
                Optional<Coordinate> kakaoResult = geocodeWithKakao(query);
                if (kakaoResult.isPresent()) {
                    return kakaoResult;
                }
            }
        }

        for (String query : queries) {
            Optional<Coordinate> nominatimResult = geocodeWithNominatim(query);
            if (nominatimResult.isPresent()) {
                return nominatimResult;
            }
        }

        return Optional.empty();
    }

    public Optional<ReverseGeocodeResult> reverseGeocode(double lat, double lng) {
        if (!isBlank(kakaoRestApiKey)) {
            Optional<ReverseGeocodeResult> kakaoResult = reverseGeocodeWithKakao(lat, lng);
            if (kakaoResult.isPresent()) {
                return kakaoResult;
            }
        }
        return reverseGeocodeWithNominatim(lat, lng);
    }

    private Optional<Coordinate> geocodeWithKakao(String address) {
        String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
        String url = "https://dapi.kakao.com/v2/local/search/address.json?query=" + encodedAddress;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                .GET()
                .build();

        return send(request).flatMap(body -> parseCoordinate(body, KAKAO_Y_PATTERN, KAKAO_X_PATTERN));
    }

    private Optional<Coordinate> geocodeWithNominatim(String address) {
        String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
        String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=kr&q=" + encodedAddress;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "SmartRecruit/1.0")
                .GET()
                .build();

        return send(request).flatMap(body -> parseCoordinate(body, NOMINATIM_LAT_PATTERN, NOMINATIM_LON_PATTERN));
    }

    private Optional<ReverseGeocodeResult> reverseGeocodeWithKakao(double lat, double lng) {
        String addressUrl = "https://dapi.kakao.com/v2/local/geo/coord2address.json?x="
                + lng + "&y=" + lat + "&input_coord=WGS84";
        HttpRequest addressRequest = HttpRequest.newBuilder(URI.create(addressUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                .GET()
                .build();
        Optional<ReverseGeocodeResult> addressResult = send(addressRequest)
                .flatMap(body -> parseKakaoReverseGeocode(body, lat, lng));
        if (addressResult.isPresent()) {
            return addressResult;
        }

        String regionUrl = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x="
                + lng + "&y=" + lat + "&input_coord=WGS84";
        HttpRequest regionRequest = HttpRequest.newBuilder(URI.create(regionUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                .GET()
                .build();

        return send(regionRequest).flatMap(body -> parseKakaoRegioncode(body, lat, lng));
    }

    private Optional<ReverseGeocodeResult> reverseGeocodeWithNominatim(double lat, double lng) {
        String url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat="
                + lat + "&lon=" + lng + "&accept-language=ko";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "SmartRecruit/1.0")
                .GET()
                .build();

        return send(request).flatMap(body -> parseNominatimReverseGeocode(body, lat, lng));
    }

    private Optional<String> send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Optional.of(response.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return Optional.empty();
    }

    private Optional<Coordinate> parseCoordinate(String body, Pattern latitudePattern, Pattern longitudePattern) {
        Matcher latitudeMatcher = latitudePattern.matcher(body);
        Matcher longitudeMatcher = longitudePattern.matcher(body);
        if (latitudeMatcher.find() && longitudeMatcher.find()) {
            return Optional.of(new Coordinate(
                    Double.parseDouble(latitudeMatcher.group(1)),
                    Double.parseDouble(longitudeMatcher.group(1))
            ));
        }
        return Optional.empty();
    }

    private Optional<ReverseGeocodeResult> parseKakaoReverseGeocode(String body, double lat, double lng) {
        String roadAddress = extractObject(body, "road_address").orElse("");
        String address = extractObject(body, "address").orElse("");
        if (!isBlank(roadAddress)) {
            String sido = extractJsonString(roadAddress, "region_1depth_name").orElse("");
            String sigungu = extractJsonString(address, "region_2depth_name")
                    .or(() -> extractJsonString(roadAddress, "region_2depth_name")
                            .map(value -> value.contains(" ") ? value.split("\\s+")[0] : value))
                    .orElse("");
            String detailAddress = kakaoRoadDetail(roadAddress);
            return reverseResult(sido, sigungu, detailAddress, lat, lng);
        }

        if (!isBlank(address)) {
            String sido = extractJsonString(address, "region_1depth_name").orElse("");
            String sigungu = extractJsonString(address, "region_2depth_name").orElse("");
            String detailAddress = extractJsonString(address, "address_name").orElse("");
            if (isBlank(sido) || isBlank(sigungu)) {
                Optional<String[]> parsed = parseSidoSigunguFromAddressName(detailAddress);
                if (parsed.isPresent()) {
                    sido = isBlank(sido) ? parsed.get()[0] : sido;
                    sigungu = isBlank(sigungu) ? parsed.get()[1] : sigungu;
                }
            }
            return reverseResult(sido, sigungu, detailAddress, lat, lng);
        }

        return Optional.empty();
    }

    private Optional<ReverseGeocodeResult> parseKakaoRegioncode(String body, double lat, double lng) {
        String sido = extractJsonString(body, "region_1depth_name").orElse("");
        String sigungu = extractJsonString(body, "region_2depth_name").orElse("");
        String dong = extractJsonString(body, "region_3depth_name").orElse("");
        String detailAddress = isBlank(dong)
                ? joinAddressParts(sido, sigungu)
                : joinAddressParts(joinAddressParts(sido, sigungu), dong);
        return reverseResult(sido, sigungu, detailAddress, lat, lng);
    }

    private Optional<ReverseGeocodeResult> parseNominatimReverseGeocode(String body, double lat, double lng) {
        String address = extractObject(body, "address").orElse("");
        if (isBlank(address)) {
            return Optional.empty();
        }

        String sido = extractJsonString(address, "state").orElse("");
        String sigungu = firstNonBlank(
                extractJsonString(address, "city").orElse(""),
                extractJsonString(address, "county").orElse(""),
                extractJsonString(address, "town").orElse(""),
                extractJsonString(address, "village").orElse("")
        );
        String detailAddress = joinAddressParts(
                extractJsonString(address, "road").orElse(""),
                extractJsonString(address, "house_number").orElse("")
        );
        return reverseResult(sido, sigungu, detailAddress, lat, lng);
    }

    private Optional<ReverseGeocodeResult> reverseResult(
            String sido,
            String sigungu,
            String detailAddress,
            double lat,
            double lng
    ) {
        if (isBlank(sido) || isBlank(sigungu)) {
            return Optional.empty();
        }
        return Optional.of(new ReverseGeocodeResult(
                sido.trim(),
                sigungu.trim(),
                detailAddress == null ? "" : detailAddress.trim(),
                lat,
                lng
        ));
    }

    private String kakaoRoadDetail(String roadAddress) {
        String roadName = extractJsonString(roadAddress, "road_name").orElse("");
        String mainBuildingNo = extractJsonString(roadAddress, "main_building_no").orElse("");
        String subBuildingNo = extractJsonString(roadAddress, "sub_building_no").orElse("");
        String buildingName = extractJsonString(roadAddress, "building_name").orElse("");

        String detail = joinAddressParts(roadName, mainBuildingNo);
        if (!isBlank(subBuildingNo)) {
            detail += "-" + subBuildingNo.trim();
        }
        if (!isBlank(buildingName)) {
            detail += " (" + buildingName.trim() + ")";
        }
        return detail.trim();
    }

    private Optional<String> extractJsonString(String body, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return Optional.of(unescapeJsonString(matcher.group(1)));
        }
        return Optional.empty();
    }

    private Optional<String> extractObject(String body, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*");
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            int index = matcher.end();
            if (body.startsWith("null", index)) {
                continue;
            }
            if (index >= body.length() || body.charAt(index) != '{') {
                continue;
            }
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = index; i < body.length(); i++) {
                char ch = body.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (ch == '"') {
                    inString = true;
                } else if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        return Optional.of(body.substring(index, i + 1));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String joinAddressParts(String first, String second) {
        if (isBlank(first)) {
            return second == null ? "" : second.trim();
        }
        if (isBlank(second)) {
            return first.trim();
        }
        return first.trim() + " " + second.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Optional<String[]> parseSidoSigunguFromAddressName(String addressName) {
        if (isBlank(addressName)) {
            return Optional.empty();
        }
        String[] parts = addressName.trim().split("\\s+");
        if (parts.length < 2) {
            return Optional.empty();
        }

        String sido = parts[0];
        String sigungu;
        if (parts.length >= 3 && (parts[2].endsWith("구") || parts[2].endsWith("군"))) {
            sigungu = parts[1] + " " + parts[2];
        } else if (parts[1].endsWith("시") || parts[1].endsWith("군") || parts[1].endsWith("구")) {
            sigungu = parts[1];
        } else {
            return Optional.empty();
        }

        return Optional.of(new String[]{sido, sigungu});
    }

    private String unescapeJsonString(String value) {
        String unescaped = value
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");
        Matcher matcher = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(unescaped);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Character.toString((char) Integer.parseInt(matcher.group(1), 16)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private List<String> geocodingQueries(String address) {
        String normalized = normalizeAddress(address);
        Set<String> queries = new LinkedHashSet<>();
        addIfPresent(queries, normalized);

        String withoutFloor = normalized.replaceAll("\\s+\\d+\\s*층.*$", "").trim();
        addIfPresent(queries, withoutFloor);

        String roadOrDongAddress = withoutFloor.replaceAll("\\s+[^\\s]*(빌딩|타워|센터|상가|점|호)$", "").trim();
        addIfPresent(queries, roadOrDongAddress);

        String[] parts = roadOrDongAddress.split("\\s+");
        if (parts.length > 3) {
            addIfPresent(queries, String.join(" ", List.of(parts).subList(0, Math.min(parts.length, 5))));
        }

        return new ArrayList<>(queries);
    }

    private String normalizeAddress(String address) {
        return address.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\[[^]]*]", " ")
                .replace("지도보기", " ")
                .replace("상세보기", " ")
                .replace("근무지", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void addIfPresent(Set<String> values, String value) {
        if (!isBlank(value)) {
            values.add(value.trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record Coordinate(double latitude, double longitude) {}

    public record ReverseGeocodeResult(
            String sido,
            String sigungu,
            String detailAddress,
            double latitude,
            double longitude
    ) {}
}
