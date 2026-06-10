package kr.ac.dankook.ace.smart_recruit.repository.recommendation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.dankook.ace.smart_recruit.model.recommendation.RecommendationStat;

public interface RecommendationStatRepository extends JpaRepository<RecommendationStat, Long> {

    List<RecommendationStat> findTop10ByOrderByCreatedAtDesc();
}
