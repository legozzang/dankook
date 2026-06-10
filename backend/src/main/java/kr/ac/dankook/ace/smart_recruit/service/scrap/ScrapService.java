package kr.ac.dankook.ace.smart_recruit.service.scrap;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.scrap.Scrap;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.scrap.ScrapRepository;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScrapService {

    private final ScrapRepository scrapRepository;
    private final MemberRepository memberRepository;
    private final JobPostingRepository jobPostingRepository;

    // 스크랩 토글 (등록 or 취소) — true 반환 시 스크랩 등록됨
    @Transactional
    public boolean toggle(String email, Long jobPostingId) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return scrapRepository.findByMember_IdAndJobPosting_Id(member.getId(), jobPostingId)
                .map(scrap -> {
                    scrapRepository.delete(scrap);
                    return false; // 취소됨
                })
                .orElseGet(() -> {
                    JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                            .orElseThrow(() -> new IllegalArgumentException("공고를 찾을 수 없습니다."));
                    scrapRepository.save(new Scrap(member, jobPosting));
                    return true; // 등록됨
                });
    }

    // 특정 공고가 스크랩됐는지 여부
    public boolean isScrapped(String email, Long jobPostingId) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return scrapRepository.existsByMember_IdAndJobPosting_Id(member.getId(), jobPostingId);
    }

    // 내 스크랩 목록 조회
    public List<ScrapItem> getMyScraps(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return scrapRepository.findByMemberIdWithJobPosting(member.getId()).stream()
                .map(scrap -> {
                    JobPosting j = scrap.getJobPosting();
                    return new ScrapItem(
                            j.getId(),
                            j.getTitle(),
                            j.getCompany(),
                            j.getRegionSido() + " " + j.getRegionSigungu(),
                            j.getPayType() != null && j.getPayAmount() != null && j.getPayAmount() > 0
                                    ? j.getPayType() + " " + String.format("%,d", j.getPayAmount()) + "원"
                                    : (j.getPayType() != null ? j.getPayType() : "급여 협의"),
                            j.getDeadline() != null && !j.getDeadline().isBlank() ? j.getDeadline() : "마감일 미정",
                            scrap.getCreatedAt().toString(),
                            j.getStatus().name()
                    );
                })
                .toList();
    }

    // 로그인한 사용자의 스크랩된 공고 ID 목록 (list 페이지 초기 렌더링용)
    public List<Long> getMyScrappedIds(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return scrapRepository.findJobPostingIdsByMemberId(member.getId());
    }

    public record ScrapItem(
            Long jobPostingId,
            String title,
            String company,
            String location,
            String salary,
            String deadline,
            String scrappedAt,
            String status
    ) {}
}
