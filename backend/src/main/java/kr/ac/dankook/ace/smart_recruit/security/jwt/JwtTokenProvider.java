package kr.ac.dankook.ace.smart_recruit.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys; // 추가 필수
import io.jsonwebtoken.JwtException; // 추가 필수
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest; // 추가 필수

import org.springframework.beans.factory.annotation.Value; // @Value용
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // 추가 필수
import org.springframework.security.core.Authentication; // 중요: tomcat 것이 아님
import org.springframework.security.core.GrantedAuthority; // 추가 필수
import org.springframework.security.core.authority.SimpleGrantedAuthority; // 추가 필수
import org.springframework.security.core.userdetails.User; // 추가 필수
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils; // 추가 필수

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    // 실무 관행: Secret Key는 반드시 환경변수나 application.properties에서 가져옵니다.
    @Value("${jwt.secret}")
    private String secretKey;

    private final long tokenValidityInMilliseconds = 1000L * 60 * 60; // 1시간 유지

    private Key key;

    @PostConstruct
    public void init() {
        // Secret Key를 바이트 배열로 변환 후 HMAC-SHA 알고리즘에 맞는 Key 객체 생성
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // 1. 토큰 생성 (Member 정보를 담음)
    public String createToken(Long memberId, String email, String role) {
        Claims claims = Jwts.claims().setSubject(email);
        claims.put("memberId", memberId);
        claims.put("role", role); // DB의 Role(SEEKER, EMPLOYER 등)을 주입

        Date now = new Date();
        Date validity = new Date(now.getTime() + tokenValidityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. 토큰에서 권한 정보 꺼내기 (Spring Security 연동용)
    public Authentication getAuthentication(String token) {
        // 토큰에서 Claims 추출
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                            .parseClaimsJws(token).getBody();

        // 권한 정보를 SimpleGrantedAuthority로 변환
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("role").toString().split(","))
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)) // Spring Security는 "ROLE_" 접두사를 권장
                        .collect(Collectors.toList());

        // User 객체 생성 (Spring Security에서 인증된 사용자 정보를 담는 객체)
        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    // 3. 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 4. 헤더에서 토큰 추출
    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
