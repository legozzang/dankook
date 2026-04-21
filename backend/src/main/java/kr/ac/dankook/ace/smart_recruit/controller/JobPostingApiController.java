package kr.ac.dankook.ace.smart_recruit.controller;

// 이 코드는 테스트 코드이므로 개발할 때에는 제거하신 후 새롭게 구성해 주세요.

import kr.ac.dankook.ace.smart_recruit.model.employer.Employer;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobSourceType;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus;
import kr.ac.dankook.ace.smart_recruit.repository.EmployerRepository;
import kr.ac.dankook.ace.smart_recruit.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class JobPostingApiController {

    private static final Long AI_EMPLOYER_ID = 1L;

    private final JobPostingRepository jobPostingRepository;
    private final EmployerRepository employerRepository;

    // 공고 목록 조회
    @GetMapping
    public ResponseEntity<List<JobPostingResponse>> list() {
        List<JobPostingResponse> result = jobPostingRepository.findAll().stream()
                .map(jp -> new JobPostingResponse(
                        jp.getId(),
                        jp.getTitle(),
                        jp.getContent(),
                        jp.getRegion(),
                        jp.getJobType(),
                        jp.getStatus().name(),
                        jp.getDeadline(),
                        jp.getSourceType().name(),
                        jp.getExternalUrl(),
                        jp.getCreatedAt() != null ? jp.getCreatedAt().toString() : null
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    // 공고 등록
    @PostMapping
    public ResponseEntity<Void> create(@RequestBody JobPostingRequest request) {
        Employer employer = employerRepository.getReferenceById(AI_EMPLOYER_ID);
        jobPostingRepository.save(new JobPosting(
                employer,
                request.title(),
                request.content(),
                request.region(),
                request.jobType(),
                JobStatus.valueOf(request.status()),
                request.deadline(),
                JobSourceType.valueOf(request.sourceType()),
                request.externalUrl()
        ));
        return ResponseEntity.ok().build();
    }

    record JobPostingResponse(
            Long id,
            String title,
            String content,
            String region,
            String jobType,
            String status,
            String deadline,
            String sourceType,
            String externalUrl,
            String createdAt
    ) {}

    record JobPostingRequest(
            String title,
            String content,
            String region,
            String jobType,
            String status,
            String deadline,
            String sourceType,
            String externalUrl
    ) {}
}
