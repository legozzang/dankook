package kr.ac.dankook.ace.smart_recruit.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;
@Getter //getter 생성
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
    // 개발자가 자바 코드에서 member.getCompany()를 호출하는 순간, JPA는 다음과 같은 논리로 동작
    // "오, 이 Member 객체의 ID가 1이네?" (메모리 확인)
    // "근데 이 녀석은 Company랑 1:1 관계고, 주인은 Company네?" (mappedBy 설정 확인)
    // "그럼 companies 테이블에 가서 member_id가 1인 데이터를 찾아오면 되겠군!"
    // SELECT * FROM companies WHERE member_id = 1; (실제 SQL 실행)
    // 그래서 실제론 companyId가 뭔지 몰라야하지만 연관관계 매핑을 통해 Id만 가지고 companyId를 알 수 있음 (JPA덕분)
    @OneToOne(mappedBy = "member")
    private Company company; // 다리: "나랑 연결된 업체는 여기야"

    @Column(nullable = false, unique =true, length = 100) // attribute에 대한 설정
    private String email;

    @Column(nullable = false)
    private String password;

    // Enum 타입이고 데이터의 타입이 문자열이라는 것을 알려줘야함
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 50)
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
}