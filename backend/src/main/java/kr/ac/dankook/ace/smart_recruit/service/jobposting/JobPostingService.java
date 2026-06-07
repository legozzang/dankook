package kr.ac.dankook.ace.smart_recruit.service.jobposting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobpostingaisummary.JobPostingAiSummary;
import kr.ac.dankook.ace.smart_recruit.config.AppConstants;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import kr.ac.dankook.ace.smart_recruit.repository.scrap.ScrapRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private static final Pattern SALARY_PATTERN = Pattern.compile("(시급|월급|일급|연봉|급여)\\s*[:：]?\\s*([0-9,]+\\s*(원|만원)?|협의|면접\\s*후\\s*결정)");
    private static final Pattern WORK_TIME_PATTERN = Pattern.compile("((근무\\s*시간|근무시간|시간)\\s*[:：]?\\s*)?([0-2]?\\d\\s*[:시]\\s*\\d{0,2}\\s*~\\s*[0-2]?\\d\\s*[:시]\\s*\\d{0,2}|오전\\s*\\d+\\s*시\\s*~\\s*오후\\s*\\d+\\s*시|주\\s*\\d+\\s*일|요일\\s*협의)");

    private final JobPostingRepository jobPostingRepository;
    private final EntityManager entityManager;
    private final ScrapRepository scrapRepository;
    private final MemberRepository memberRepository;

    public List<JobPosting> findAll() {
        return jobPostingRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<JobPostingCard> findAllCards() {
        List<JobPosting> postings = jobPostingRepository.findRecentWithAiSummary(PageRequest.of(0, 10));
        Set<Long> scraped = batchScrapedIds(postings);
        return postings.stream().map(p -> toCard(p, scraped)).toList();
    }

    public Optional<JobPostingCard> findCardById(Long id) {
        return jobPostingRepository.findById(id).map(posting -> {
            Long memberId = currentMemberId();
            boolean scraped = memberId != null
                    && scrapRepository.existsByMember_IdAndJobPosting_Id(memberId, id);
            return toCard(posting, scraped ? Set.of(id) : Collections.emptySet());
        });
    }

    public List<JobPostingCard> findCardsByFilters(
            String sido, String sigungu, String jobTypeMajor, String jobTypeMid,
            String keyword, String payType, String sort, Double lat, Double lng, int limit) {
        List<JobPosting> postings = jobPostingRepository.findByFilters(
                sido, sigungu, jobTypeMajor, jobTypeMid, keyword, payType);
        if ("salary".equals(sort)) {
            postings = new ArrayList<>(postings);
            postings.sort(Comparator.comparingInt(
                    (JobPosting j) -> j.getPayAmount() != null ? j.getPayAmount() : 0
            ).reversed());
        } else if ("distance".equals(sort) && lat != null && lng != null) {
            postings = new ArrayList<>(postings);
            Coordinate center = new Coordinate(lat, lng);
            postings.sort(Comparator.comparingDouble((JobPosting j) -> distanceKm(center, coordinateFor(j))));
        }

        Stream<JobPosting> stream = postings.stream();
        if (limit != Integer.MAX_VALUE) {
            stream = stream.limit(limit);
        }
        List<JobPosting> limited = stream.toList();
        Set<Long> scraped = batchScrapedIds(limited);
        return limited.stream().map(p -> toCard(p, scraped)).toList();
    }

    public List<JobPostingCard> findCardsByRadius(
            double lat, double lng, double radiusKm, int limit,
            String payType, String sido, String sigungu,
            String jobTypeMajor, String jobTypeMid, String keyword, String sort) {
        int sqlLimit = "salary".equals(sort) ? 5000 : limit;
        List<JobPosting> postings = new ArrayList<>(
                findByRadius(lat, lng, radiusKm, payType, sido, sigungu, jobTypeMajor, jobTypeMid, keyword, sqlLimit));
        loadAiSummaries(postings);
        if ("salary".equals(sort)) {
            postings.sort(Comparator.comparingInt(
                    (JobPosting j) -> j.getPayAmount() != null ? j.getPayAmount() : 0
            ).reversed());
            postings = postings.subList(0, Math.min(limit, postings.size()));
        }
        Set<Long> scraped = batchScrapedIds(postings);
        return postings.stream().map(p -> toCard(p, scraped)).toList();
    }

    // ── 스크랩 배치 조회 헬퍼 ──────────────────────────────────────────────

    /**
     * SecurityContextHolder에서 현재 로그인 유저의 memberId를 안전하게 꺼낸다.
     * 비로그인(AnonymousAuthenticationToken) 또는 null 이면 null 반환.
     */
    private Long currentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserDetails)) {
            return null;
        }
        String email = ((UserDetails) principal).getUsername();
        return memberRepository.findByEmail(email)
                .map(m -> m.getId())
                .orElse(null);
    }

    /**
     * 공고 목록에 대해 한 번의 쿼리로 스크랩된 ID Set을 반환 (N+1 방지).
     */
    private Set<Long> batchScrapedIds(List<JobPosting> postings) {
        if (postings.isEmpty()) return Collections.emptySet();
        Long memberId = currentMemberId();
        if (memberId == null) return Collections.emptySet();
        List<Long> postingIds = postings.stream().map(JobPosting::getId).toList();
        return new HashSet<>(scrapRepository.findScrapedJobPostingIds(memberId, postingIds));
    }

    // ── 내부 변환 메서드 ──────────────────────────────────────────────────

    private JobPostingCard toCard(JobPosting jobPosting, Set<Long> scrapedIds) {
        String location = locationLabel(jobPosting);
        String dong = dongLabel(jobPosting, location);
        boolean hasExactLocation = hasCoordinate(jobPosting);
        Coordinate coordinate = coordinateFor(jobPosting, dong);
        boolean scraped = scrapedIds.contains(jobPosting.getId());

        return new JobPostingCard(
                jobPosting.getId(),
                jobPosting.getTitle(),
                companyName(jobPosting.getCompany()),
                jobPosting.getContent(),
                location,
                dong,
                jobPosting.getRegionSido() != null ? jobPosting.getRegionSido() : "",
                jobPosting.getRegionSigungu() != null ? jobPosting.getRegionSigungu() : "",
                jobPosting.getJobTypeMajor() != null ? jobPosting.getJobTypeMajor() : "",
                jobPosting.getJobTypeMid() != null ? jobPosting.getJobTypeMid() : "",
                jobPosting.getJobType(),
                jobPosting.getStatus().name(),
                deadlineLabel(jobPosting.getDeadline()),
                salaryLabel(jobPosting),
                extractWorkTime(jobPosting.getContent()),
                "",                          // recommendationReason (추천 경로가 아닌 경우 빈 값)
                summaryLines(jobPosting),
                jobPosting.getExternalUrl(),
                coordinate.latitude(),
                coordinate.longitude(),
                hasExactLocation,
                scraped
        );
    }

    private boolean hasCoordinate(JobPosting jobPosting) {
        return jobPosting.getLatitude() != null && jobPosting.getLongitude() != null;
    }

    private Coordinate coordinateFor(JobPosting jobPosting) {
        return coordinateFor(jobPosting, dongLabel(jobPosting, locationLabel(jobPosting)));
    }

    private Coordinate coordinateFor(JobPosting jobPosting, String dong) {
        return hasCoordinate(jobPosting)
                ? new Coordinate(jobPosting.getLatitude(), jobPosting.getLongitude())
                : fallbackCoordinateFor(jobPosting.getId(), dong);
    }

    private double distanceKm(Coordinate a, Coordinate b) {
        double earthRadius = 6371.0;
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLng = Math.toRadians(b.longitude() - a.longitude());
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double value = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        value = Math.min(1.0, Math.max(0.0, value));
        return earthRadius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private List<JobPosting> findByRadius(
            double lat, double lng, double radiusKm, String payType,
            String sido, String sigungu, String jobTypeMajor, String jobTypeMid,
            String keyword, int limit) {
        Query query = entityManager.createNativeQuery("""
                SELECT * FROM (
                    SELECT *, (6371 * acos(
                        cos(radians(:lat)) * cos(radians(latitude)) * cos(radians(longitude) - radians(:lng))
                        + sin(radians(:lat)) * sin(radians(latitude))
                    )) AS distance
                    FROM job_postings
                    WHERE latitude IS NOT NULL AND longitude IS NOT NULL
                      AND (:payType IS NULL OR pay_type = :payType)
                      AND (:sido IS NULL OR region_sido = :sido)
                      AND (:sigungu IS NULL OR region_sigungu = :sigungu)
                      AND (:jobTypeMajor IS NULL OR job_type_major = :jobTypeMajor)
                      AND (:jobTypeMid IS NULL OR job_type_mid = :jobTypeMid)
                      AND (:keyword IS NULL OR LOWER(title) LIKE LOWER(CONCAT('%', :keyword, '%')))
                ) sub
                WHERE distance <= :radiusKm
                ORDER BY distance
                """, JobPosting.class);

        query.setParameter("lat", lat);
        query.setParameter("lng", lng);
        query.setParameter("radiusKm", radiusKm);
        query.setParameter("payType", payType);
        query.setParameter("sido", sido);
        query.setParameter("sigungu", sigungu);
        query.setParameter("jobTypeMajor", jobTypeMajor);
        query.setParameter("jobTypeMid", jobTypeMid);
        query.setParameter("keyword", keyword);
        query.setMaxResults(limit);

        List<?> result = query.getResultList();
        return result.stream().map(JobPosting.class::cast).toList();
    }

    private void loadAiSummaries(List<JobPosting> postings) {
        List<Long> ids = postings.stream().map(JobPosting::getId).toList();
        if (!ids.isEmpty()) {
            jobPostingRepository.findWithAiSummaryByIdIn(ids);
        }
    }

    private String companyName(String company) {
        return isBlank(company) ? "회사명 미등록" : company;
    }

    private String locationLabel(JobPosting jobPosting) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, jobPosting.getRegionSido());
        addIfPresent(parts, jobPosting.getRegionSigungu());
        if (!parts.isEmpty()) return String.join(" ", parts);
        if (!isBlank(jobPosting.getRegion())) return jobPosting.getRegion();
        return "위치 미등록";
    }

    private String dongLabel(JobPosting jobPosting, String location) {
        String source = !isBlank(jobPosting.getRegionSigungu())
                ? jobPosting.getRegionSigungu()
                : (!isBlank(jobPosting.getRegion()) ? jobPosting.getRegion() : location);
        if (isBlank(source)) return "기타";
        for (String token : source.split("\\s+")) {
            if (token.endsWith("동") || token.endsWith("읍") || token.endsWith("면") || token.endsWith("구")) {
                return token;
            }
        }
        return source.split("\\s+")[0];
    }

    private String deadlineLabel(String deadline) {
        return isBlank(deadline) ? "마감일 미정" : deadline;
    }

    private String salaryLabel(JobPosting jobPosting) {
        if (!isBlank(jobPosting.getPayType()) && jobPosting.getPayAmount() != null && jobPosting.getPayAmount() > 0) {
            return jobPosting.getPayType() + " " + String.format("%,d", jobPosting.getPayAmount()) + "원";
        }
        if (!isBlank(jobPosting.getPayType())) return jobPosting.getPayType();
        return extractSalary(jobPosting.getContent());
    }

    private String extractSalary(String content) {
        return extract(content, SALARY_PATTERN, 0, "급여 협의");
    }

    private String extractWorkTime(String content) {
        return extract(content, WORK_TIME_PATTERN, 0, "근무시간 협의");
    }

    private String extract(String content, Pattern pattern, int group, String fallback) {
        if (isBlank(content)) return fallback;
        Matcher matcher = pattern.matcher(content.replaceAll("\\s+", " "));
        if (matcher.find()) return matcher.group(group).trim();
        return fallback;
    }

    private List<String> summaryLines(JobPosting jobPosting) {
        JobPostingAiSummary aiSummary = jobPosting.getJobPostingAiSummary();
        List<String> lines = new ArrayList<>();
        if (aiSummary != null) {
            addSummaryLine(lines, "필요 역량", aiSummary.getRequiredSkills());
            addSummaryLine(lines, "주요 업무", aiSummary.getMainTasks());
            addSummaryLine(lines, "혜택", aiSummary.getCoreBenefits());
        }
        if (lines.size() < 3) {
            addIfPresent(lines, "급여: " + salaryLabel(jobPosting));
            addIfPresent(lines, "시간: " + extractWorkTime(jobPosting.getContent()));
            addIfPresent(lines, "지역: " + locationLabel(jobPosting));
        }
        return lines.stream().limit(3).toList();
    }

    private void addSummaryLine(List<String> lines, String label, String value) {
        String cleaned = cleanJsonText(value);
        if (!isBlank(cleaned)) lines.add(label + ": " + cleaned);
    }

    private String cleanJsonText(String value) {
        if (isBlank(value)) return "";
        return value.replace("[", "").replace("]", "").replace("\"", "")
                .replace("{", "").replace("}", "").trim();
    }

    private Coordinate fallbackCoordinateFor(Long id, String dong) {
        int seed = Math.abs((String.valueOf(id) + dong).hashCode());
        double angle = (seed % 360) * Math.PI / 180;
        double radiusKm = 0.6 + (seed % 4300) / 1000.0;
        double lat = AppConstants.DANKOOK_LATITUDE + (Math.cos(angle) * radiusKm / 111.0);
        double lng = AppConstants.DANKOOK_LONGITUDE + (Math.sin(angle) * radiusKm / (111.0 * Math.cos(Math.toRadians(AppConstants.DANKOOK_LATITUDE))));
        return new Coordinate(round(lat), round(lng));
    }

    private double round(double value) {
        return Math.round(value * 1000000.0) / 1000000.0;
    }

    private void addIfPresent(List<String> values, String value) {
        if (!isBlank(value)) values.add(value.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ── 공개 레코드 ──────────────────────────────────────────────────────

    public record JobPostingCard(
            Long id,
            String title,
            String companyName,
            String content,
            String location,
            String dong,
            String sido,
            String sigungu,
            String jobTypeMajor,
            String jobTypeMid,
            String jobType,
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
            boolean scraped          // ← 신규: 로그인 유저의 스크랩 여부
    ) {}

    private record Coordinate(double latitude, double longitude) {}
}
