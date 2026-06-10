package kr.ac.dankook.ace.smart_recruit.repository.scrap;

import kr.ac.dankook.ace.smart_recruit.model.scrap.Scrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByMember_IdAndJobPosting_Id(Long memberId, Long jobPostingId);

    Optional<Scrap> findByMember_IdAndJobPosting_Id(Long memberId, Long jobPostingId);

    // N+1 방지: 화면 목록 렌더링 시 대량 공고 ID에 대해 한 번에 스크랩 여부를 조회
    @Query("SELECT s.jobPosting.id FROM Scrap s WHERE s.member.id = :memberId AND s.jobPosting.id IN :jobPostingIds")
    List<Long> findScrapedJobPostingIds(@Param("memberId") Long memberId,
                                        @Param("jobPostingIds") List<Long> jobPostingIds);

    // 마이페이지 목록용 (JobPosting + AI요약 한 번에 페치)
    @Query("""
            SELECT s FROM Scrap s
            JOIN FETCH s.jobPosting j
            LEFT JOIN FETCH j.jobPostingAiSummary
            WHERE s.member.id = :memberId
            ORDER BY s.createdAt DESC
            """)
    List<Scrap> findByMemberIdWithJobPosting(@Param("memberId") Long memberId);

    // 목록 페이지 초기화용 (전체 스크랩 ID)
    @Query("SELECT s.jobPosting.id FROM Scrap s WHERE s.member.id = :memberId")
    List<Long> findJobPostingIdsByMemberId(@Param("memberId") Long memberId);
}