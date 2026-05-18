package kr.ac.dankook.ace.smart_recruit.repository.jobposting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobStatus;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long>, JpaSpecificationExecutor<JobPosting> {

    List<JobPosting> findAllByOrderByCreatedAtDesc();

    @Query("SELECT j.regionSido, j.regionSigungu, COUNT(j) FROM JobPosting j WHERE j.regionSido IS NOT NULL AND j.regionSido <> '' GROUP BY j.regionSido, j.regionSigungu ORDER BY COUNT(j) DESC")
    List<Object[]> countGroupByRegion();

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
