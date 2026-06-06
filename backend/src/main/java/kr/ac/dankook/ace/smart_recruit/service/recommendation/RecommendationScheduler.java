package kr.ac.dankook.ace.smart_recruit.service.recommendation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.recommendation.RecommendationStat;
import kr.ac.dankook.ace.smart_recruit.model.recommendation.UserJobRecommendation;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import kr.ac.dankook.ace.smart_recruit.repository.recommendation.RecommendationStatRepository;
import kr.ac.dankook.ace.smart_recruit.repository.recommendation.UserJobRecommendationRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecommendationScheduler {

    private static final Set<Integer> ALLOWED_INTERVALS = Set.of(1, 3, 6, 12, 24);

    private final MemberRepository memberRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserJobRecommendationRepository recommendationRepository;
    private final RecommendationStatRepository recommendationStatRepository;
    private final GeminiService geminiService;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void generateRecommendations() {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();
        List<Member> members = memberRepository.findByGeminiApiKeyIsNotNull();
        int totalCount = 0;

        for (Member member : members) {
            if (!shouldRun(member, now)) {
                continue;
            }

            totalCount += refreshMember(member);
        }

        if (totalCount > 0) {
            saveStat(startedAt, totalCount);
        }
    }

    @Transactional
    public int refreshForMember(Long memberId) {
        LocalDateTime startedAt = LocalDateTime.now();
        int count = memberRepository.findById(memberId)
                .map(this::refreshMember)
                .orElse(0);

        saveStat(startedAt, count);
        return count;
    }

    private int refreshMember(Member member) {
        if (isBlank(member.getGeminiApiKey())) {
            return 0;
        }

        List<JobPosting> postings = findRecommendationTargets(member);
        if (postings.isEmpty()) {
            return 0;
        }

        Map<Long, String> reasons = geminiService.callGemini(member.getGeminiApiKey(), postings);
        if (reasons.isEmpty()) {
            return 0;
        }

        return saveRecommendations(member, postings, reasons);
    }

    private List<JobPosting> findRecommendationTargets(Member member) {
        String jobTypeMajor = normalize(member.getPreferredJobTypeMajor());
        String regionSido = normalize(member.getDesiredRegionSido());
        String regionSigungu = normalize(member.getDesiredRegionSigungu());
        if (!isBlank(regionSigungu)) {
            regionSido = null;
        }
        List<Long> excludeIds = recommendationRepository.findByMemberId(member.getId())
                .stream()
                .map(recommendation -> recommendation.getJobPosting().getId())
                .toList();
        if (excludeIds.isEmpty()) {
            excludeIds = List.of(0L);
        }
        PageRequest limitTen = PageRequest.of(0, 10);

        List<JobPosting> byPay = jobPostingRepository.findRecommendationTargetsByPay(
                jobTypeMajor,
                regionSido,
                regionSigungu,
                excludeIds,
                limitTen
        );
        List<JobPosting> byLatest = jobPostingRepository.findRecommendationTargetsByLatest(
                jobTypeMajor,
                regionSido,
                regionSigungu,
                excludeIds,
                limitTen
        );

        Map<Long, JobPosting> merged = new LinkedHashMap<>();
        byPay.forEach(posting -> merged.put(posting.getId(), posting));
        byLatest.forEach(posting -> merged.put(posting.getId(), posting));
        return new ArrayList<>(merged.values());
    }

    private boolean shouldRun(Member member, LocalDateTime now) {
        Integer intervalHours = member.getRecommendationIntervalHours();
        if (isBlank(member.getGeminiApiKey()) || intervalHours == null || !ALLOWED_INTERVALS.contains(intervalHours)) {
            return false;
        }

        return recommendationRepository.findLatestCreatedAtByMemberId(member.getId())
                .map(lastCreatedAt -> !lastCreatedAt.plusHours(intervalHours).isAfter(now))
                .orElse(true);
    }

    private int saveRecommendations(Member member, List<JobPosting> postings, Map<Long, String> reasons) {
        Map<Long, JobPosting> postingById = postings.stream()
                .collect(Collectors.toMap(JobPosting::getId, posting -> posting, (left, right) -> left));
        Map<Long, UserJobRecommendation> existingByPostingId = recommendationRepository.findByMemberId(member.getId())
                .stream()
                .collect(Collectors.toMap(
                        recommendation -> recommendation.getJobPosting().getId(),
                        recommendation -> recommendation,
                        (left, right) -> left
                ));

        List<UserJobRecommendation> toSave = new ArrayList<>();
        for (Map.Entry<Long, String> entry : reasons.entrySet()) {
            Long postingId = entry.getKey();
            String reason = entry.getValue();
            if (isBlank(reason) || !postingById.containsKey(postingId)) {
                continue;
            }

            UserJobRecommendation recommendation = existingByPostingId.get(postingId);
            if (recommendation == null) {
                recommendation = new UserJobRecommendation(member, postingById.get(postingId), reason.trim());
            } else {
                recommendation.updateReason(reason.trim());
            }
            toSave.add(recommendation);
        }

        if (!toSave.isEmpty()) {
            recommendationRepository.saveAll(toSave);
        }
        return toSave.size();
    }

    private void saveStat(LocalDateTime startedAt, int postingCount) {
        double durationSeconds = Duration.between(startedAt, LocalDateTime.now()).toMillis() / 1000.0;
        recommendationStatRepository.save(new RecommendationStat(durationSeconds, postingCount));
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
