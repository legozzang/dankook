package kr.ac.dankook.ace.smart_recruit.model.scrap;

import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "scrap",
    uniqueConstraints = {
        @UniqueConstraint( // 이 테이블에는 유니크해야하는 조합이 있다 
            name = "uk_scrap_member_posting", 
            columnNames = {"member_id", "posting_id"} // 두 Column을 묶어 해당 조합이 단 하나만 존재해야 한다는 뜻
        )
    }
)
public class Scrap {
    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = java.time.LocalDateTime.now();
    }

    // 사용자 정의 생성자
    public Scrap(Member member, JobPosting jobPosting) {
        this.member = member;
        this.jobPosting = jobPosting;
    }

}