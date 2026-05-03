package kr.ac.dankook.ace.smart_recruit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignUpRequest {
    private String email;
    private String password;
    private String nickname;
    private String role; // "SEEKER" 또는 "EMPLOYER"
}
