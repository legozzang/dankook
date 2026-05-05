package kr.ac.dankook.ace.smart_recruit.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateRequest {
    private String currentPassword;
    private String newPassword;
    private String nickname;
}
