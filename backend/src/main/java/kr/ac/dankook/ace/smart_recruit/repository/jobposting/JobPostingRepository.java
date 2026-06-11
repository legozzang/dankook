package kr.ac.dankook.ace.smart_recruit.repository.jobposting;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long>, JpaSpecificationExecutor<JobPosting> {

    List<JobPosting> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT j FROM JobPosting j
            LEFT JOIN FETCH j.jobPostingAiSummary
            WHERE j.status = kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus.OPEN
            ORDER BY j.createdAt DESC
            """)
    List<JobPosting> findRecentWithAiSummary(Pageable pageable);

    @Query("""
            SELECT j FROM JobPosting j
            LEFT JOIN FETCH j.jobPostingAiSummary
            WHERE j.id = :id
            """)
    Optional<JobPosting> findByIdWithAiSummary(@Param("id") Long id);

    @Query("""
            SELECT j FROM JobPosting j
            LEFT JOIN FETCH j.jobPostingAiSummary
            WHERE (:sido IS NULL OR j.regionSido = :sido)
              AND (:sigungu IS NULL OR j.regionSigungu = :sigungu)
              AND (:jobTypeMajor IS NULL OR j.jobTypeMajor = :jobTypeMajor)
              AND (:jobTypeMid IS NULL OR j.jobTypeMid = :jobTypeMid)
              AND (:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:payType IS NULL OR j.payType = :payType)
              AND j.status = kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus.OPEN
            ORDER BY j.createdAt DESC
            """)
    List<JobPosting> findByFilters(
            @Param("sido") String sido,
            @Param("sigungu") String sigungu,
            @Param("jobTypeMajor") String jobTypeMajor,
            @Param("jobTypeMid") String jobTypeMid,
            @Param("keyword") String keyword,
            @Param("payType") String payType
    );

    @Query("""
            SELECT j FROM JobPosting j
            LEFT JOIN FETCH j.jobPostingAiSummary
            WHERE j.id IN :ids
              AND j.status = kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus.OPEN
            """)
    List<JobPosting> findWithAiSummaryByIdIn(@Param("ids") List<Long> ids);

    @Query("""
            SELECT j FROM JobPosting j
            LEFT JOIN FETCH j.jobPostingAiSummary
            WHERE j.status = kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus.OPEN
              AND j.id NOT IN :excludeIds
              AND (
                ((:jobTypeMajor IS NULL OR :jobTypeMajor = '') AND (:jobTypeMid IS NULL OR :jobTypeMid = ''))
                OR (:jobTypeMid IS NOT NULL AND :jobTypeMid <> '' AND j.jobTypeMid = :jobTypeMid)
                OR (:jobTypeMajor IS NOT NULL AND :jobTypeMajor <> '' AND j.jobTypeMajor = :jobTypeMajor)
              )
              AND (:payType IS NULL OR :payType = '' OR j.payType = :payType)
              AND (
                (
                  (:regionSido1 IS NULL OR :regionSido1 = '') AND (:regionSigungu1 IS NULL OR :regionSigungu1 = '')
                  AND (:regionSido2 IS NULL OR :regionSido2 = '') AND (:regionSigungu2 IS NULL OR :regionSigungu2 = '')
                  AND (:regionSido3 IS NULL OR :regionSido3 = '') AND (:regionSigungu3 IS NULL OR :regionSigungu3 = '')
                )
                OR (:regionSigungu1 IS NOT NULL AND :regionSigungu1 <> '' AND j.regionSigungu = :regionSigungu1)
                OR ((:regionSigungu1 IS NULL OR :regionSigungu1 = '') AND :regionSido1 IS NOT NULL AND :regionSido1 <> '' AND j.regionSido = :regionSido1)
                OR (:regionSigungu2 IS NOT NULL AND :regionSigungu2 <> '' AND j.regionSigungu = :regionSigungu2)
                OR ((:regionSigungu2 IS NULL OR :regionSigungu2 = '') AND :regionSido2 IS NOT NULL AND :regionSido2 <> '' AND j.regionSido = :regionSido2)
                OR (:regionSigungu3 IS NOT NULL AND :regionSigungu3 <> '' AND j.regionSigungu = :regionSigungu3)
                OR ((:regionSigungu3 IS NULL OR :regionSigungu3 = '') AND :regionSido3 IS NOT NULL AND :regionSido3 <> '' AND j.regionSido = :regionSido3)
              )
            ORDER BY j.createdAt DESC
            """)
    List<JobPosting> findRecommendationTargets(
            @Param("jobTypeMajor") String jobTypeMajor,
            @Param("jobTypeMid") String jobTypeMid,
            @Param("payType") String payType,
            @Param("regionSido1") String regionSido1,
            @Param("regionSigungu1") String regionSigungu1,
            @Param("regionSido2") String regionSido2,
            @Param("regionSigungu2") String regionSigungu2,
            @Param("regionSido3") String regionSido3,
            @Param("regionSigungu3") String regionSigungu3,
            @Param("excludeIds") List<Long> excludeIds,
            Pageable pageable
    );

    @Query("SELECT DISTINCT j.payType FROM JobPosting j WHERE j.payType IS NOT NULL AND j.payType <> '' AND j.status = kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus.OPEN ORDER BY j.payType")
    List<String> findDistinctPayTypes();

    @Query("SELECT DISTINCT j.regionSido, j.regionSigungu FROM JobPosting j WHERE j.regionSido IS NOT NULL AND j.regionSido <> '' AND j.regionSigungu IS NOT NULL AND j.regionSigungu <> '' AND j.status = kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus.OPEN ORDER BY j.regionSido, j.regionSigungu")
    List<Object[]> findDistinctSidoSigunguPairs();

    @Query("SELECT DISTINCT j.regionSido FROM JobPosting j WHERE j.regionSido IS NOT NULL AND j.regionSido <> '' AND j.status = kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus.OPEN ORDER BY j.regionSido")
    List<String> findDistinctSidos();

    @Query("SELECT DISTINCT j.jobTypeMajor, j.jobTypeMid FROM JobPosting j WHERE j.jobTypeMajor IS NOT NULL AND j.jobTypeMajor <> '' AND j.status = kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus.OPEN ORDER BY j.jobTypeMajor, j.jobTypeMid")
    List<Object[]> findDistinctJobMajorMidPairs();

    @Query("SELECT j.regionSido, j.regionSigungu, COUNT(j) FROM JobPosting j WHERE j.regionSido IS NOT NULL AND j.regionSido <> '' GROUP BY j.regionSido, j.regionSigungu ORDER BY COUNT(j) DESC")
    List<Object[]> countGroupByRegion();

    @Query("""
            SELECT COUNT(j) FROM JobPosting j
            WHERE (:sido IS NULL OR j.regionSido = :sido)
              AND (:sigungu IS NULL OR j.regionSigungu = :sigungu)
              AND (:major IS NULL OR j.jobTypeMajor = :major)
              AND (:mid IS NULL OR j.jobTypeMid = :mid)
              AND (:payType IS NULL OR j.payType = :payType)
            """)
    Long countByAllFilters(
            @Param("sido") String sido,
            @Param("sigungu") String sigungu,
            @Param("major") String major,
            @Param("mid") String mid,
            @Param("payType") String payType
    );

    @Query("SELECT j.jobTypeMajor, j.jobTypeMid, COUNT(j) FROM JobPosting j WHERE (:sido IS NULL OR j.regionSido = :sido) AND (:sigungu IS NULL OR j.regionSigungu = :sigungu) AND (:payType IS NULL OR j.payType = :payType) AND j.jobTypeMajor IS NOT NULL AND j.jobTypeMajor <> '' GROUP BY j.jobTypeMajor, j.jobTypeMid ORDER BY COUNT(j) DESC")
    List<Object[]> countGroupByJobType(@Param("sido") String sido, @Param("sigungu") String sigungu, @Param("payType") String payType);

    @Query("SELECT j.payType, AVG(j.payAmount) FROM JobPosting j WHERE (:sido IS NULL OR j.regionSido = :sido) AND (:sigungu IS NULL OR j.regionSigungu = :sigungu) AND (:major IS NULL OR j.jobTypeMajor = :major) AND (:mid IS NULL OR j.jobTypeMid = :mid) AND (:payType IS NULL OR j.payType = :payType) AND j.payType IS NOT NULL AND j.payType <> '' AND j.payAmount IS NOT NULL AND j.payAmount > 0 GROUP BY j.payType ORDER BY j.payType")
    List<Object[]> avgPayGroupByType(@Param("sido") String sido, @Param("sigungu") String sigungu, @Param("major") String major, @Param("mid") String mid, @Param("payType") String payType);

    @Query("SELECT j.jobTypeMajor, j.jobTypeMid, AVG(j.payAmount), COUNT(j) FROM JobPosting j WHERE (:sido IS NULL OR j.regionSido = :sido) AND (:sigungu IS NULL OR j.regionSigungu = :sigungu) AND (:payType IS NULL OR j.payType = :payType) AND j.payAmount IS NOT NULL AND j.payAmount > 0 AND j.payType IS NOT NULL AND j.payType <> '' GROUP BY j.jobTypeMajor, j.jobTypeMid ORDER BY AVG(j.payAmount) DESC")
    List<Object[]> avgPayByJobType(@Param("sido") String sido, @Param("sigungu") String sigungu, @Param("payType") String payType);

    @Query("SELECT j.regionSido, j.regionSigungu, AVG(j.payAmount), COUNT(j) FROM JobPosting j WHERE (:major IS NULL OR j.jobTypeMajor = :major) AND (:mid IS NULL OR j.jobTypeMid = :mid) AND (:payType IS NULL OR j.payType = :payType) AND j.payAmount IS NOT NULL AND j.payAmount > 0 AND j.payType IS NOT NULL AND j.payType <> '' GROUP BY j.regionSido, j.regionSigungu ORDER BY AVG(j.payAmount) DESC")
    List<Object[]> avgPayByRegion(@Param("major") String major, @Param("mid") String mid, @Param("payType") String payType);

    long countByStatus(JobStatus status);
}
