package kr.ac.dankook.ace.smart_recruit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberInfoResponse {
    public MemberInfoResponse(String email2, String nickname2, String desiredRegionSido2, String desiredRegionSigungu2,
            String desiredRegionDong2, String desiredRegion2Sido2, String desiredRegion2Sigungu2,
            String desiredRegion3Sido2, String desiredRegion3Sigungu2, String preferredJobTypeMajor2,
            String preferredJobTypeMid2, String preferredJobTypeMinor2, String preferredJobTypeDetail2,
            String preferredPayType2, Integer minPayAmount2, boolean emailNotification2, String geminiApiKey2,
            Integer recommendationIntervalHours2, String recommendationCustomPrompt) {
        //TODO Auto-generated constructor stub
    }
    private String email;
    private String nickname;
    private String desiredRegionSido;
    private String desiredRegionSigungu;
    private String desiredRegionDong;
    private String homeAddress;
    private Double homeLatitude;
    private Double homeLongitude;
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
    private String geminiApiKey;
    private Integer recommendationIntervalHours;
    private String recommendationCustomPrompt;
}
