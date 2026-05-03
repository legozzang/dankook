package kr.ac.dankook.ace.smart_recruit.dto;

import lombok.*;

@Getter
@NoArgsConstructor
public class LoginRequest {
    private String email;
    private String password;
}