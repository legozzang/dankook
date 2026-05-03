package kr.ac.dankook.ace.smart_recruit.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.dankook.ace.smart_recruit.dto.LoginRequest;
import kr.ac.dankook.ace.smart_recruit.dto.SignUpRequest;
import kr.ac.dankook.ace.smart_recruit.dto.TokenResponse;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.member.Role;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public TokenResponse login(LoginRequest request) {
        // 1. 이메일로 회원 조회
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // 2. 비밀번호 일치 확인 (암호화된 비번과 비교)
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("잘못된 비밀번호입니다.");
        }

        // 3. 토큰 생성 (ID, 이메일, 권한 주입)
        String token = jwtTokenProvider.createToken(
                member.getId(), 
                member.getEmail(), 
                member.getRole().name()
        );

        return new TokenResponse(token, member.getEmail(), member.getRole().name());
    }

    public Long signUp(SignUpRequest request){
        // 1. 이메일 중복 체크
        if(memberRepository.existsByEmail(request.getEmail())){
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        // 2. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        
        // Role 변환 방어 로직
        Role role;
        try {
            if (request.getRole() == null) {
                throw new IllegalArgumentException("역할(Role)은 필수 입력값입니다.");
            }
            role = Role.valueOf(request.getRole().toUpperCase()); // 대소문자 무시를 위해 toUpperCase() 권장
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 역할입니다: " + request.getRole());
        }

        // 3. 회원 엔티티 생성 및 저장
        Member member = Member.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .role(role)
                .build();

        // 데이터베이스에 저장 후 해당 member의 id를 리턴
        Member savedMember = memberRepository.save(member);
        return savedMember.getId();
    }

    @Transactional
    public void deleteMember(Long memberId, String userEmail){

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        if(!member.getEmail().equals(userEmail)){
            throw new IllegalStateException("권한이 없습니다.");
        }

        memberRepository.delete(member);
    }
}
