package kr.ac.dankook.ace.smart_recruit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import kr.ac.dankook.ace.smart_recruit.dto.*;
import kr.ac.dankook.ace.smart_recruit.service.AuthService;
import lombok.RequiredArgsConstructor;



@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public String loginPage() {
        return "login"; // templates/login.html
    }

    @GetMapping("/signup")
    public String signUpPage() {
        return "signup"; // templates/signup.html
    }

    @GetMapping("/mypage")
    public String myPage() {
        return "mypage"; // mypage.html
    }


    @GetMapping("/members/me")
    @ResponseBody
    public ResponseEntity<MemberInfoResponse> getMemberInfo(@AuthenticationPrincipal User user) {
        MemberInfoResponse response = authService.getMemberInfo(user.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/edit-profile")
    public String editProfilePage(){
        return "edit-profile"; // templates/edit-profile.html
    }

    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        
        // 성공 시 200 OK와 함께 바디에 토큰을 담아 보냅니다.
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    @ResponseBody
    public ResponseEntity<Long> signUp(@Valid @RequestBody SignUpRequest request){
        // 회원가입 성공 시 201 상태코드와 함께 ID를 리턴
        Long memberId = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(memberId);
    }

    @DeleteMapping("/delete/me")
    @ResponseBody
    public ResponseEntity<Void> deleteMember(@AuthenticationPrincipal User user){
        authService.deleteMember(user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/update/me")
    @ResponseBody
    public ResponseEntity<Void> updateMember(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateRequest request){
        authService.updateMember(user.getUsername(), request);
        return ResponseEntity.ok().build();
    }
}