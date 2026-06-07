package kr.ac.dankook.ace.smart_recruit.repository.recommendation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.ac.dankook.ace.smart_recruit.model.recommendation.UserJobRecommendation;

public interface UserJobRecommendationRepository extends JpaRepository<UserJobRecommendation, Long> {

    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.jobPostingAiSummary"})
    List<UserJobRecommendation> findByMemberId(Long memberId);

    Optional<UserJobRecommendation> findByMemberIdAndJobPostingId(Long memberId, Long jobPostingId);

    void deleteByMemberIdAndJobPostingId(Long memberId, Long jobPostingId);

    @Query("""
            SELECT r.jobPosting.id AS jobPostingId, r.recommendationReason AS reason
            FROM UserJobRecommendation r
            WHERE r.member.id = :memberId
            """)
    List<ReasonRow> findReasonRows(@Param("memberId") Long memberId);

    default Map<Long, String> findReasonMap(Long memberId) {
        return findReasonRows(memberId).stream()
                .filter(row -> row.getReason() != null && !row.getReason().isBlank())
                .collect(Collectors.toMap(
                        ReasonRow::getJobPostingId,
                        ReasonRow::getReason,
                        (left, right) -> left
                ));
    }

    @Query("""
            SELECT MAX(r.createdAt)
            FROM UserJobRecommendation r
            WHERE r.member.id = :memberId
            """)
    Optional<LocalDateTime> findLatestCreatedAtByMemberId(@Param("memberId") Long memberId);

    interface ReasonRow {
        Long getJobPostingId();
        String getReason();
    }
}
