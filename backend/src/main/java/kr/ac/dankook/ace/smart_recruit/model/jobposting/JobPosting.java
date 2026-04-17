package kr.ac.dankook.ace.smart_recruit.model.jobposting;

import java.time.LocalDateTime;
import java.util.*;

import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.employer.Employer;
import kr.ac.dankook.ace.smart_recruit.model.jobpostingaisummary.JobPostingAiSummary;
import kr.ac.dankook.ace.smart_recruit.model.scrap.Scrap;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 롬복이 만들어주는 protected 생성자는 JPA가 객체를 생성하기에 충분
@Entity
@Table(
    name = "job_posting",
    indexes = {
        @Index(name = "idx_job_posting_region", columnList = "region"),
        @Index(name = "idx_job_posting_job_type", columnList = "job_type")
    }
)
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // company_id (FK)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Employer employer;

    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    @OneToOne(mappedBy = "jobPosting")
    private JobPostingAiSummary jobPostingAiSummary;

    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    // CascadeType은 영속성 전이로 부모 엔티티를 저장하거나 삭제할 때 그 부모와 연결된 자식 엔티티들도 같이 처리할지 결정하는 옵션
    // 부모 엔티티와 자식 엔티티 사이의 견결이 끊어진 자식을 자동으로 삭제하는 옵션
    // jobPosting이 사라지면 공고를 가지고 있던 Scrap도 사라진다
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Scrap> scraps = new ArrayList<>();

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(length = 100)
    private String region;

    @Column(name = "job_type", length = 100)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    // (예: 2003-08-10)
    @Column(length = 10)
    private String deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private JobSourceType sourceType;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 생성자
    public JobPosting(
            Employer employer,
            String title,
            String content,
            String region,
            String jobType,
            JobStatus status,
            String deadline,
            JobSourceType sourceType,
            String externalUrl
    ) {
        this.employer = employer;
        this.title = title;
        this.content = content;
        this.region = region;
        this.jobType = jobType;
        this.status = status;
        this.deadline = deadline;
        this.sourceType = sourceType;
        this.externalUrl = externalUrl;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
