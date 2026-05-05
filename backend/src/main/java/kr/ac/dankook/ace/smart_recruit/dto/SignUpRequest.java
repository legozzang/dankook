package kr.ac.dankook.ace.smart_recruit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignUpRequest {
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
        message = "비밀번호는 8~20자이며, 영문 대소문자, 숫자, 특수문자를 각각 최소 1개 이상 포함해야 합니다."
    )
    private String password;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Pattern(
        regexp = "^[a-zA-Z0-9가-힣]{2,10}$", 
        message = "닉네임은 특수문자를 제외한 2~10자여야 합니다."
    )
    private String nickname;

    @NotBlank(message = "역할(Role)을 입력해주세요.") // null과 빈 문자열 모두 방지
    @Pattern(
        regexp = "^(SEEKER|EMPLOYER)$",
        message = "역할(Role)은 'SEEKER' 또는 'EMPLOYER'여야 합니다."
    )
    private String role; // "SEEKER" 또는 "EMPLOYER"
}
