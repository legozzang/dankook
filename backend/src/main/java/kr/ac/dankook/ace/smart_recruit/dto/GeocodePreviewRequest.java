package kr.ac.dankook.ace.smart_recruit.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입·수정 화면에서 거주지 상세주소를 지오코딩 미리보기할 때의 요청.
 */
@Getter
@NoArgsConstructor
public class GeocodePreviewRequest {
    private String sido;
    private String sigungu;
    private String detail;
}
