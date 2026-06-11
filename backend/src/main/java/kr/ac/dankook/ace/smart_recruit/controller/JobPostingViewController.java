package kr.ac.dankook.ace.smart_recruit.controller;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import kr.ac.dankook.ace.smart_recruit.config.AppConstants;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService.JobPostingCard;
import kr.ac.dankook.ace.smart_recruit.util.JsonViewUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
    최초 작성자 : 유지훈
    최초 작성일 : 2026.04.18
    목적 : /jobpostings 뷰 라우팅 Controller
    개정 이력 : 박진현, 2026.06.07 (focusId 파라미터 방어, AppConstants·JsonViewUtils·Slf4j 적용)
*/
@Slf4j
@Controller
@RequiredArgsConstructor
public class JobPostingViewController {

    private final JobPostingService jobPostingService;
    private final JobPostingRepository jobPostingRepository;
    private final MemberRepository memberRepository;

    @GetMapping("/")
    public String root() {
        return "redirect:/jobpostings";
    }

    @GetMapping("/jobpostings")
    public String jobPostingList(
            @RequestParam(value = "focusId", required = false) String focusIdParam,
            @AuthenticationPrincipal User user,
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
                    log.warn("[focusId 미발견] id={}, msg={}", focusId, e.getMessage());
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
            List<String> mids = jobTypeMidByMajor.computeIfAbsent(major, k -> new ArrayList<>());
            if (!mid.isBlank()) {
                mids.add(mid);
            }
        }

        model.addAttribute("jobPostings", jobPostings);
        model.addAttribute("focusId", focusId != null ? String.valueOf(focusId) : null);
        model.addAttribute("payTypes", jobPostingRepository.findDistinctPayTypes());
        model.addAttribute("dongs", dongs);
        model.addAttribute("sidos", jobPostingRepository.findDistinctSidos());
        model.addAttribute("sigunguBySidoJson", JsonViewUtils.toJson(sigunguBySido));
        model.addAttribute("jobMajors", new ArrayList<>(jobTypeMidByMajor.keySet()));
        model.addAttribute("jobTypeMidByMajorJson", JsonViewUtils.toJson(jobTypeMidByMajor));
        if (user != null) {
            memberRepository.findByEmail(user.getUsername()).ifPresent(member -> {
                List<Map<String, String>> desiredRegions = new ArrayList<>();
                if (member.getDesiredRegionSido() != null && !member.getDesiredRegionSido().isBlank()) {
                    desiredRegions.add(Map.of(
                        "label", "관심 지역 1",
                        "sido", member.getDesiredRegionSido(),
                        "sigungu", member.getDesiredRegionSigungu() != null ? member.getDesiredRegionSigungu() : ""
                    ));
                }
                if (member.getDesiredRegion2Sido() != null && !member.getDesiredRegion2Sido().isBlank()) {
                    desiredRegions.add(Map.of(
                        "label", "관심 지역 2",
                        "sido", member.getDesiredRegion2Sido(),
                        "sigungu", member.getDesiredRegion2Sigungu() != null ? member.getDesiredRegion2Sigungu() : ""
                    ));
                }
                if (member.getDesiredRegion3Sido() != null && !member.getDesiredRegion3Sido().isBlank()) {
                    desiredRegions.add(Map.of(
                        "label", "관심 지역 3",
                        "sido", member.getDesiredRegion3Sido(),
                        "sigungu", member.getDesiredRegion3Sigungu() != null ? member.getDesiredRegion3Sigungu() : ""
                    ));
                }
                if (!desiredRegions.isEmpty()) {
                    model.addAttribute("desiredRegions", desiredRegions);
                }
            });
        }
        model.addAttribute("isAdmin", isAdmin(user));

        return "jobposting/list";
    }

    private boolean isAdmin(User user) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
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
            log.warn("[focusId 포맷 오류] raw={}", raw);
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
}
