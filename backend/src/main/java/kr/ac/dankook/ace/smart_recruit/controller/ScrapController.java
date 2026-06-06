package kr.ac.dankook.ace.smart_recruit.controller;

import kr.ac.dankook.ace.smart_recruit.service.scrap.ScrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scraps")
@RequiredArgsConstructor
public class ScrapController {

    private final ScrapService scrapService;

    /**
     * POST /api/scraps/toggle?jobPostingId=123
     * 이미 스크랩이면 삭제, 없으면 추가. 최종 상태를 {"scraped": true/false} 로 반환.
     * 비인증 요청은 SecurityConfig의 .authenticated() 규칙으로 401을 선반환.
     */
    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggle(
            @RequestParam Long jobPostingId,
            @AuthenticationPrincipal User user
    ) {
        boolean scraped = scrapService.toggle(user.getUsername(), jobPostingId);
        return ResponseEntity.ok(Map.of("scraped", scraped));
    }

    // GET /api/scraps/my — 마이페이지 스크랩 목록
    @GetMapping("/my")
    public ResponseEntity<List<ScrapService.ScrapItem>> getMyScraps(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(scrapService.getMyScraps(user.getUsername()));
    }

    // GET /api/scraps/my/ids — 목록 페이지 초기화용 스크랩 ID 목록
    @GetMapping("/my/ids")
    public ResponseEntity<List<Long>> getMyScrappedIds(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(scrapService.getMyScrappedIds(user.getUsername()));
    }
}