package kr.ac.dankook.ace.smart_recruit.controller;

import org.apache.catalina.connector.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import kr.ac.dankook.ace.smart_recruit.dto.*;
import kr.ac.dankook.ace.smart_recruit.service.AuthService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        
        // 성공 시 200 OK와 함께 바디에 토큰을 담아 보냅니다.
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(@RequestBody SignUpRequest request){
        // 회원가입 성공 시 201 상태코드와 함께 ID를 리턴
        Long memberId = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(memberId);
    }
}
