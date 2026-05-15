package kr.ac.dankook.ace.smart_recruit.model.member;

import java.time.LocalDateTime;
import java.util.*;

import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.community.Community;
import kr.ac.dankook.ace.smart_recruit.model.communitycomment.CommunityComment;
import kr.ac.dankook.ace.smart_recruit.model.employer.Employer;
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
    
    // DB 테이블에는 아래 Column이 생기지 않음 자바 객체 세상에만 존재하는 필드
    // 논리적으로 나를 참조하는 곳은 member라는 곳이야 라고 알려줌
    // 내부적으로 일어나는 일 (비하인드 스토리)
    // 개발자가 자바 코드에서 member.getEmployer()를 호출하는 순간, JPA는 다음과 같은 논리로 동작
    // "오, 이 Member 객체의 ID가 1이네?" (메모리 확인)
    // "근데 이 녀석은 Employer랑 1:1 관계고, 주인은 Employer네?" (mappedBy 설정 확인)
    // "그럼 employers 테이블에 가서 member_id가 1인 데이터를 찾아오면 되겠군!"
    // SELECT * FROM employers WHERE member_id = 1; (실제 SQL 실행)
    // 그래서 실제론 employerId가 뭔지 몰라야하지만 연관관계 매핑을 통해 Id만 가지고 employerId를 알 수 있음 (JPA덕분)
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private Employer employer; // 다리: "나랑 연결된 업체는 여기야"

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
}