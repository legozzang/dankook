package kr.ac.dankook.ace.smart_recruit.service.recommendation;

import java.util.ArrayList;
import java.util.Iterator;
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

@Service
public class GeminiService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemma-4-26b-a4b-it:generateContent?key=";
    private static final int BATCH_SIZE = 20;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<Long, String> callGemini(String apiKey, List<JobPosting> postings) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (isBlank(apiKey) || postings == null || postings.isEmpty()) {
            return result;
        }

        for (int start = 0; start < postings.size(); start += BATCH_SIZE) {
            List<JobPosting> batch = postings.subList(start, Math.min(start + BATCH_SIZE, postings.size()));
            result.putAll(callBatch(apiKey, batch));
        }
        return result;
    }

    private Map<Long, String> callBatch(String apiKey, List<JobPosting> batch) {
        try {
            String prompt = buildPrompt(batch);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "system_instruction", Map.of(
                            "parts", List.of(Map.of("text",
                                    "너는 채용공고 추천 이유를 작성하는 도우미야. "
                                            + "반드시 한국어로, 각 이유는 30자 이내로, "
                                            + "마크다운/설명/주석 없이, 오직 JSON만 출력해라. "
                                            + "형식: {\"1\":\"이유\",\"2\":\"이유\",...}"
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
            Map<String, String> parsed = parseReasonJson(cleanJsonText(text));
            Map<Long, String> reasons = new LinkedHashMap<>();
            for (int i = 0; i < batch.size(); i++) {
                String reason = parsed.get(String.valueOf(i + 1));
                if (!isBlank(reason)) {
                    reasons.put(batch.get(i).getId(), reason.trim());
                }
            }
            return reasons;
        } catch (Exception e) {
            System.err.println("[GeminiService] callBatch 실패: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return Map.of();
        }
    }

    private String previewBody(String body) {
        return body != null ? body.substring(0, Math.min(500, body.length())) : "null";
    }

    private String buildPrompt(List<JobPosting> postings) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < postings.size(); i++) {
            JobPosting posting = postings.get(i);
            lines.add((i + 1) + ". 제목: " + value(posting.getTitle())
                    + ", 직종: " + value(posting.getJobTypeDetail())
                    + ", 급여: " + value(posting.getPayType()) + " " + value(posting.getPayAmount())
                    + ", 복지: " + value(posting.getWelfare())
                    + ", 지역: " + value(posting.getRegionSido()));
        }

        return "채용공고 목록:\n" + String.join("\n", lines);
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

    private Map<String, String> parseReasonJson(String text) throws Exception {
        Map<String, String> reasons = new LinkedHashMap<>();
        JsonNode root = objectMapper.readTree(text);
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            reasons.put(field.getKey(), field.getValue().asText(""));
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
        return cleaned.trim();
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
