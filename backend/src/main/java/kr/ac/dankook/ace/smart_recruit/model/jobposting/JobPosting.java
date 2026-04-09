package kr.ac.dankook.ace.smart_recruit.model.jobposting;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.employer.Employer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
