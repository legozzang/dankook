package kr.ac.dankook.ace.smart_recruit.model;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Address {
    @Column(length = 20)
    private String city; // 시

    @Column(length = 20)
    private String district; // 구/군
    
    @Column(length = 100)
    private String detailAddress; // 상세 주소
}