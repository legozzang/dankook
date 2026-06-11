package kr.ac.dankook.ace.smart_recruit.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import kr.ac.dankook.ace.smart_recruit.util.JsonViewUtils;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final JobPostingRepository jobPostingRepository;

    @GetMapping
    public String dashboard(
            @RequestParam(required = false) String regionSido,
            @RequestParam(required = false) String regionSigungu,
            @RequestParam(required = false) String jobTypeMajor,
            @RequestParam(required = false) String jobTypeMid,
            @RequestParam(required = false) String payType,
            Model model
    ) {
        String selectedSido = nvl(regionSido);
        String selectedSigungu = nvl(regionSigungu);
        String selectedMajor = nvl(jobTypeMajor);
        String selectedMid = nvl(jobTypeMid);
        String selectedPayType = nvl(payType);

        String sidoParam = nullable(selectedSido);
        String sigunguParam = nullable(selectedSigungu);
        String majorParam = nullable(selectedMajor);
        String midParam = nullable(selectedMid);
        String payTypeParam = nullable(selectedPayType);

        List<Object[]> regionRaw = jobPostingRepository.countGroupByRegion();
        List<Object[]> allJobTypeRaw = jobPostingRepository.countGroupByJobType(null, null, null);
        List<Object[]> allPayRaw = jobPostingRepository.avgPayGroupByType(null, null, null, null, null);
        List<Object[]> jobTypeRaw = jobPostingRepository.countGroupByJobType(sidoParam, sigunguParam, payTypeParam);
        List<Object[]> payRaw = jobPostingRepository.avgPayGroupByType(sidoParam, sigunguParam, majorParam, midParam, payTypeParam);
        List<Object[]> jobPayRaw = payTypeParam == null
                ? List.of()
                : jobPostingRepository.avgPayByJobType(sidoParam, sigunguParam, payTypeParam);
        List<Object[]> regionPayRaw = payTypeParam == null
                ? List.of()
                : jobPostingRepository.avgPayByRegion(majorParam, midParam, payTypeParam);

        model.addAttribute("totalJobPostings", jobPostingRepository.count());
        boolean anyFilterActive = !selectedSido.isBlank()
                || !selectedSigungu.isBlank()
                || !selectedMajor.isBlank()
                || !selectedMid.isBlank()
                || !selectedPayType.isBlank();
        Long filteredCount = anyFilterActive
                ? jobPostingRepository.countByAllFilters(sidoParam, sigunguParam, majorParam, midParam, payTypeParam)
                : null;
        model.addAttribute("filteredCount", filteredCount);
        model.addAttribute("openCount", jobPostingRepository.countByStatus(JobStatus.OPEN));
        model.addAttribute("closedCount", jobPostingRepository.countByStatus(JobStatus.CLOSED));
        model.addAttribute("regionStats", regionStats(regionRaw, 20));
        model.addAttribute("jobTypeStats", jobTypeStats(jobTypeRaw));
        model.addAttribute("payStats", payStats(payRaw));
        model.addAttribute("jobPayStats", averagePayRanking(jobPayRaw));
        model.addAttribute("regionPayStats", averagePayRanking(regionPayRaw));
        model.addAttribute("payTypeList", payTypeList(allPayRaw));
        addCascadeModel(model, regionRaw, allJobTypeRaw);
        model.addAttribute("selectedSido", selectedSido);
        model.addAttribute("selectedSigungu", selectedSigungu);
        model.addAttribute("selectedMajor", selectedMajor);
        model.addAttribute("selectedMid", selectedMid);
        model.addAttribute("selectedPayType", selectedPayType);
        model.addAttribute("dashboardFilterLabel", filterLabel(selectedSido, selectedSigungu, selectedMajor, selectedMid, selectedPayType));

        return "dashboard/index";
    }

    private void addCascadeModel(Model model, List<Object[]> regionRaw, List<Object[]> jobTypeRaw) {
        model.addAttribute("sidoList", List.of(
                "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
                "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"));
        model.addAttribute("majorList", majorList(jobTypeRaw));
        model.addAttribute("sigunguBySidoJson", JsonViewUtils.toJson(sigunguBySido(regionRaw)));
        model.addAttribute("midByMajorJson", JsonViewUtils.toJson(midByMajor(jobTypeRaw)));
    }

    private Map<String, Long> regionStats(List<Object[]> rows, int limit) {
        return rows.stream()
                .limit(limit)
                .collect(Collectors.toMap(
                        row -> label(row[0], row[1]),
                        row -> (Long) row[2],
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Long> jobTypeStats(List<Object[]> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> label(row[0], row[1]),
                        row -> (Long) row[2],
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Double> payStats(List<Object[]> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row[0]),
                        row -> ((Number) row[1]).doubleValue(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<String> payTypeList(List<Object[]> rows) {
        return rows.stream()
                .map(row -> nvl(row[0]))
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<Map<String, Object>> averagePayRanking(List<Object[]> rows) {
        long maxPay = rows.stream()
                .mapToLong(row -> ((Number) row[2]).longValue())
                .max()
                .orElse(1L);

        return rows.stream()
                .map(row -> {
                    long avgPay = ((Number) row[2]).longValue();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", label(row[0], row[1]));
                    item.put("avgPay", avgPay);
                    item.put("count", row[3]);
                    item.put("barPct", Math.max(1L, avgPay * 100L / maxPay));
                    return item;
                })
                .toList();
    }

    private Map<String, List<String>> sigunguBySido(List<Object[]> rows) {
        Map<String, TreeSet<String>> grouped = new TreeMap<>();
        for (Object[] row : rows) {
            String sido = nvl(row[0]);
            String sigungu = nvl(row[1]);
            if (!sido.isBlank() && !sigungu.isBlank()) {
                grouped.computeIfAbsent(sido, ignored -> new TreeSet<>()).add(sigungu);
            }
        }
        return toListMap(grouped);
    }

    private Map<String, List<String>> midByMajor(List<Object[]> rows) {
        Map<String, TreeSet<String>> grouped = new TreeMap<>();
        for (Object[] row : rows) {
            String major = nvl(row[0]);
            String mid = nvl(row[1]);
            if (!major.isBlank() && !mid.isBlank()) {
                grouped.computeIfAbsent(major, ignored -> new TreeSet<>()).add(mid);
            }
        }
        return toListMap(grouped);
    }

    private Map<String, List<String>> toListMap(Map<String, TreeSet<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, values) -> result.put(key, List.copyOf(values)));
        return result;
    }

    private List<String> majorList(List<Object[]> rows) {
        return rows.stream()
                .map(row -> nvl(row[0]))
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private String filterLabel(String... values) {
        return List.of(values).stream()
                .map(this::nvl)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" · "));
    }

    private String label(Object first, Object second) {
        String primary = nvl(first);
        String secondary = nvl(second);
        if (secondary.isBlank()) return primary;
        return primary + " · " + secondary;
    }

    private String nullable(String value) {
        String normalized = nvl(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String nvl(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
