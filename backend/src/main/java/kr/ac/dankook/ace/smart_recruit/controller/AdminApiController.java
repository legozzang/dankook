package kr.ac.dankook.ace.smart_recruit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.member.Role;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final MemberRepository memberRepository;
    private final JobPostingRepository jobPostingRepository;

    @DeleteMapping("/members/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        if (!memberRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        memberRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/members/{id}/role")
    @Transactional
    public ResponseEntity<Void> updateMemberRole(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        member.updateRole(Role.valueOf(request.role()));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/job-postings/{id}")
    public ResponseEntity<Void> deleteJobPosting(@PathVariable Long id) {
        if (!jobPostingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        jobPostingRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/job-postings/{id}/status")
    @Transactional
    public ResponseEntity<Void> updateJobPostingStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        JobPosting jobPosting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공고를 찾을 수 없습니다."));
        jobPosting.updateStatus(JobStatus.valueOf(request.status()));
        return ResponseEntity.ok().build();
    }

    record RoleUpdateRequest(String role) {
    }

    record StatusUpdateRequest(String status) {
    }
}
