package kr.ac.dankook.ace.smart_recruit.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.dankook.ace.smart_recruit.dto.LoginRequest;
import kr.ac.dankook.ace.smart_recruit.dto.MemberInfoResponse;
import kr.ac.dankook.ace.smart_recruit.dto.GeminiSettingsRequest;
import kr.ac.dankook.ace.smart_recruit.dto.SignUpRequest;
import kr.ac.dankook.ace.smart_recruit.dto.TokenResponse;
import kr.ac.dankook.ace.smart_recruit.dto.UpdateRequest;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.member.Role;
import kr.ac.dankook.ace.smart_recruit.repository.MemberRepository;
import kr.ac.dankook.ace.smart_recruit.security.jwt.JwtTokenProvider;
import kr.ac.dankook.ace.smart_recruit.service.location.GeocodingService;
import kr.ac.dankook.ace.smart_recruit.service.location.GeocodingService.Coordinate;
import kr.ac.dankook.ace.smart_recruit.service.location.GeocodingService.ReverseGeocodeResult;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final GeocodingService geocodingService;

    /**
     * 시/도 + 시/군/구 + 상세주소를 지오코딩한다. 검증 로직 공통 진입점.
     * 빈 값·너무 짧은 값·변환 실패는 모두 IllegalArgumentException으로 거부한다.
     */
    public Coordinate geocodeHome(String sido, String sigungu, String detailAddress) {
        if (detailAddress == null || detailAddress.isBlank()) {
            throw new IllegalArgumentException("상세 주소를 입력해주세요.");
        }
        String trimmed = detailAddress.trim();
        if (trimmed.length() < 5) {
            throw new IllegalArgumentException("상세 주소를 더 구체적으로 입력해주세요. (예: 죽전로 152)");
        }

        String fullAddress = String.join(" ",
                sido == null ? "" : sido.trim(),
                sigungu == null ? "" : sigungu.trim(),
                trimmed).trim();

        return geocodingService.geocode(fullAddress)
                .orElseThrow(() -> new IllegalArgumentException(
                        "입력하신 상세 주소의 위치를 찾을 수 없습니다. 주소를 다시 확인해주세요."));
    }

    /**
     * 회원가입·수정 전 주소 미리보기용. 좌표만 변환해 반환(저장하지 않음).
     */
    public Coordinate previewHomeLocation(String sido, String sigungu, String detailAddress) {
        return geocodeHome(sido, sigungu, detailAddress);
    }

    public ReverseGeocodeResult previewReverseGeocode(double lat, double lng) {
        return geocodingService.reverseGeocode(lat, lng)
                .orElseThrow(() -> new IllegalArgumentException("해당 위치의 주소를 찾을 수 없습니다."));
    }

    /**
     * 지오코딩 후 Member에 거주지 좌표를 반영한다. 변환 실패 시 가입·수정을 거부한다.
     */
    private void applyHomeLocation(Member member, String sido, String sigungu, String detailAddress) {
        Coordinate coordinate = geocodeHome(sido, sigungu, detailAddress);
        member.updateHomeLocation(detailAddress.trim(), coordinate.latitude(), coordinate.longitude());
    }

    // 로그인
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

        return new TokenResponse(token, member.getId(), member.getEmail(), member.getRole().name());
    }

    // 회원 가입
    @Transactional
    public Long signUp(SignUpRequest request){
        // 1. 이메일 중복 체크 & 닉네임 중복 체크
        if(memberRepository.existsByEmail(request.getEmail())){
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        if(memberRepository.existsByNickname(request.getNickname())){
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
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
                .desiredRegionSido(request.getDesiredRegionSido())
                .desiredRegionSigungu(request.getDesiredRegionSigungu())
                .desiredRegion2Sido(request.getDesiredRegion2Sido())
                .desiredRegion2Sigungu(request.getDesiredRegion2Sigungu())
                .desiredRegion3Sido(request.getDesiredRegion3Sido())
                .desiredRegion3Sigungu(request.getDesiredRegion3Sigungu())
                .preferredJobTypeMajor(request.getPreferredJobTypeMajor())
                .preferredJobTypeMid(request.getPreferredJobTypeMid())
                .preferredPayType(request.getPreferredPayType())
                .minPayAmount(request.getMinPayAmount())
                .build();

        // 4. 관심 지역 1 상세 주소 → 거주지 좌표 지오코딩 후 반영
        applyHomeLocation(member,
                request.getDesiredRegionSido(),
                request.getDesiredRegionSigungu(),
                request.getHomeAddress());

        // 데이터베이스에 저장 후 해당 member의 id를 리턴
        Member savedMember = memberRepository.save(member);
        return savedMember.getId();
    }

    // 회원 탈퇴 (삭제)
    @Transactional
    public void deleteMember(String userEmail){

        Member member = memberRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        memberRepository.delete(member);
    }

    // 회원 정보 수정
    @Transactional
    public void updateMember(String userEmail, UpdateRequest request){
        Member member = memberRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if(request.getNewPassword() != null && !request.getNewPassword().isBlank()){
            // 현재 비밀번호 확인
            if (request.getCurrentPassword() == null ||
                !passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
                throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
            }
            String encodedPassword = passwordEncoder.encode(request.getNewPassword());
            member.updatePassword(encodedPassword);
        }

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            // 본인의 현재 닉네임과 다르면서, 동시에 다른 사람이 이미 사용 중인지 체크
            if (!member.getNickname().equals(request.getNickname()) && 
                memberRepository.existsByNickname(request.getNickname())) {
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
            }
            member.updateNickname(request.getNickname());
        }

        member.updatePreferences(
                request.getDesiredRegionSido(),
                request.getDesiredRegionSigungu(),
                request.getDesiredRegionDong(),
                request.getDesiredRegion2Sido(),
                request.getDesiredRegion2Sigungu(),
                request.getDesiredRegion3Sido(),
                request.getDesiredRegion3Sigungu(),
                request.getPreferredJobTypeMajor(),
                request.getPreferredJobTypeMid(),
                request.getPreferredJobTypeMinor(),
                request.getPreferredJobTypeDetail(),
                request.getPreferredPayType(),
                request.getMinPayAmount()
        );
        member.updateEmailNotification(request.getEmailNotification());

        // 관심 지역 1 상세 주소가 전달되면 거주지 좌표 재지오코딩
        if (request.getHomeAddress() != null && !request.getHomeAddress().isBlank()) {
            applyHomeLocation(member,
                    request.getDesiredRegionSido() != null ? request.getDesiredRegionSido() : member.getDesiredRegionSido(),
                    request.getDesiredRegionSigungu() != null ? request.getDesiredRegionSigungu() : member.getDesiredRegionSigungu(),
                    request.getHomeAddress());
        }
    }

    @Transactional
    public void updateGeminiSettings(String userEmail, GeminiSettingsRequest request) {
        Member member = memberRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Integer intervalHours = request.getIntervalHours();
        if (intervalHours != null
                && intervalHours != 1
                && intervalHours != 3
                && intervalHours != 6
                && intervalHours != 12
                && intervalHours != 24) {
            throw new IllegalArgumentException("추천 갱신 주기는 1/3/6/12/24시간 중 하나여야 합니다.");
        }

        member.updateGeminiSettings(request.getGeminiApiKey(), intervalHours, request.getCustomPrompt());
    }

    // 회원 정보 조회
    public MemberInfoResponse getMemberInfo(String userEmail){
        Member member = memberRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return new MemberInfoResponse(
                member.getEmail(),
                member.getNickname(),
                member.getDesiredRegionSido(),
                member.getDesiredRegionSigungu(),
                member.getDesiredRegionDong(),
                member.getHomeAddress(),
                member.getHomeLatitude(),
                member.getHomeLongitude(),
                member.getDesiredRegion2Sido(),
                member.getDesiredRegion2Sigungu(),
                member.getDesiredRegion3Sido(),
                member.getDesiredRegion3Sigungu(),
                member.getPreferredJobTypeMajor(),
                member.getPreferredJobTypeMid(),
                member.getPreferredJobTypeMinor(),
                member.getPreferredJobTypeDetail(),
                member.getPreferredPayType(),
                member.getMinPayAmount(),
                member.isEmailNotification(),
                member.getGeminiApiKey(),
                member.getRecommendationIntervalHours(),
                member.getRecommendationCustomPrompt()
        );
    }
}
