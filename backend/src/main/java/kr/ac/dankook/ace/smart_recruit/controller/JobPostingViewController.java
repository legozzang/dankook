package kr.ac.dankook.ace.smart_recruit.controller;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService.JobPostingCard;
import lombok.RequiredArgsConstructor;
/* 
    최초 작성자 : 유지훈
    최초 작성일 : 2026.04.18
    목적 : Jobposting을 위한 Controller
    개정 이력 :  이름, 20xx.0x.xx (변경사항) <= 향후 작성
*/
@Controller
@RequiredArgsConstructor
public class JobPostingViewController {

    // 단국대 기준 좌표 — JS의 JobMapConfig.defaultCenter 와 반드시 동일해야 함
    private static final double DANKOOK_LATITUDE  = 37.3216;
    private static final double DANKOOK_LONGITUDE = 127.1267;

    private final JobPostingService jobPostingService;
    private final JobPostingRepository jobPostingRepository;

    @GetMapping("/")
    public String root() {
        return "redirect:/jobpostings";
    }

    // http://localhost:8080/jobpostings
    @GetMapping("/jobpostings")
    public String jobPostingList(
            @RequestParam(value = "focusId", required = false) String focusIdParam,
            Model model) {

        // ── focusId 파싱: null·공백·"undefined"·비숫자 모두 방어 ──────────────
        Long focusId = parseFocusId(focusIdParam);

        // ── 전체 카드 조회 (JS 레이어가 시나리오별 필터링 담당) ────────────────
        List<JobPostingCard> jobPostings = new ArrayList<>(jobPostingService.findAllCards());

        // ── focusId 방어: 해당 공고가 목록에 없으면 맨 앞에 강제 삽입 ──────────
        // 이유: 초기 HTML에 카드가 없으면 JS의 applyFilters()가 focusId를 찾지 못함
        if (focusId != null) {
            boolean alreadyPresent = jobPostings.stream()
                    .anyMatch(c -> focusId.equals(c.id()));
            if (!alreadyPresent) {
                try {
                    jobPostingService.findCardById(focusId)
                            .ifPresent(target -> jobPostings.add(0, target));
                } catch (Exception e) {
                    // 유령 ID(DB에 없는 값) → 무시하고 정상 목록만 렌더링
                    System.err.println("[WARN] focusId " + focusId + " not found in DB: " + e.getMessage());
                }
            }
        }

        // ── 뷰 바인딩 ──────────────────────────────────────────────────────────
        List<String> dongs = jobPostings.stream()
                .map(JobPostingCard::dong)
                .distinct()
                .toList();

        Map<String, List<String>> sigunguBySido = new TreeMap<>();
        for (Object[] row : jobPostingRepository.findDistinctSidoSigunguPairs()) {
            String sido = (String) row[0];
            String sigungu = (String) row[1];
            sigunguBySido.computeIfAbsent(sido, k -> new ArrayList<>()).add(sigungu);
        }

        Map<String, List<String>> jobTypeMidByMajor = new TreeMap<>();
        for (Object[] row : jobPostingRepository.findDistinctJobMajorMidPairs()) {
            String major = (String) row[0];
            String mid = row[1] != null ? (String) row[1] : "";
            if (!mid.isBlank()) {
                jobTypeMidByMajor.computeIfAbsent(major, k -> new ArrayList<>()).add(mid);
            }
        }

        model.addAttribute("jobPostings", jobPostings);
        model.addAttribute("focusId", focusId != null ? String.valueOf(focusId) : null);
        model.addAttribute("payTypes", jobPostingRepository.findDistinctPayTypes());
        model.addAttribute("dongs", dongs);
        model.addAttribute("sidos", new ArrayList<>(sigunguBySido.keySet()));
        model.addAttribute("sigunguBySidoJson", toJson(sigunguBySido));
        model.addAttribute("jobMajors", new ArrayList<>(jobTypeMidByMajor.keySet()));
        model.addAttribute("jobTypeMidByMajorJson", toJson(jobTypeMidByMajor));

        return "jobposting/list";
    }

    /**
     * focusId 문자열을 Long으로 안전하게 변환한다.
     * null, 공백, "undefined", 비숫자 → null 반환 (500 방지)
     */
    private Long parseFocusId(String raw) {
        if (raw == null || raw.isBlank() || "undefined".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            System.err.println("[WARN] 유효하지 않은 focusId 포맷 무시: " + raw);
            return null;
        }
    }

    @GetMapping("/jobpostings/{id}")
    public String jobPostingDetail(@PathVariable Long id, Model model) {
        return jobPostingService.findCardById(id)
                .map(jobPosting -> {
                    model.addAttribute("jobPosting", jobPosting);
                    return "jobposting/detail";
                })
                .orElse("redirect:/jobpostings");
    }

    private String toJson(Map<String, List<String>> valuesByKey) {
        StringBuilder sb = new StringBuilder("{");
        boolean firstKey = true;
        for (Map.Entry<String, List<String>> entry : valuesByKey.entrySet()) {
            if (!firstKey) sb.append(',');
            firstKey = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":[");
            boolean firstValue = true;
            for (String value : entry.getValue()) {
                if (!firstValue) sb.append(',');
                firstValue = false;
                sb.append('"').append(escapeJson(value)).append('"');
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
