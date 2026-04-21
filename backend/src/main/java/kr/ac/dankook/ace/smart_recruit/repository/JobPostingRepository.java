package kr.ac.dankook.ace.smart_recruit.repository;

// 이 코드는 테스트 코드이므로 개발할 때에는 제거하신 후 새롭게 구성해 주세요.

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
}
