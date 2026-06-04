package kr.ac.dankook.ace.smart_recruit.model.jobposting;

import java.time.LocalDateTime;
import java.util.*;

import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.jobpostingaisummary.JobPostingAiSummary;
import kr.ac.dankook.ace.smart_recruit.model.postingcomment.PostingComment;
import kr.ac.dankook.ace.smart_recruit.model.scrap.Scrap;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 롬복이 만들어주는 protected 생성자는 JPA가 객체를 생성하기에 충분
@Entity
@Table(
    name = "job_postings",
    indexes = {
        @Index(name = "idx_job_posting_region", columnList = "region"),
        @Index(name = "idx_job_posting_job_type", columnList = "job_type")
    }
)
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    @OneToOne(mappedBy = "jobPosting", fetch = FetchType.LAZY)
    private JobPostingAiSummary jobPostingAiSummary;

    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostingComment> postingComments = new ArrayList<>();
    
    // DB column으로 생성되지 않는 자바 객체 내부에서 존재하는 가상의 관계
    // CascadeType은 영속성 전이로 부모 엔티티를 저장하거나 삭제할 때 그 부모와 연결된 자식 엔티티들도 같이 처리할지 결정하는 옵션
    // 부모 엔티티와 자식 엔티티 사이의 견결이 끊어진 자식을 자동으로 삭제하는 옵션
    // jobPosting이 사라지면 공고를 가지고 있던 Scrap도 사라진다
    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Scrap> scraps = new ArrayList<>();

    @Column(name = "title",nullable = false, length = 255)
    private String title;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "region", length = 100)
    private String region;

    // 직무
    @Column(name = "job_type", length = 100)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false, length = 20)
    private JobStatus status;

    // (예: 2003-08-10)
    @Column(name = "deadline", length = 50)
    private String deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private JobSourceType sourceType;

    @Column(name = "external_url", length = 1024)
    private String externalUrl;

    @Column(name = "company", nullable = false, length = 255)
    private String company;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "region_sido", nullable = false, length = 50)
    private String regionSido;

    @Column(name = "region_sigungu", nullable = false, length = 100)
    private String regionSigungu;

    @Column(name = "pay_type", length = 20)
    private String payType;

    @Column(name = "pay_amount")
    private Integer payAmount;

    @Column(name = "job_type_major", nullable = false, length = 100)
    private String jobTypeMajor;

    @Column(name = "job_type_mid", nullable = false, length = 100)
    private String jobTypeMid;

    @Column(name = "job_type_minor", nullable = false, length = 100)
    private String jobTypeMinor;

    @Column(name = "job_type_detail", nullable = false, length = 100)
    private String jobTypeDetail;

    @Column(name = "welfare", columnDefinition = "TEXT")
    private String welfare;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 생성자
    public JobPosting(
            String title,
            String content,
            String region,
            String jobType,
            JobStatus status,
            String deadline,
            JobSourceType sourceType,
            String externalUrl,
            String company,
            Double latitude,
            Double longitude,
            String regionSido,
            String regionSigungu,
            String payType,
            Integer payAmount,
            String jobTypeMajor,
            String jobTypeMid,
            String jobTypeMinor,
            String jobTypeDetail,
            String welfare
    ) {
        this.title = title;
        this.content = content;
        this.region = region;
        this.jobType = jobType;
        this.status = status;
        this.deadline = deadline;
        this.sourceType = sourceType;
        this.externalUrl = externalUrl;
        this.company = company;
        this.latitude = latitude;
        this.longitude = longitude;
        this.regionSido = regionSido;
        this.regionSigungu = regionSigungu;
        this.payType = payType;
        this.payAmount = payAmount;
        this.jobTypeMajor = jobTypeMajor;
        this.jobTypeMid = jobTypeMid;
        this.jobTypeMinor = jobTypeMinor;
        this.jobTypeDetail = jobTypeDetail;
        this.welfare = welfare;
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

    public void updateStatus(JobStatus status) {
        this.status = status;
    }
}
