package kr.ac.dankook.ace.smart_recruit.model.member;

import java.time.LocalDateTime;
import java.util.*;

import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.community.Community;
import kr.ac.dankook.ace.smart_recruit.model.communitycomment.CommunityComment;
import kr.ac.dankook.ace.smart_recruit.model.postingcomment.PostingComment;
import kr.ac.dankook.ace.smart_recruit.model.scrap.Scrap;
import lombok.*;
@Getter //getter 생성
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 무분별한 생성을 차단
@Entity // 이 클래스를 엔티티로 사용하겠다는 뜻
@Table(name = "members")
public class Member{
    @Id // PK
    @GeneratedValue(strategy = GenerationType.IDENTITY) // PK생성 전략을 데이터베이스에 위임(MySQL의 AUTO_INCREMENT 사용)
    private Long id;
    
    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostingComment> postingComments = new ArrayList<>();

    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    // CascadeType은 영속성 전이로 부모 엔티티를 저장하거나 삭제할 때 그 부모와 연결된 자식 엔티티들도 같이 처리할지 결정하는 옵션
    // 부모 엔티티와 자식 엔티티 사이의 견결이 끊어진 자식을 자동으로 삭제하는 옵션
    // Member가 사라지면 공고를 가지고 있던 Scrap도 사라진다
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Scrap> scraps = new ArrayList<>();

    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommunityComment> communityComments = new ArrayList<>();

    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Community> communities = new ArrayList<>();

    @Column(name = "email", nullable = false, unique =true, length = 100) // attribute에 대한 설정
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    // Enum 타입이고 데이터의 타입이 문자열이라는 것을 알려줘야함
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "created_at",updatable = false) // Column 이름을 따로 설정, 설정하지 않으면 변수이름 따라감
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "desired_region_sido", length = 50)
    private String desiredRegionSido;

    @Column(name = "desired_region_sigungu", length = 50)
    private String desiredRegionSigungu;

    @Column(name = "desired_region_dong", length = 50)
    private String desiredRegionDong;

    @Column(name = "desired_region2_sido", length = 50)
    private String desiredRegion2Sido;

    @Column(name = "desired_region2_sigungu", length = 50)
    private String desiredRegion2Sigungu;

    @Column(name = "desired_region3_sido", length = 50)
    private String desiredRegion3Sido;

    @Column(name = "desired_region3_sigungu", length = 50)
    private String desiredRegion3Sigungu;

    @Column(name = "preferred_job_type_major", length = 100)
    private String preferredJobTypeMajor;

    @Column(name = "preferred_job_type_mid", length = 100)
    private String preferredJobTypeMid;

    @Column(name = "preferred_job_type_minor", length = 100)
    private String preferredJobTypeMinor;

    @Column(name = "preferred_job_type_detail", length = 100)
    private String preferredJobTypeDetail;

    @Column(name = "preferred_pay_type", length = 20)
    private String preferredPayType;

    @Column(name = "min_pay_amount")
    private Integer minPayAmount;

    @Column(name = "email_notification", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean emailNotification = false;

    // 아래 두 어노테이션으로 시간 자동 입력
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 생성자(사용자 정의)
    public Member(String email, String password, Role role, String nickname){
        this.email = email;
        this.password = password;
        this.role = role;
        this.nickname = nickname;
    }

    // setter 역할
    public void changeNickname(String newNickname){
        this.nickname = newNickname;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateRole(Role role) {
        this.role = role;
    }

    public void updateEmailNotification(Boolean emailNotification) {
        if (emailNotification != null) {
            this.emailNotification = emailNotification;
        }
    }

    public void updatePreferences(
            String desiredRegionSido,
            String desiredRegionSigungu,
            String desiredRegionDong,
            String desiredRegion2Sido,
            String desiredRegion2Sigungu,
            String desiredRegion3Sido,
            String desiredRegion3Sigungu,
            String preferredJobTypeMajor,
            String preferredJobTypeMid,
            String preferredJobTypeMinor,
            String preferredJobTypeDetail,
            String preferredPayType,
            Integer minPayAmount
    ) {
        if (desiredRegionSido != null) this.desiredRegionSido = desiredRegionSido;
        if (desiredRegionSigungu != null) this.desiredRegionSigungu = desiredRegionSigungu;
        if (desiredRegionDong != null) this.desiredRegionDong = desiredRegionDong;
        if (desiredRegion2Sido != null) this.desiredRegion2Sido = desiredRegion2Sido;
        if (desiredRegion2Sigungu != null) this.desiredRegion2Sigungu = desiredRegion2Sigungu;
        if (desiredRegion3Sido != null) this.desiredRegion3Sido = desiredRegion3Sido;
        if (desiredRegion3Sigungu != null) this.desiredRegion3Sigungu = desiredRegion3Sigungu;
        if (preferredJobTypeMajor != null) this.preferredJobTypeMajor = preferredJobTypeMajor;
        if (preferredJobTypeMid != null) this.preferredJobTypeMid = preferredJobTypeMid;
        if (preferredJobTypeMinor != null) this.preferredJobTypeMinor = preferredJobTypeMinor;
        if (preferredJobTypeDetail != null) this.preferredJobTypeDetail = preferredJobTypeDetail;
        if (preferredPayType != null) this.preferredPayType = preferredPayType;
        if (minPayAmount != null) this.minPayAmount = minPayAmount;
    }
}
