package kr.ac.dankook.ace.smart_recruit.model.recommendation;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "recommendation_stats")
public class RecommendationStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "duration_seconds", nullable = false)
    private Double durationSeconds;

    @Column(name = "posting_count", nullable = false)
    private Integer postingCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public RecommendationStat(double durationSeconds, int postingCount) {
        this.durationSeconds = durationSeconds;
        this.postingCount = postingCount;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
