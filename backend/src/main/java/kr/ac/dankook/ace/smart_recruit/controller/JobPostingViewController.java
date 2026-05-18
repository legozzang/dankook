package kr.ac.dankook.ace.smart_recruit.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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

    // http://localhost:8080/jobpostings
    @GetMapping("/jobpostings")
    public String jobPostingList(Model model) {
        List<JobPostingCard> jobPostings = jobPostingService.findAllCards();
        List<String> dongs = jobPostings.stream()
                .map(JobPostingCard::dong)
                .distinct()
                .toList();
        model.addAttribute("jobPostings", jobPostings);
        model.addAttribute("dongs", dongs);
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
}
