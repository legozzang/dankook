package kr.ac.dankook.ace.smart_recruit.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GeminiSettingsRequest {
    private String geminiApiKey;
    private Integer intervalHours;
}
