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

    private final JobPostingService jobPostingService;
    private final JobPostingRepository jobPostingRepository;

    @GetMapping("/")
    public String root() {
        return "redirect:/jobpostings";
    }

    // http://localhost:8080/jobpostings
    @GetMapping("/jobpostings")
    public String jobPostingList(@RequestParam(value = "focusId", required = false) String focusId, Model model) {
        
        List<JobPostingCard> jobPostings = jobPostingService.findAllCards();
        
        // 🚨 [실무 방어 관행 Habit] 마이페이지 포커스 ID가 넘어왔는데, 
        // 페이징이나 데이터 목록 압축 때문에 현재 리스트에 해당 ID가 누락되는 현상을 철저히 방어합니다.
        if (focusId != null && !focusId.isBlank() && !focusId.equals("undefined")) {
            try {
                Long targetId = Long.parseLong(focusId.trim());
                
                // 현재 전체 카드 목록에 마이페이지에서 요청한 focusId가 존재하는지 검증
                boolean isPresent = jobPostings.stream()
                        .anyMatch(card -> card.id() != null && card.id().equals(targetId));
                
                // 만약 현재 수집된 카드 목록에 해당 공고가 누락되어 있다면 단독 추가 처리
                if (!isPresent) {
                    jobPostingService.findCardById(targetId).ifPresent(targetCard -> {
                        // 리스트 맨 앞에 강제로 끼워 넣어 프론트엔드 DOM에 무조건 그리도록 보장합니다.
                        // 이로 인해 자바스크립트 applyFilters() 내부의 console.table 전수조사에 명확히 검출됩니다.
                        if (jobPostings instanceof ArrayList) {
                            jobPostings.add(0, targetCard);
                        } else {
                            List<JobPostingCard> modifiableList = new ArrayList<>(jobPostings);
                            modifiableList.add(0, targetCard);
                            model.addAttribute("jobPostings", modifiableList);
                            return;
                        }
                    });
                }
            } catch (NumberFormatException e) {
                // 숫자가 아닌 이상한 값이 파라미터로 유입되었을 때 로그만 남기고 정상 흐름 유지
                System.err.println("[경고] 유효하지 않은 focusId 포맷 유입: " + focusId);
            }
        }

        // 기존 뷰 바인딩 로직 유지
        List<String> dongs = jobPostings.stream()
                .map(JobPostingCard::dong)
                .distinct()
                .toList();
                
        Map<String, List<String>> sigunguBySido = new TreeMap<>();
        for (Object[] row : jobPostingRepository.findDistinctSidoSigunguPairs()) {
            String sido = (String) row[0];
            String sigungu = (String) row[1];
            sigunguBySido.computeIfAbsent(sido, key -> new ArrayList<>()).add(sigungu);
        }
        List<String> sidos = new ArrayList<>(sigunguBySido.keySet());

        Map<String, List<String>> jobTypeMidByMajor = new TreeMap<>();
        for (Object[] row : jobPostingRepository.findDistinctJobMajorMidPairs()) {
            String major = (String) row[0];
            String mid = row[1] != null ? (String) row[1] : "";
            if (!mid.isBlank()) {
                jobTypeMidByMajor.computeIfAbsent(major, key -> new ArrayList<>()).add(mid);
            }
        }
        List<String> jobMajors = new ArrayList<>(jobTypeMidByMajor.keySet());
        
        model.addAttribute("payTypes", jobPostingRepository.findDistinctPayTypes());
        
        // 위에서 가공 및 안전 검증이 끝난 리스트를 바인딩
        if (model.getAttribute("jobPostings") == null) {
            model.addAttribute("jobPostings", jobPostings);
        }
        
        model.addAttribute("dongs", dongs);
        model.addAttribute("sidos", sidos);
        model.addAttribute("sigunguBySidoJson", toJson(sigunguBySido));
        model.addAttribute("jobMajors", jobMajors);
        model.addAttribute("jobTypeMidByMajorJson", toJson(jobTypeMidByMajor));
        
        return "jobposting/list";
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
