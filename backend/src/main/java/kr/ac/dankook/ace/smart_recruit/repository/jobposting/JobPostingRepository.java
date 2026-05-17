package kr.ac.dankook.ace.smart_recruit.repository.jobposting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    List<JobPosting> findAllByOrderByCreatedAtDesc();
}
