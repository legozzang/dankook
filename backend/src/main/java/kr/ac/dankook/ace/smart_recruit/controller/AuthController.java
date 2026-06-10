package kr.ac.dankook.ace.smart_recruit.controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.core.io.ClassPathResource;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.validation.Valid;
import kr.ac.dankook.ace.smart_recruit.dto.*;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import kr.ac.dankook.ace.smart_recruit.service.AuthService;
import kr.ac.dankook.ace.smart_recruit.service.location.GeocodingService.Coordinate;
import kr.ac.dankook.ace.smart_recruit.service.location.GeocodingService.ReverseGeocodeResult;
import kr.ac.dankook.ace.smart_recruit.util.StringUtils;
import lombok.RequiredArgsConstructor;



@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JobPostingRepository jobPostingRepository;

    @GetMapping("/login")
    public String loginPage() {
        return "login"; // templates/login.html
    }

    @GetMapping("/signup")
    public String signUpPage(Model model) {
        model.addAttribute("sigunguBySidoJson", buildSigunguBySidoJson());
        return "signup"; // templates/signup.html
    }

    @GetMapping("/mypage")
    public String myPage() {
        return "mypage"; // mypage.html
    }

    @GetMapping("/members/me")
    @ResponseBody
    public ResponseEntity<MemberInfoResponse> getMemberInfo(@AuthenticationPrincipal User user) {
        MemberInfoResponse response = authService.getMemberInfo(user.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/edit-profile")
    public String editProfilePage(Model model){
        model.addAttribute("kscoHierarchyJson", buildKscoHierarchyJson());
        model.addAttribute("sigunguBySidoJson", buildSigunguBySidoJson());
        return "edit-profile"; // templates/edit-profile.html
    }

    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        ResponseCookie cookie = ResponseCookie.from("accessToken", response.getAccessToken())
                .httpOnly(true)
                .path("/")
                .maxAge(60 * 60 * 24)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/signup")
    @ResponseBody
    public ResponseEntity<Long> signUp(@Valid @RequestBody SignUpRequest request){
        // 회원가입 성공 시 201 상태코드와 함께 ID를 리턴
        Long memberId = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(memberId);
    }

    /**
     * 거주지 상세주소 지오코딩 미리보기.
     * 변환 성공 시 좌표를 반환, 실패 시 GlobalExceptionHandler가 400 + 메시지로 응답.
     * (회원가입 화면에서 호출하므로 SecurityConfig에서 permitAll 처리)
     */
    @PostMapping("/geocode/preview")
    @ResponseBody
    public ResponseEntity<Coordinate> previewGeocode(@RequestBody GeocodePreviewRequest request){
        Coordinate coordinate = authService.previewHomeLocation(
                request.getSido(), request.getSigungu(), request.getDetail());
        return ResponseEntity.ok(coordinate);
    }

    @GetMapping("/geocode/reverse")
    @ResponseBody
    public ResponseEntity<ReverseGeocodeResult> reverseGeocode(
            @RequestParam double lat,
            @RequestParam double lng
    ) {
        return ResponseEntity.ok(authService.previewReverseGeocode(lat, lng));
    }

    @DeleteMapping("/delete/me")
    @ResponseBody
    public ResponseEntity<Void> deleteMember(@AuthenticationPrincipal User user){
        authService.deleteMember(user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/update/me")
    @ResponseBody
    public ResponseEntity<Void> updateMember(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateRequest request){
        authService.updateMember(user.getUsername(), request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/me/gemini")
    @ResponseBody
    public ResponseEntity<Void> updateGeminiSettings(
            @AuthenticationPrincipal User user,
            @RequestBody GeminiSettingsRequest request
    ) {
        authService.updateGeminiSettings(user.getUsername(), request);
        return ResponseEntity.ok().build();
    }

    private String buildKscoHierarchyJson() {
        try {
            ClassPathResource resource = new ClassPathResource("ksco_2025_reverse.json");
            if (!resource.exists()) {
                return "{}";
            }

            String json = new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Map<String, Map<String, Map<String, Boolean>>>> hierarchy = new TreeMap<>();

            Pattern pattern = Pattern.compile(
                    "\\\"([^\\\"]+)\\\"\\s*:\\s*\\{\\s*\\\"소분류\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*\\\"중분류\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*\\\"대분류\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"\\s*\\}"
            );
            Matcher matcher = pattern.matcher(json);
            while (matcher.find()) {
                String detail = matcher.group(1);
                String minor = matcher.group(2);
                String mid = matcher.group(3);
                String major = matcher.group(4);

                if (StringUtils.isBlank(major) || StringUtils.isBlank(mid) || StringUtils.isBlank(minor) || StringUtils.isBlank(detail)) {
                    continue;
                }

                hierarchy
                        .computeIfAbsent(major, ignored -> new TreeMap<>())
                        .computeIfAbsent(mid, ignored -> new TreeMap<>())
                        .computeIfAbsent(minor, ignored -> new TreeMap<>())
                        .put(detail, true);
            }

            return toJson(hierarchy);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildSigunguBySidoJson() {
        try {
            List<Object[]> rows = jobPostingRepository.countGroupByRegion();
            TreeMap<String, TreeSet<String>> grouped = new TreeMap<>();

            for (Object[] row : rows) {
                String sido = row[0] != null ? row[0].toString().trim() : "";
                String sigungu = row[1] != null ? row[1].toString().trim() : "";
                if (!sido.isBlank() && !sigungu.isBlank()) {
                    grouped.computeIfAbsent(sido, ignored -> new TreeSet<>()).add(sigungu);
                }
            }

            StringBuilder sb = new StringBuilder("{");
            boolean firstSido = true;
            for (Map.Entry<String, TreeSet<String>> entry : grouped.entrySet()) {
                if (!firstSido) sb.append(',');
                firstSido = false;
                sb.append('"').append(escapeJson(entry.getKey())).append("\":[");

                boolean firstSigungu = true;
                for (String sigungu : entry.getValue()) {
                    if (!firstSigungu) sb.append(',');
                    firstSigungu = false;
                    sb.append('"').append(escapeJson(sigungu)).append('"');
                }
                sb.append(']');
            }
            sb.append('}');
            return sb.toString();
        } catch (Exception e) {
            return "{}";
        }
    }


    private String toJson(Map<String, Map<String, Map<String, Map<String, Boolean>>>> hierarchy) {
        StringBuilder sb = new StringBuilder("{");
        boolean firstMajor = true;
        for (Map.Entry<String, Map<String, Map<String, Map<String, Boolean>>>> major : hierarchy.entrySet()) {
            if (!firstMajor) sb.append(',');
            firstMajor = false;
            sb.append('"').append(escapeJson(major.getKey())).append("\":{");
            boolean firstMid = true;
            for (Map.Entry<String, Map<String, Map<String, Boolean>>> mid : major.getValue().entrySet()) {
                if (!firstMid) sb.append(',');
                firstMid = false;
                sb.append('"').append(escapeJson(mid.getKey())).append("\":{");
                boolean firstMinor = true;
                for (Map.Entry<String, Map<String, Boolean>> minor : mid.getValue().entrySet()) {
                    if (!firstMinor) sb.append(',');
                    firstMinor = false;
                    sb.append('"').append(escapeJson(minor.getKey())).append("\":{");
                    boolean firstDetail = true;
                    for (String detail : minor.getValue().keySet()) {
                        if (!firstDetail) sb.append(',');
                        firstDetail = false;
                        sb.append('"').append(escapeJson(detail)).append("\":true");
                    }
                    sb.append('}');
                }
                sb.append('}');
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
