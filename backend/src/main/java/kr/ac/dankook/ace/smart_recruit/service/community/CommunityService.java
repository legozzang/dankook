package kr.ac.dankook.ace.smart_recruit.service.community;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.dankook.ace.smart_recruit.model.community.Category;
import kr.ac.dankook.ace.smart_recruit.model.community.Community;
import kr.ac.dankook.ace.smart_recruit.model.communitycomment.CommunityComment;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.member.Role;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.repository.community.CommunityCommentRepository;
import kr.ac.dankook.ace.smart_recruit.repository.community.CommunityRepository;
import kr.ac.dankook.ace.smart_recruit.repository.jobposting.JobPostingRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final MemberRepository memberRepository;
    private final JobPostingRepository jobPostingRepository;

    public List<CommunityDto> list(Category category, String keyword) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<Community> communities = (category == null)
                ? communityRepository.findAllWithMember(normalizedKeyword)
                : communityRepository.findByCategoryWithMember(category, normalizedKeyword);
        Map<Long, Long> commentCounts = commentCounts(communities);
        return communities.stream()
                .map(community -> toDto(community, commentCounts.getOrDefault(community.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CommunityDetailDto detail(Long id) {
        communityRepository.incrementViewCount(id);
        Community community = communityRepository.findByIdWithMember(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        return toDetailDto(community, findCommentDtos(id));
    }

    public CommunityDetailDto detailForEdit(Long id) {
        Community community = communityRepository.findByIdWithMember(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        return toDetailDto(community, List.of());
    }

    private List<CommentDto> findCommentDtos(Long id) {
        List<CommunityComment> topLevel = communityCommentRepository.findTopLevelByCommunityIdWithMember(id);
        List<CommunityComment> replyList = communityCommentRepository.findRepliesWithMemberByCommunityId(id);
        Map<Long, List<CommunityComment>> byParentId = replyList.stream()
                .collect(Collectors.groupingBy(comment -> comment.getParent().getId()));
        return topLevel.stream()
                .map(comment -> toCommentDto(comment, buildReplyDtos(comment.getId(), byParentId)))
                .toList();
    }

    private List<ReplyDto> buildReplyDtos(Long parentId, Map<Long, List<CommunityComment>> byParentId) {
        return byParentId.getOrDefault(parentId, List.of()).stream()
                .map(reply -> toReplyDto(reply, buildReplyDtos(reply.getId(), byParentId)))
                .toList();
    }

    @Transactional
    public Community create(String email, Category category, String title, String content, Long jobPostingId) {
        Member member = findMember(email);
        JobPosting jobPosting = findJobPosting(jobPostingId);
        Community community = new Community(member, category, title, content, jobPosting);
        return communityRepository.save(community);
    }

    @Transactional
    public Community update(Long id, String email, String title, String content) {
        Member actor = findMember(email);
        Community community = communityRepository.findByIdWithMember(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        if (!canManage(actor, community.getMember())) {
            throw new IllegalStateException("수정 권한이 없습니다.");
        }
        community.update(title, content);
        return community;
    }

    @Transactional
    public void delete(Long id, String email) {
        Member actor = findMember(email);
        Community community = communityRepository.findByIdWithMember(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        if (!canManage(actor, community.getMember())) {
            throw new IllegalStateException("삭제 권한이 없습니다.");
        }
        communityRepository.delete(community);
    }

    @Transactional
    public CommunityComment addComment(Long communityId, String email, String content) {
        Member member = findMember(email);
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        CommunityComment comment = new CommunityComment(community, member, content);
        return communityCommentRepository.save(comment);
    }

    @Transactional
    public CommunityComment addReply(Long communityId, Long parentCommentId, String email, String content) {
        Member member = findMember(email);
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        CommunityComment parent = communityCommentRepository.findById(parentCommentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        if (!parent.getCommunity().getId().equals(community.getId())) {
            throw new IllegalArgumentException("게시글의 댓글을 찾을 수 없습니다.");
        }
        return communityCommentRepository.save(new CommunityComment(community, member, content, parent));
    }

    @Transactional
    public void deleteComment(Long commentId, String email) {
        Member actor = findMember(email);
        CommunityComment comment = communityCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        if (!canManage(actor, comment.getMember())) {
            throw new IllegalStateException("삭제 권한이 없습니다.");
        }
        communityCommentRepository.delete(comment);
    }

    public Category parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Category.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Map<Long, Long> commentCounts(List<Community> communities) {
        List<Long> ids = communities.stream().map(Community::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return communityCommentRepository.countByCommunityIds(ids).stream()
                .collect(Collectors.toMap(
                        CommunityCommentRepository.CommentCountRow::getCommunityId,
                        CommunityCommentRepository.CommentCountRow::getCommentCount
                ));
    }

    private Member findMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private JobPosting findJobPosting(Long jobPostingId) {
        if (jobPostingId == null) {
            return null;
        }
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new IllegalArgumentException("첨부할 공고를 찾을 수 없습니다."));
    }

    private boolean canManage(Member actor, Member owner) {
        return actor.getRole() == Role.ADMIN || actor.getId().equals(owner.getId());
    }

    private CommunityDto toDto(Community community, long commentCount) {
        return new CommunityDto(
                community.getId(),
                community.getCategory(),
                categoryLabel(community.getCategory()),
                community.getTitle(),
                community.getMember().getNickname(),
                community.getMember().getEmail(),
                community.getViewCount(),
                community.getCreatedAt(),
                commentCount,
                community.getUpdatedAt() != null,
                toAttachedJobDto(community.getJobPosting())
        );
    }

    private CommunityDetailDto toDetailDto(Community community, List<CommentDto> comments) {
        return new CommunityDetailDto(
                community.getId(),
                community.getCategory(),
                categoryLabel(community.getCategory()),
                community.getTitle(),
                community.getContent(),
                community.getMember().getNickname(),
                community.getMember().getEmail(),
                community.getViewCount(),
                community.getCreatedAt(),
                comments,
                community.getUpdatedAt() != null,
                toAttachedJobDto(community.getJobPosting())
        );
    }

    private AttachedJobDto toAttachedJobDto(JobPosting jobPosting) {
        if (jobPosting == null) {
            return null;
        }
        return new AttachedJobDto(
                jobPosting.getId(),
                jobPosting.getCompany(),
                jobPosting.getTitle(),
                jobPosting.getJobTypeMajor(),
                jobPosting.getRegionSido(),
                jobPosting.getDeadline(),
                jobPosting.getExternalUrl()
        );
    }

    private CommentDto toCommentDto(CommunityComment comment, List<ReplyDto> replies) {
        return new CommentDto(
                comment.getId(),
                comment.getMember().getNickname(),
                comment.getMember().getEmail(),
                comment.getContent(),
                comment.getCreatedAt(),
                replies
        );
    }

    private ReplyDto toReplyDto(CommunityComment reply, List<ReplyDto> subReplies) {
        return new ReplyDto(
                reply.getId(),
                reply.getMember().getNickname(),
                reply.getMember().getEmail(),
                reply.getContent(),
                reply.getCreatedAt(),
                subReplies
        );
    }

    private String categoryLabel(Category category) {
        return switch (category) {
            case INFO -> "정보";
            case QNA -> "질문";
            case FREE -> "자유";
        };
    }

    public record CommunityDto(
            Long id,
            Category category,
            String categoryLabel,
            String title,
            String nickname,
            String authorEmail,
            Integer viewCount,
            LocalDateTime createdAt,
            long commentCount,
            boolean isEdited,
            AttachedJobDto attachedJob
    ) {
    }

    public record CommunityDetailDto(
            Long id,
            Category category,
            String categoryLabel,
            String title,
            String content,
            String nickname,
            String authorEmail,
            Integer viewCount,
            LocalDateTime createdAt,
            List<CommentDto> comments,
            boolean isEdited,
            AttachedJobDto attachedJob
    ) {
    }

    public record AttachedJobDto(
            Long id,
            String company,
            String title,
            String jobTypeMajor,
            String regionSido,
            String deadline,
            String externalUrl
    ) {
    }

    public record CommentDto(
            Long id,
            String nickname,
            String authorEmail,
            String content,
            LocalDateTime createdAt,
            List<ReplyDto> replies
    ) {
        public int totalReplyCount() {
            return replies.stream().mapToInt(ReplyDto::totalCount).sum();
        }
    }

    public record ReplyDto(
            Long id,
            String nickname,
            String authorEmail,
            String content,
            LocalDateTime createdAt,
            List<ReplyDto> replies
    ) {
        public int totalCount() {
            return 1 + replies.stream().mapToInt(ReplyDto::totalCount).sum();
        }
    }
}
