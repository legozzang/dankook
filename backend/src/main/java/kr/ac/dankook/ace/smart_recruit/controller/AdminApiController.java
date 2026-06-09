package kr.ac.dankook.ace.smart_recruit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import kr.ac.dankook.ace.smart_recruit.model.recommendation.UserJobRecommendation;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.recommendation.UserJobRecommendationRepository;
import kr.ac.dankook.ace.smart_recruit.service.AdminService;
import kr.ac.dankook.ace.smart_recruit.service.EmailService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final AdminService adminService;
    private final EmailService emailService;
    private final MemberRepository memberRepository;
    private final UserJobRecommendationRepository userJobRecommendationRepository;

    @DeleteMapping("/members/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        if (!adminService.deleteMember(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/members/{id}/role")
    public ResponseEntity<Void> updateMemberRole(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        adminService.updateMemberRole(id, request.role());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/members/{id}/send-recommendations")
    public ResponseEntity<String> sendRecommendationEmail(@PathVariable Long id) {
        return memberRepository.findById(id)
                .map(member -> {
                    List<UserJobRecommendation> recommendations = userJobRecommendationRepository.findByMemberId(id);
                    if (recommendations.isEmpty()) {
                        return ResponseEntity.ok("추천 공고가 없습니다.");
                    }
                    emailService.sendRecommendationEmail(member, recommendations);
                    return ResponseEntity.ok("발송 완료: " + member.getEmail());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/job-postings/{id}")
    public ResponseEntity<Void> deleteJobPosting(@PathVariable Long id) {
        if (!adminService.deleteJobPosting(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/job-postings/{id}/status")
    public ResponseEntity<Void> updateJobPostingStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        adminService.updateJobPostingStatus(id, request.status());
        return ResponseEntity.ok().build();
    }

    record RoleUpdateRequest(String role) {
    }

    record StatusUpdateRequest(String status) {
    }
}
