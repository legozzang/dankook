package kr.ac.dankook.ace.smart_recruit.dto;

import lombok.*;

@Getter
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private Long memberId;
    private String email;
    private String role;
}
