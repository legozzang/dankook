package kr.ac.dankook.ace.smart_recruit.model.communitycomment;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.community.Community;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "community_comments")
public class CommunityComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "created_at",updatable = false) // Column 이름을 따로 설정, 설정하지 않으면 변수이름 따라감
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 아래 두 어노테이션으로 시간 자동 입력
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 사용자 정의 생성자
    public CommunityComment(Community community, Member member, String content) {
        this.community = community;
        this.member = member;
        this.content = content;
    }
}
