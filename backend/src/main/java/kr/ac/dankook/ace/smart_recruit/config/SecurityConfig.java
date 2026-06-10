package kr.ac.dankook.ace.smart_recruit.config;

import kr.ac.dankook.ace.smart_recruit.security.jwt.JwtAuthenticationFilter;
import kr.ac.dankook.ace.smart_recruit.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity // @EnableWebMvc 대신 시큐리티 설정을 위해 이것을 사용
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF 및 HTTP 기본 인증 비활성화
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())

            // 2. 세션을 사용하지 않음 (JWT 방식의 필수 설정)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 3. 요청별 권한 설정
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/job-postings/recommendations").authenticated()
                .requestMatchers("/api/job-postings/recommendations/refresh").authenticated()
                .requestMatchers("/api/job-postings/recommendations/estimated-time").permitAll()

                // 누구나 접근 가능한 경로 (화이트리스트)
                .requestMatchers("/",
                                "/dashboard",
                                "/auth/signup",
                                "/auth/login",
                                "/auth/geocode/preview",
                                "/auth/geocode/reverse",
                                "/auth/mypage",
                                "/main", 
                                "/jobs/list",
                                "/jobpostings",
                                "/jobpostings/**",
                                "/communities",
                                "/communities/**",
                                // [임시] 로컬 테스트용 — 채용공고 화면·AI 파이프라인 업로드 경로 인증 해제
                                // TODO: 운영 전 팀원과 협의 후 적절한 권한 정책으로 교체 필요
                                "/api/job-postings",
                                "/api/job-postings/**",
                                "/error"
                                ).permitAll()
                
                // 정적 리소스 (CSS, JS, 이미지 등)도 모두 허용
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()

                // 회원 정보 수정/삭제는 인증된 사용자만 접근 가능
                .requestMatchers("/auth/update/me",
                                "/auth/delete/me",
                                "/auth/members/me",
                                "/auth/edit-profile",
                                "/auth/me/gemini",
                                "/api/scraps/**"
                                ).authenticated()

                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")

                .anyRequest().authenticated() // 나머지는 인증 필요
            )

            // 4. JWT 필터 배치
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                            UsernamePasswordAuthenticationFilter.class)

            // 5. 브라우저 페이지 요청은 로그인으로, API 요청은 상태 코드로 응답
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    String accept = request.getHeader("Accept");
                    if (accept != null && accept.contains("text/html")) {
                        String redirectUrl = request.getRequestURI();
                        response.sendRedirect("/auth/login?redirect=" + redirectUrl);
                    } else {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 비밀번호를 안전하게 해싱하여 DB에 저장하기 위한 설정
        return new BCryptPasswordEncoder();
    }
}
