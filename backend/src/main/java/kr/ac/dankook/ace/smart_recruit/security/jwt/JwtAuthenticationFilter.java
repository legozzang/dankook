package kr.ac.dankook.ace.smart_recruit.security.jwt;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.*;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // 1. 헤더에서 JWT 토큰 추출
        String token = jwtTokenProvider.resolveToken(request);

        // 2. 토큰이 유효한지 검사
        if (token != null && jwtTokenProvider.validateToken(token)) {
            // 3. 토큰이 유효하면 사용자 정보를 꺼내서 SecurityContext에 저장
            // 이 작업을 거쳐야 이후 컨트롤러에서 @AuthenticationPrincipal 등으로 유저 정보를 쓸 수 있음
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 4. 다음 필터로 진행
        filterChain.doFilter(request, response);
    }
}
