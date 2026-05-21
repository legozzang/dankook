package kr.ac.dankook.ace.smart_recruit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberInfoResponse {
    private String email;
    private String nickname;
    private String desiredRegionSido;
    private String desiredRegionSigungu;
    private String desiredRegionDong;
    private String desiredRegion2Sido;
    private String desiredRegion2Sigungu;
    private String desiredRegion2Dong;
    private String desiredRegion3Sido;
    private String desiredRegion3Sigungu;
    private String desiredRegion3Dong;
    private String preferredJobTypeMajor;
    private String preferredJobTypeMid;
    private String preferredJobTypeMinor;
    private String preferredJobTypeDetail;
    private String preferredPayType;
    private Integer minPayAmount;
    private Boolean emailNotification;
}
