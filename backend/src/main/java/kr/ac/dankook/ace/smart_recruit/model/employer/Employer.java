package kr.ac.dankook.ace.smart_recruit.model.employer;

import java.util.*;

import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "employers")
public class Employer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY) // 지연 방식(안전성)
    @JoinColumn(name = "member_id", nullable = false) // Member의 PK를 참조하여 member_id에 넣음, 데이터의 주인, 연관관계의 주인
    private Member member;

    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    @OneToMany(mappedBy = "employer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPosting> jobPostings = new ArrayList<>();

    @Column(name = "company_name", length = 50)
    private String companyName;

    @Column(name = "business_number", length = 20, unique = true)
    private String businessNumber;

    @Column(name = "ai_created")
    private Boolean aiCreated = false;

    @Embedded // Address 객체의 필드들이 companies 테이블의 컬럼으로 들어감
    private Address address;
}
