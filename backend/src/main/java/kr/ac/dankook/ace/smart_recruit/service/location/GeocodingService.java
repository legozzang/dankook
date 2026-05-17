package kr.ac.dankook.ace.smart_recruit.service.location;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
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

        if (!isBlank(kakaoRestApiKey)) {
            Optional<Coordinate> kakaoResult = geocodeWithKakao(address);
            if (kakaoResult.isPresent()) {
                return kakaoResult;
            }
        }

        return geocodeWithNominatim(address);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record Coordinate(double latitude, double longitude) {}
}
