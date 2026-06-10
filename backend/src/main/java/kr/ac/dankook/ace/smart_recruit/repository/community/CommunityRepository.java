package kr.ac.dankook.ace.smart_recruit.repository.community;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.ac.dankook.ace.smart_recruit.model.community.Category;
import kr.ac.dankook.ace.smart_recruit.model.community.Community;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    @Query("""
            SELECT c FROM Community c JOIN FETCH c.member LEFT JOIN FETCH c.jobPosting
            WHERE (:keyword IS NULL OR c.title LIKE CONCAT('%', :keyword, '%')
                   OR c.content LIKE CONCAT('%', :keyword, '%'))
            ORDER BY c.createdAt DESC
            """)
    List<Community> findAllWithMember(@Param("keyword") String keyword);

    @Query("""
            SELECT c FROM Community c JOIN FETCH c.member LEFT JOIN FETCH c.jobPosting
            WHERE c.category = :category
              AND (:keyword IS NULL OR c.title LIKE CONCAT('%', :keyword, '%')
                   OR c.content LIKE CONCAT('%', :keyword, '%'))
            ORDER BY c.createdAt DESC
            """)
    List<Community> findByCategoryWithMember(@Param("category") Category category, @Param("keyword") String keyword);

    @Query("SELECT c FROM Community c JOIN FETCH c.member LEFT JOIN FETCH c.jobPosting WHERE c.id = :id")
    Optional<Community> findByIdWithMember(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Community c SET c.viewCount = c.viewCount + 1 WHERE c.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
