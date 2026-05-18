package kr.ac.dankook.ace.smart_recruit.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobSourceType;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class JobPostingApiController {

    private static final double DEFAULT_LATITUDE = 37.3216;
    private static final double DEFAULT_LONGITUDE = 127.1267;

    private final JobPostingRepository jobPostingRepository;

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
                        jp.getCreatedAt() != null ? jp.getCreatedAt().toString() : null,
                        jp.getCompany(),
                        jp.getWelfare()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody JobPostingRequest request) {
        jobPostingRepository.save(new JobPosting(
                request.title(),
                request.content(),
                request.region(),
                request.jobType(),
                safeJobStatus(request.status()),
                request.deadline(),
                safeSourceType(request.sourceType()),
                request.externalUrl(),
                request.company() != null ? request.company() : "",
                request.latitude() != null ? request.latitude() : DEFAULT_LATITUDE,
                request.longitude() != null ? request.longitude() : DEFAULT_LONGITUDE,
                request.regionSido() != null ? request.regionSido() : "",
                request.regionSigungu() != null ? request.regionSigungu() : "",
                request.payType(),
                request.payAmount(),
                request.jobTypeMajor() != null ? request.jobTypeMajor() : "",
                request.jobTypeMid() != null ? request.jobTypeMid() : "",
                request.jobTypeMinor() != null ? request.jobTypeMinor() : "",
                request.jobTypeDetail() != null ? request.jobTypeDetail() : "",
                request.welfare()
        ));
        return ResponseEntity.status(201).build();
    }

    private JobStatus safeJobStatus(String s) {
        try {
            return JobStatus.valueOf(s);
        } catch (Exception e) {
            return JobStatus.OPEN;
        }
    }

    private JobSourceType safeSourceType(String s) {
        try {
            return JobSourceType.valueOf(s);
        } catch (Exception e) {
            return JobSourceType.INTERNAL;
        }
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
            String createdAt,
            String company,
            String welfare
    ) {
    }

    record JobPostingRequest(
            String title,
            String content,
            String region,
            @JsonProperty("job_type") String jobType,
            String status,
            String deadline,
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("external_url") String externalUrl,
            String company,
            Double latitude,
            Double longitude,
            @JsonProperty("region_sido") String regionSido,
            @JsonProperty("region_sigungu") String regionSigungu,
            @JsonProperty("pay_type") String payType,
            @JsonProperty("pay_amount") Integer payAmount,
            @JsonProperty("job_type_major") String jobTypeMajor,
            @JsonProperty("job_type_mid") String jobTypeMid,
            @JsonProperty("job_type_minor") String jobTypeMinor,
            @JsonProperty("job_type_detail") String jobTypeDetail,
            String welfare
    ) {
    }
}
