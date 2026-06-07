package kr.ac.dankook.ace.smart_recruit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.member.Role;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    long countByRole(Role role);

    /**
     * 관리자 회원 목록 검색 — role·keyword 조건 DB 레벨 필터링
     *
     * <p>기존 {@code findAll()} + 메모리 스트림 필터 방식을 대체합니다.
     * 전체 회원을 메모리에 올리지 않으므로 회원 수가 증가해도 OOM 위험이 없습니다.
     *
     * @param role    null이면 모든 역할 포함
     * @param keyword 이메일·닉네임 부분 일치 검색 (이미 소문자 정규화된 값)
     */
    @Query("""
            SELECT m FROM Member m
            WHERE (:role IS NULL OR m.role = :role)
              AND (:keyword = ''
                   OR LOWER(m.email)    LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY m.id DESC
            """)
    List<Member> findByRoleAndKeyword(@Param("role") Role role,
                                      @Param("keyword") String keyword);
}
