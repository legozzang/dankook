package kr.ac.dankook.ace.smart_recruit.controller;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
    public String jobPostingList(Model model) {
        List<JobPostingCard> jobPostings = jobPostingService.findAllCards();
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
        model.addAttribute("jobPostings", jobPostings);
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
