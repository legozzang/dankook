package kr.ac.dankook.ace.smart_recruit.controller;

// 이 코드는 테스트 코드이므로 개발할 때에는 제거하신 후 새롭게 구성해 주세요.

import kr.ac.dankook.ace.smart_recruit.model.employer.Employer;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobSourceType;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus;
import kr.ac.dankook.ace.smart_recruit.repository.EmployerRepository;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import kr.ac.dankook.ace.smart_recruit.service.location.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class JobPostingApiController {

    private static final Long AI_EMPLOYER_ID = 1L;

    private final JobPostingRepository jobPostingRepository;
    private final EmployerRepository employerRepository;
    private final GeocodingService geocodingService;

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
                        jp.getLatitude(),
                        jp.getLongitude(),
                        jp.getCreatedAt() != null ? jp.getCreatedAt().toString() : null
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    // 공고 등록
    @PostMapping
    public ResponseEntity<Void> create(@RequestBody JobPostingRequest request) {
        Employer employer = employerRepository.getReferenceById(AI_EMPLOYER_ID);
        Optional<GeocodingService.Coordinate> coordinate = resolveCoordinate(request);
        jobPostingRepository.save(new JobPosting(
                employer,
                request.title(),
                request.content(),
                request.region(),
                request.jobType(),
                JobStatus.valueOf(request.status()),
                request.deadline(),
                JobSourceType.valueOf(request.sourceType()),
                request.externalUrl(),
                coordinate.map(GeocodingService.Coordinate::latitude).orElse(null),
                coordinate.map(GeocodingService.Coordinate::longitude).orElse(null)
        ));
        return ResponseEntity.ok().build();
    }

    private Optional<GeocodingService.Coordinate> resolveCoordinate(JobPostingRequest request) {
        if (request.latitude() != null && request.longitude() != null) {
            return Optional.of(new GeocodingService.Coordinate(request.latitude(), request.longitude()));
        }
        return geocodingService.geocode(request.region());
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
            Double latitude,
            Double longitude,
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
            String externalUrl,
            Double latitude,
            Double longitude
    ) {}
}
