package kr.ac.dankook.ace.smart_recruit.repository.community;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.ac.dankook.ace.smart_recruit.model.communitycomment.CommunityComment;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    @Query("SELECT cc FROM CommunityComment cc JOIN FETCH cc.member WHERE cc.community.id = :communityId AND cc.parent IS NULL ORDER BY cc.createdAt ASC")
    List<CommunityComment> findTopLevelByCommunityIdWithMember(@Param("communityId") Long communityId);

    @Query("SELECT cc FROM CommunityComment cc JOIN FETCH cc.member JOIN FETCH cc.parent WHERE cc.community.id = :communityId AND cc.parent IS NOT NULL ORDER BY cc.createdAt ASC")
    List<CommunityComment> findRepliesWithMemberByCommunityId(@Param("communityId") Long communityId);

    @Query("""
            SELECT cc.community.id AS communityId, COUNT(cc) AS commentCount
            FROM CommunityComment cc
            WHERE cc.community.id IN :communityIds
            GROUP BY cc.community.id
            """)
    List<CommentCountRow> countByCommunityIds(@Param("communityIds") List<Long> communityIds);

    interface CommentCountRow {
        Long getCommunityId();

        Long getCommentCount();
    }
}
