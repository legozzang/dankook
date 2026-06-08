package kr.ac.dankook.ace.smart_recruit.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import kr.ac.dankook.ace.smart_recruit.model.community.Category;
import kr.ac.dankook.ace.smart_recruit.service.community.CommunityService;
import kr.ac.dankook.ace.smart_recruit.service.community.CommunityService.CommunityDetailDto;
import lombok.RequiredArgsConstructor;

/* 
    최초 작성자 : 유지훈
    최초 작성일 : 2026.04.18
    목적 : Community를 위한 Controller
    개정 이력 :  이름, 20xx.0x.xx (변경사항) <= 향후 작성
*/
@Controller
@RequiredArgsConstructor
public class CommunityViewController {

    private final CommunityService communityService;

    @GetMapping("/communities")
    public String list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal User user,
            Model model
    ) {
        Category parsedCategory = communityService.parseCategory(category);
        model.addAttribute("posts", communityService.list(parsedCategory, keyword));
        model.addAttribute("currentCategory", parsedCategory != null ? parsedCategory.name() : "");
        model.addAttribute("currentKeyword", keyword != null ? keyword : "");
        model.addAttribute("isAdmin", isAdmin(user));
        return "community/list";
    }

    @GetMapping("/communities/{id}")
    public String detail(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            Model model
    ) {
        CommunityDetailDto post = communityService.detail(id);
        String currentEmail = user != null ? user.getUsername() : "";
        boolean isAdmin = isAdmin(user);
        model.addAttribute("post", post);
        model.addAttribute("currentEmail", currentEmail);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isOwnerOrAdmin", isAdmin || post.authorEmail().equals(currentEmail));
        return "community/detail";
    }

    @GetMapping("/communities/write")
    public String write(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("isAdmin", isAdmin(user));
        return "community/write";
    }

    @GetMapping("/communities/{id}/edit")
    public String edit(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            Model model
    ) {
        if (user == null) {
            return "redirect:/communities/" + id;
        }
        CommunityDetailDto post = communityService.detailForEdit(id);
        boolean isAdmin = isAdmin(user);
        if (!isAdmin && !post.authorEmail().equals(user.getUsername())) {
            return "redirect:/communities/" + id;
        }
        model.addAttribute("post", post);
        model.addAttribute("isAdmin", isAdmin);
        return "community/edit";
    }

    private boolean isAdmin(User user) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
