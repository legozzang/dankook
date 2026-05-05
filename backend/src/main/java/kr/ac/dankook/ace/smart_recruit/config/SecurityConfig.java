package kr.ac.dankook.ace.smart_recruit.config;

import kr.ac.dankook.ace.smart_recruit.security.jwt.JwtAuthenticationFilter;
import kr.ac.dankook.ace.smart_recruit.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

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
                // 누구나 접근 가능한 경로 (화이트리스트)
                .requestMatchers("/auth/signup",
                                "/auth/login",
                                "/auth/mypage", 
                                "/main", 
                                "/jobs/list",
                                "/auth/edit-profile"
                                ).permitAll()
                
                // 정적 리소스 (CSS, JS, 이미지 등)도 모두 허용
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()

                // 회원 정보 수정/삭제는 인증된 사용자만 접근 가능
                .requestMatchers("/auth/update/me",
                                "/auth/delete/me",
                                "/auth/members/me"
                                ).authenticated()

                .anyRequest().authenticated() // 나머지는 인증 필요
            )

            // 4. JWT 필터 배치
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), 
                            UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 비밀번호를 안전하게 해싱하여 DB에 저장하기 위한 설정
        return new BCryptPasswordEncoder();
    }
}