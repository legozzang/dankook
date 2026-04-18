package kr.ac.dankook.ace.smart_recruit.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
/* 
    최초 작성자 : 유지훈
    최초 작성일 : 2026.04.18
    목적 : Jobposting을 위한 Controller
    개정 이력 :  이름, 20xx.0x.xx (변경사항) <= 향후 작성
*/
@Controller
public class JobPostingViewController {
    // http://localhost:8080/jobpostings
    @GetMapping("/jobpostings")
    public String jobPostingList() {
        return "jobposting/list";
    }
}
