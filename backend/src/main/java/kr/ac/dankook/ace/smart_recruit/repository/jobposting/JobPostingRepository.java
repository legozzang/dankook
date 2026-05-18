package kr.ac.dankook.ace.smart_recruit.repository.jobposting;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @Query("select jp from JobPosting jp left join fetch jp.employer order by jp.createdAt desc")
    List<JobPosting> findAllWithEmployerOrderByCreatedAtDesc();

    @Query("""
            select jp
            from JobPosting jp
            left join fetch jp.employer
            left join fetch jp.jobPostingAiSummary
            order by jp.createdAt desc
            """)
    List<JobPosting> findAllWithEmployerAndAiSummaryOrderByCreatedAtDesc();

    @Query("""
            select jp
            from JobPosting jp
            left join fetch jp.employer
            left join fetch jp.jobPostingAiSummary
            where jp.id = :id
            """)
    Optional<JobPosting> findByIdWithEmployerAndAiSummary(Long id);

    @Query("""
            select jp
            from JobPosting jp
            left join fetch jp.employer
            where jp.latitude is null or jp.longitude is null
            """)
    List<JobPosting> findAllMissingCoordinateWithEmployer();
}
