package kr.ac.dankook.ace.smart_recruit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.ac.dankook.ace.smart_recruit.model.community.Category;
import kr.ac.dankook.ace.smart_recruit.service.community.CommunityService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/communities")
@RequiredArgsConstructor
public class CommunityApiController {

    private final CommunityService communityService;

    @PostMapping
    public ResponseEntity<Void> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CommunityRequest request
    ) {
        Category category = communityService.parseCategory(request.category());
        if (category == null) {
            throw new IllegalArgumentException("유효하지 않은 카테고리입니다.");
        }
        communityService.create(user.getUsername(), category, request.title(), request.content(), request.jobPostingId());
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CommunityUpdateRequest request
    ) {
        communityService.update(id, user.getUsername(), request.title(), request.content());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        communityService.delete(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Void> addComment(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CommentRequest request
    ) {
        communityService.addComment(id, user.getUsername(), request.content());
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/{id}/comments/{cid}/replies")
    public ResponseEntity<Void> addReply(
            @PathVariable Long id,
            @PathVariable Long cid,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CommentRequest request
    ) {
        communityService.addReply(id, cid, user.getUsername(), request.content());
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/{id}/comments/{cid}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long id,
            @PathVariable Long cid,
            @AuthenticationPrincipal User user
    ) {
        communityService.deleteComment(cid, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    record CommunityRequest(
            @NotBlank String category,
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 5000) String content,
            @JsonAlias("jobPostingId")
            Long jobPostingId
    ) {
    }

    record CommunityUpdateRequest(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 5000) String content
    ) {
    }

    record CommentRequest(
            @NotBlank @Size(max = 1000) String content
    ) {
    }
}
