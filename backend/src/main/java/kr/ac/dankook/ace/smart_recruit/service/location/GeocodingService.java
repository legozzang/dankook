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
}
