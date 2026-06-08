package kr.ac.dankook.ace.smart_recruit.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.member.Role;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final MemberRepository memberRepository;
    private final JobPostingRepository jobPostingRepository;

    @Transactional
    public boolean deleteMember(Long id) {
        if (!memberRepository.existsById(id)) {
            return false;
        }
        memberRepository.deleteById(id);
        return true;
    }

    @Transactional
    public void updateMemberRole(Long id, String role) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        try {
            member.updateRole(Role.valueOf(role));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("유효하지 않은 역할: " + role);
        }
    }

    @Transactional
    public boolean deleteJobPosting(Long id) {
        if (!jobPostingRepository.existsById(id)) {
            return false;
        }
        jobPostingRepository.deleteById(id);
        return true;
    }

    @Transactional
    public void updateJobPostingStatus(Long id, String status) {
        JobPosting jobPosting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공고를 찾을 수 없습니다."));
        try {
            jobPosting.updateStatus(JobStatus.valueOf(status));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("유효하지 않은 공고 상태: " + status);
        }
    }
}
