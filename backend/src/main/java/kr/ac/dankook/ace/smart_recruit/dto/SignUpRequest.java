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
        regexp = "^(SEEKER)$",
        message = "역할(Role)은 'SEEKER'여야 합니다."
    )
    private String role; // "SEEKER"

    @NotBlank(message = "관심 지역 1의 시/도를 선택해주세요.")
    private String desiredRegionSido;

    @NotBlank(message = "관심 지역 1의 시/군/구를 선택해주세요.")
    private String desiredRegionSigungu;

    // 관심 지역 1의 상세 주소(도로명/지번) — 지오코딩하여 거주지 좌표로 저장
    @NotBlank(message = "관심 지역 1의 상세 주소를 입력해주세요.")
    private String homeAddress;

    private String desiredRegion2Sido;

    private String desiredRegion2Sigungu;

    private String desiredRegion3Sido;

    private String desiredRegion3Sigungu;
}
