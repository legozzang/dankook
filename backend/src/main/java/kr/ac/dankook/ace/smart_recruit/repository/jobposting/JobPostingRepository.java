package kr.ac.dankook.ace.smart_recruit.repository.jobposting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @Query("select jp from JobPosting jp join fetch jp.employer order by jp.createdAt desc")
    List<JobPosting> findAllWithEmployerOrderByCreatedAtDesc();
}
