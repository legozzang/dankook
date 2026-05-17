package kr.ac.dankook.ace.smart_recruit.service.jobposting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.dankook.ace.smart_recruit.model.employer.Address;
import kr.ac.dankook.ace.smart_recruit.model.employer.Employer;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobpostingaisummary.JobPostingAiSummary;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import kr.ac.dankook.ace.smart_recruit.service.location.GeocodingService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private static final double DANKOOK_LATITUDE = 37.3216;
    private static final double DANKOOK_LONGITUDE = 127.1267;
    private static final Pattern SALARY_PATTERN = Pattern.compile("(시급|월급|일급|연봉|급여)\\s*[:：]?\\s*([0-9,]+\\s*(원|만원)?|협의|면접\\s*후\\s*결정)");
    private static final Pattern WORK_TIME_PATTERN = Pattern.compile("((근무\\s*시간|근무시간|시간)\\s*[:：]?\\s*)?([0-2]?\\d\\s*[:시]\\s*\\d{0,2}\\s*~\\s*[0-2]?\\d\\s*[:시]\\s*\\d{0,2}|오전\\s*\\d+\\s*시\\s*~\\s*오후\\s*\\d+\\s*시|주\\s*\\d+\\s*일|요일\\s*협의)");

    private final JobPostingRepository jobPostingRepository;
    private final GeocodingService geocodingService;

    public List<JobPosting> findAll() {
        return jobPostingRepository.findAllWithEmployerOrderByCreatedAtDesc();
    }

    @Transactional
    public List<JobPostingCard> findAllCards() {
        return jobPostingRepository.findAllWithEmployerAndAiSummaryOrderByCreatedAtDesc().stream()
                .map(this::toCard)
                .toList();
    }

    @Transactional
    public Optional<JobPostingCard> findCardById(Long id) {
        return jobPostingRepository.findByIdWithEmployerAndAiSummary(id).map(this::toCard);
    }

    private JobPostingCard toCard(JobPosting jobPosting) {
        String location = locationLabel(jobPosting);
        String dong = dongLabel(jobPosting, location);
        resolveMissingCoordinate(jobPosting, location);
        boolean hasExactLocation = hasCoordinate(jobPosting);
        Coordinate coordinate = hasExactLocation
                ? new Coordinate(jobPosting.getLatitude(), jobPosting.getLongitude())
                : fallbackCoordinateFor(jobPosting.getId(), dong);

        return new JobPostingCard(
                jobPosting.getId(),
                jobPosting.getTitle(),
                companyName(jobPosting.getEmployer()),
                jobPosting.getContent(),
                location,
                dong,
                jobPosting.getJobType(),
                jobPosting.getStatus().name(),
                deadlineLabel(jobPosting.getDeadline()),
                extractSalary(jobPosting.getContent()),
                extractWorkTime(jobPosting.getContent()),
                summaryLines(jobPosting),
                jobPosting.getExternalUrl(),
                coordinate.latitude(),
                coordinate.longitude(),
                hasExactLocation
        );
    }

    private void resolveMissingCoordinate(JobPosting jobPosting, String location) {
        if (hasCoordinate(jobPosting) || isBlank(location) || "위치 미등록".equals(location)) {
            return;
        }

        geocodingService.geocode(location)
                .ifPresent(coordinate -> jobPosting.updateLocation(coordinate.latitude(), coordinate.longitude()));
    }

    private boolean hasCoordinate(JobPosting jobPosting) {
        return jobPosting.getLatitude() != null && jobPosting.getLongitude() != null;
    }

    private String companyName(Employer employer) {
        if (employer == null || isBlank(employer.getCompanyName())) {
            return "회사명 미등록";
        }
        return employer.getCompanyName();
    }

    private String locationLabel(JobPosting jobPosting) {
        Employer employer = jobPosting.getEmployer();
        if (employer != null && employer.getAddress() != null) {
            Address address = employer.getAddress();
            List<String> parts = new ArrayList<>();
            addIfPresent(parts, address.getCity());
            addIfPresent(parts, address.getDistrict());
            addIfPresent(parts, address.getDetailAddress());
            if (!parts.isEmpty()) {
                return String.join(" ", parts);
            }
        }
        if (!isBlank(jobPosting.getRegion())) {
            return jobPosting.getRegion();
        }
        return "위치 미등록";
    }

    private String dongLabel(JobPosting jobPosting, String location) {
        String source = !isBlank(jobPosting.getRegion()) ? jobPosting.getRegion() : location;
        if (isBlank(source)) {
            return "기타";
        }

        for (String token : source.split("\\s+")) {
            if (token.endsWith("동") || token.endsWith("읍") || token.endsWith("면")) {
                return token;
            }
        }
        return source.split("\\s+")[0];
    }

    private String deadlineLabel(String deadline) {
        return isBlank(deadline) ? "마감일 미정" : deadline;
    }

    private String extractSalary(String content) {
        return extract(content, SALARY_PATTERN, 0, "급여 협의");
    }

    private String extractWorkTime(String content) {
        return extract(content, WORK_TIME_PATTERN, 0, "근무시간 협의");
    }

    private String extract(String content, Pattern pattern, int group, String fallback) {
        if (isBlank(content)) {
            return fallback;
        }
        Matcher matcher = pattern.matcher(content.replaceAll("\\s+", " "));
        if (matcher.find()) {
            return matcher.group(group).trim();
        }
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
            addIfPresent(lines, "급여: " + extractSalary(jobPosting.getContent()));
            addIfPresent(lines, "시간: " + extractWorkTime(jobPosting.getContent()));
            addIfPresent(lines, "지역: " + locationLabel(jobPosting));
        }

        return lines.stream().limit(3).toList();
    }

    private void addSummaryLine(List<String> lines, String label, String value) {
        String cleaned = cleanJsonText(value);
        if (!isBlank(cleaned)) {
            lines.add(label + ": " + cleaned);
        }
    }

    private String cleanJsonText(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .replace("{", "")
                .replace("}", "")
                .trim();
    }

    private Coordinate fallbackCoordinateFor(Long id, String dong) {
        int seed = Math.abs((String.valueOf(id) + dong).hashCode());
        double angle = (seed % 360) * Math.PI / 180;
        double radiusKm = 0.6 + (seed % 4300) / 1000.0;
        double lat = DANKOOK_LATITUDE + (Math.cos(angle) * radiusKm / 111.0);
        double lng = DANKOOK_LONGITUDE + (Math.sin(angle) * radiusKm / (111.0 * Math.cos(Math.toRadians(DANKOOK_LATITUDE))));
        return new Coordinate(round(lat), round(lng));
    }

    private double round(double value) {
        return Math.round(value * 1000000.0) / 1000000.0;
    }

    private void addIfPresent(List<String> values, String value) {
        if (!isBlank(value)) {
            values.add(value.trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record JobPostingCard(
            Long id,
            String title,
            String companyName,
            String content,
            String location,
            String dong,
            String jobType,
            String status,
            String deadline,
            String salary,
            String workTime,
            List<String> summaryLines,
            String externalUrl,
            double latitude,
            double longitude,
            boolean exactLocation
    ) {}

    private record Coordinate(double latitude, double longitude) {}
}
