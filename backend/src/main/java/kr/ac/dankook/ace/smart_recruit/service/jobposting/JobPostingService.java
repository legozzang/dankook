package kr.ac.dankook.ace.smart_recruit.service.jobposting;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;

    public List<JobPosting> findAll() {
        return jobPostingRepository.findAllWithEmployerOrderByCreatedAtDesc();
    }
}
