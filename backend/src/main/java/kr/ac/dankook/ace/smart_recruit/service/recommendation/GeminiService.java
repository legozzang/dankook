package kr.ac.dankook.ace.smart_recruit.service.recommendation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;

@Service
public class GeminiService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemma-4-26b-a4b-it:generateContent?key=";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<Long, String> callGemini(String apiKey, Member member, List<JobPosting> postings) {
        if (isBlank(apiKey) || postings == null || postings.isEmpty()) {
            return Map.of();
        }

        try {
            String prompt = buildPrompt(member, postings);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "system_instruction", Map.of(
                            "parts", List.of(Map.of("text",
                                    "너는 구직자 맞춤 채용공고 추천 도우미야.\n"
                                            + "주어진 공고 목록에서 구직자 선호 조건에 가장 잘 맞는 공고를 최대 10개 선택하고,\n"
                                            + "각 선택 이유를 30자 이내 한국어로 작성해.\n"
                                            + "마크다운·설명·주석 없이 JSON 배열만 출력해.\n"
                                            + "형식: [{\"idx\":1,\"reason\":\"이유\"}, ...]"
                            ))
                    ),
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    ))
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_URL + apiKey, entity, String.class);
            System.out.println("[GeminiService] 응답 status=" + response.getStatusCode()
                    + " body 앞500=" + previewBody(response.getBody()));

            String text = extractText(response.getBody());
            return parseReasonJson(cleanJsonText(text), postings);
        } catch (Exception e) {
            System.err.println("[GeminiService] callGemini 실패: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return Map.of();
        }
    }

    private String previewBody(String body) {
        return body != null ? body.substring(0, Math.min(500, body.length())) : "null";
    }

    private String buildPrompt(Member member, List<JobPosting> postings) {
        List<String> lines = new ArrayList<>();
        lines.add("[구직자 선호]");
        lines.add("선호 직종: " + preferredJobType(member));
        lines.add("희망 지역: " + preferredRegions(member));
        lines.add("급여 형태: " + defaultIfBlank(member.getPreferredPayType(), "무관"));
        lines.add("");
        lines.add("[채용공고 목록]");

        for (int i = 0; i < postings.size(); i++) {
            JobPosting posting = postings.get(i);
            lines.add((i + 1) + ". 제목: " + value(posting.getTitle())
                    + " | 회사: " + value(posting.getCompany())
                    + " | 직종: " + postingJobType(posting)
                    + " | 급여: " + salary(posting)
                    + " | 지역: " + region(posting.getRegionSido(), posting.getRegionSigungu())
                    + " | 마감: " + value(posting.getDeadline())
                    + " | 복지: " + truncate(posting.getWelfare(), 100));
        }

        return String.join("\n", lines);
    }

    private String extractText(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        try {
            JsonNode parts = objectMapper.readTree(responseBody)
                    .at("/candidates/0/content/parts");
            if (!parts.isArray()) {
                return "";
            }
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) {
                    continue;
                }
                String text = part.path("text").asText("");
                if (!text.isBlank()) {
                    return text;
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private Map<Long, String> parseReasonJson(String text, List<JobPosting> postings) throws Exception {
        Map<Long, String> reasons = new LinkedHashMap<>();
        JsonNode root = objectMapper.readTree(text);
        if (!root.isArray()) {
            return reasons;
        }

        for (JsonNode item : root) {
            int idx = item.path("idx").asInt(0);
            String reason = item.path("reason").asText("");
            if (idx < 1 || idx > postings.size() || isBlank(reason)) {
                continue;
            }
            reasons.put(postings.get(idx - 1).getId(), reason.trim());
        }
        return reasons;
    }

    private String cleanJsonText(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```json\\s*", "")
                    .replaceFirst("^```\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        int start = cleaned.indexOf("[");
        int end = cleaned.lastIndexOf("]");
        if (start >= 0 && end >= start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return cleaned.trim();
    }

    private String preferredJobType(Member member) {
        String major = value(member.getPreferredJobTypeMajor());
        String mid = value(member.getPreferredJobTypeMid());
        if (isBlank(major) && isBlank(mid)) {
            return "무관";
        }
        if (isBlank(major)) {
            return mid;
        }
        if (isBlank(mid)) {
            return major;
        }
        return major + " > " + mid;
    }

    private String preferredRegions(Member member) {
        List<String> regions = new ArrayList<>();
        addRegion(regions, member.getDesiredRegionSido(), member.getDesiredRegionSigungu());
        addRegion(regions, member.getDesiredRegion2Sido(), member.getDesiredRegion2Sigungu());
        addRegion(regions, member.getDesiredRegion3Sido(), member.getDesiredRegion3Sigungu());
        return regions.isEmpty() ? "무관" : String.join(", ", regions);
    }

    private void addRegion(List<String> regions, String sido, String sigungu) {
        String region = !isBlank(sigungu) ? sigungu.trim() : value(sido);
        if (!isBlank(region)) {
            regions.add(region);
        }
    }

    private String postingJobType(JobPosting posting) {
        String major = value(posting.getJobTypeMajor());
        String mid = value(posting.getJobTypeMid());
        if (!isBlank(major) && !isBlank(mid)) {
            return major + " > " + mid;
        }
        if (!isBlank(major)) {
            return major;
        }
        if (!isBlank(mid)) {
            return mid;
        }
        return value(posting.getJobTypeDetail());
    }

    private String salary(JobPosting posting) {
        String payType = value(posting.getPayType());
        String payAmount = posting.getPayAmount() == null ? "" : posting.getPayAmount() + "원";
        String salary = (payType + " " + payAmount).trim();
        return isBlank(salary) ? "미정" : salary;
    }

    private String region(String sido, String sigungu) {
        String region = (value(sido) + " " + value(sigungu)).trim();
        return isBlank(region) ? "미정" : region;
    }

    private String truncate(String value, int maxLength) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) + "…" : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
