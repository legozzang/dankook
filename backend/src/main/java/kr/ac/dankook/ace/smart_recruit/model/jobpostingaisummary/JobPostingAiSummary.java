package kr.ac.dankook.ace.smart_recruit.model.jobpostingaisummary;

import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 매개변수가 없는 기본 생성자를 자동으로 만들어주는 롬복 어노테이션
@Entity
@Table(name = "job_posting_ai_summary")
public class JobPostingAiSummary{
    // PK 생성 전략을 따로 설정하지 않음 FK로 JobPosting 테이블에서 가져올 예정(PK & FK)
    @Id
    private Long id;
    
    @MapsId //위에 id와 엮임을 표시
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id")
    private JobPosting jobPosting;
    
    // String으로 받아서 필요시 라이브러리로 List 변환
    // column 타입은 JSON이지만 자바 코드에서는 String으로 받을 수도 있고 List<String>으로 받을 수 있음
    @Column(name = "required_skills", columnDefinition = "json")
    private String requiredSkills;
    
    @Column(name = "main_tasks", columnDefinition = "json")
    private String mainTasks;
    
    @Column(name = "core_benefits", columnDefinition = "json")
    private String coreBenefits;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status",nullable = false, length = 20)
    private AiStatus aiStatus = AiStatus.PENDING;

    // 사용자 정의 생성자
    public JobPostingAiSummary(JobPosting jobPosting){
        this.jobPosting = jobPosting;
    }
}