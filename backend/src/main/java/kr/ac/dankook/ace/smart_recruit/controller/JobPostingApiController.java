package kr.ac.dankook.ace.smart_recruit.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.ac.dankook.ace.smart_recruit.model.recommendation.UserJobRecommendation;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.recommendation.RecommendationStatRepository;
import kr.ac.dankook.ace.smart_recruit.repository.recommendation.UserJobRecommendationRepository;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService.JobPostingCard;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService.JobPostingCreateCommand;
import kr.ac.dankook.ace.smart_recruit.service.jobposting.JobPostingService.JobPostingResponse;
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

    private final JobPostingService jobPostingService;
    private final MemberRepository memberRepository;
    private final UserJobRecommendationRepository userJobRecommendationRepository;
    private final RecommendationStatRepository recommendationStatRepository;
    private final RecommendationScheduler recommendationScheduler;

    @GetMapping
    public ResponseEntity<List<JobPostingResponse>> list() {
        return ResponseEntity.ok(jobPostingService.findAllResponses());
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
                    List<UserJobRecommendation> recs = userJobRecommendationRepository.findByMemberId(member.getId());
                    List<Long> jobIds = recs.stream()
                            .map(r -> r.getJobPosting().getId())
                            .toList();
                    Map<Long, JobPostingCard> cardMap = jobPostingService.findCardsByIdIn(jobIds).stream()
                            .collect(java.util.stream.Collectors.toMap(JobPostingCard::id, c -> c));
                    List<CardResponse> result = recs.stream()
                            .map(r -> {
                                JobPostingCard card = cardMap.get(r.getJobPosting().getId());
                                return card == null ? null : toCardResponse(card, r.getRecommendationReason());
                            })
                            .filter(c -> c != null)
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
                .filter(seconds -> seconds != null && seconds > 0)
                .toList();
        if (durations.isEmpty()) {
            return ResponseEntity.ok(new EstimatedTimeResponse(0.0, 0));
        }

        double average = durations.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        long estimatedSeconds = Math.max(1, Math.round(average));
        return ResponseEntity.ok(new EstimatedTimeResponse((double) estimatedSeconds, durations.size()));
    }

    private int parseLimit(String limit) {
        try {
            int parsed = Integer.parseInt(limit);
            return parsed > 0 ? parsed : 10;
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "전체".equals(trimmed) || "all".equals(trimmed) ? null : trimmed;
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

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody JobPostingRequest request) {
        jobPostingService.create(new JobPostingCreateCommand(
                request.title(),
                request.content(),
                request.region(),
                request.jobType(),
                request.status(),
                request.deadline(),
                request.sourceType(),
                request.externalUrl(),
                request.company(),
                request.latitude(),
                request.longitude(),
                request.regionSido(),
                request.regionSigungu(),
                request.payType(),
                request.payAmount(),
                request.jobTypeMajor(),
                request.jobTypeMid(),
                request.jobTypeMinor(),
                request.jobTypeDetail(),
                request.welfare()
        ));
        return ResponseEntity.status(201).build();
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
