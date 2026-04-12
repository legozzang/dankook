package kr.ac.dankook.ace.smart_recruit.model.postingcomment;


import jakarta.persistence.*;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "posting_comment")
public class PostingComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "content",nullable = false, columnDefinition = "TEXT")
    private String content;

    // 댓글 생성시 양방향 연관관계도 함께 설정하는 정적 메서드
    public static PostingComment createComment(Member member, JobPosting jobPosting, String content) {
        PostingComment comment = new PostingComment();
        comment.member = member;
        comment.jobPosting = jobPosting;
        comment.content = content;
        
        // 양방향 리스트에도 추가
        member.getPostingComments().add(comment);
        jobPosting.getPostingComments().add(comment);
        
        return comment;
    }
}
