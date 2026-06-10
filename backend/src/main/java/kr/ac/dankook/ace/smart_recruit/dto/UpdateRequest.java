package kr.ac.dankook.ace.smart_recruit.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateRequest {
    private String currentPassword;

    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
        message = "새 비밀번호는 8~20자이며, 영문, 숫자, 특수문자를 포함해야 합니다."
    )
    private String newPassword;
    
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]{2,10}$", message = "닉네임은 특수문자를 제외한 2~10자여야 합니다.")
    private String nickname;

    private String desiredRegionSido;
    private String desiredRegionSigungu;
    private String desiredRegionDong;
    private String homeAddress;
    private String desiredRegion2Sido;
    private String desiredRegion2Sigungu;
    private String desiredRegion3Sido;
    private String desiredRegion3Sigungu;
    private String preferredJobTypeMajor;
    private String preferredJobTypeMid;
    private String preferredJobTypeMinor;
    private String preferredJobTypeDetail;
    private String preferredPayType;
    private Integer minPayAmount;
    private Boolean emailNotification;
}
