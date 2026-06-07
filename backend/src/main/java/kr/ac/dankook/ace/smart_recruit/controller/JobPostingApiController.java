package kr.ac.dankook.ace.smart_recruit.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobSourceType;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus;
import kr.ac.dankook.ace.smart_recruit.model.recommendation.UserJobRecommendation;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import kr.ac.dankook.ace.smart_recruit.repository.recommendation.RecommendationStatRepository;
import kr.ac.dankook.ace.smart_recruit.repository.recommendation.UserJobRecommendationRepository;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService;
import kr.ac.dankook.ace.smart_recruit.config.AppConstants;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService.JobPostingCard;
import kr.ac.dankook.ace.smart_recruit.service.recommendation.RecommendationScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class JobPostingApiController {

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingService jobPostingService;
    private final MemberRepository memberRepository;
    private final UserJobRecommendationRepository userJobRecommendationRepository;
    private final RecommendationStatRepository recommendationStatRepository;
    private final RecommendationScheduler recommendationScheduler;

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

    @GetMapping("/search")
    public ResponseEntity<List<CardResponse>> search(
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String jobTypeMajor,
            @RequestParam(required = false) String jobTypeMid,
            @RequestParam(required = false, defaultValue = "10") String limit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radius,
            @RequestParam(required = false) String payType,
            @RequestParam(required = false, defaultValue = "default") String sort,
            @AuthenticationPrincipal User user
    ) {
        String s = normalizeFilter(sido);
        String sg = normalizeFilter(sigungu);
        String jm = normalizeFilter(jobTypeMajor);
        String jmd = normalizeFilter(jobTypeMid);
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String pt = normalizeFilter(payType);
        Map<Long, String> personalizedReasons = findPersonalizedReasons(user);
        int limitCount = "all".equals(limit) ? Integer.MAX_VALUE : parseLimit(limit);

        if (lat != null && lng != null && radius != null) {
            int radiusLimitCount = "all".equals(limit) ? 1000 : limitCount;
            List<CardResponse> result = jobPostingService
                    .findCardsByRadius(lat, lng, radius, radiusLimitCount, pt, s, sg, jm, jmd, kw, sort)
                    .stream().map(card -> toCardResponse(card, personalizedReasons)).toList();
            return ResponseEntity.ok(result);
        }

        List<CardResponse> result = jobPostingService
                .findCardsByFilters(s, sg, jm, jmd, kw, pt, sort, lat, lng, limitCount)
                .stream().map(card -> toCardResponse(card, personalizedReasons)).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<CardResponse>> recommendations(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.ok(List.of());
        }

        return memberRepository.findByEmail(user.getUsername())
                .map(member -> {
                    List<CardResponse> result = userJobRecommendationRepository.findByMemberId(member.getId())
                            .stream()
                            .map(this::toRecommendationCardResponse)
                            .toList();
                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> ResponseEntity.ok(List.of()));
    }

    @PostMapping("/recommendations/refresh")
    public ResponseEntity<RefreshResponse> refreshRecommendations(@AuthenticationPrincipal User user) {
        Instant startedAt = Instant.now();
        if (user == null) {
            return ResponseEntity.ok(new RefreshResponse("로그인이 필요합니다", 0, elapsedSeconds(startedAt)));
        }

        return memberRepository.findByEmail(user.getUsername())
                .map(member -> {
                    int count = recommendationScheduler.refreshForMember(member.getId());
                    return ResponseEntity.ok(new RefreshResponse("갱신 완료", count, elapsedSeconds(startedAt)));
                })
                .orElseGet(() -> ResponseEntity.ok(new RefreshResponse("사용자를 찾을 수 없습니다", 0, elapsedSeconds(startedAt))));
    }

    @GetMapping("/recommendations/estimated-time")
    public ResponseEntity<EstimatedTimeResponse> estimatedRecommendationTime() {
        List<Double> durations = recommendationStatRepository.findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(stat -> stat.getDurationSeconds())
                .filter(duration -> duration != null && duration >= 0)
                .toList();

        if (durations.isEmpty()) {
            return ResponseEntity.ok(new EstimatedTimeResponse(null, 0));
        }

        double average = durations.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        return ResponseEntity.ok(new EstimatedTimeResponse(Math.ceil(average), durations.size()));
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
                request.latitude() != null ? request.latitude() : AppConstants.DANKOOK_LATITUDE,
                request.longitude() != null ? request.longitude() : AppConstants.DANKOOK_LONGITUDE,
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

    private String normalizeFilter(String value) {
        return value != null && !value.isBlank() && !"all".equals(value) ? value : null;
    }

    private int parseLimit(String limit) {
        try {
            int parsed = Integer.parseInt(limit);
            return parsed > 0 ? parsed : 10;
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private double elapsedSeconds(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis() / 1000.0;
    }

    private Map<Long, String> findPersonalizedReasons(User user) {
        if (user == null) {
            return Collections.emptyMap();
        }
        return memberRepository.findByEmail(user.getUsername())
                .map(member -> userJobRecommendationRepository.findReasonMap(member.getId()))
                .orElseGet(Collections::emptyMap);
    }

    private CardResponse toCardResponse(JobPostingCard card, Map<Long, String> personalizedReasons) {
        String recommendationReason = personalizedReasons.getOrDefault(card.id(), card.recommendationReason());
        return toCardResponse(card, recommendationReason);
    }

    private CardResponse toRecommendationCardResponse(UserJobRecommendation recommendation) {
        JobPostingCard card = jobPostingService.toCard(recommendation.getJobPosting());
        return toCardResponse(card, recommendation.getRecommendationReason());
    }

    private CardResponse toCardResponse(JobPostingCard card, String recommendationReason) {
        return new CardResponse(
                card.id(),
                card.title(),
                card.companyName(),
                card.location(),
                card.dong(),
                card.sido(),
                card.sigungu(),
                card.jobType(),
                card.jobTypeMajor(),
                card.jobTypeMid(),
                card.status(),
                card.deadline(),
                card.salary(),
                card.workTime(),
                recommendationReason,
                card.summaryLines(),
                card.externalUrl(),
                card.latitude(),
                card.longitude(),
                card.exactLocation(),
                card.scraped()
        );
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

    record CardResponse(
            Long id,
            String title,
            String companyName,
            String location,
            String dong,
            String sido,
            String sigungu,
            String jobType,
            String jobTypeMajor,
            String jobTypeMid,
            String status,
            String deadline,
            String salary,
            String workTime,
            String recommendationReason,
            List<String> summaryLines,
            String externalUrl,
            double latitude,
            double longitude,
            boolean exactLocation,
            boolean scraped
    ) {
    }

    record RefreshResponse(
            String message,
            int count,
            double durationSeconds
    ) {
    }

    record EstimatedTimeResponse(
            Double estimatedSeconds,
            int sampleCount
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
