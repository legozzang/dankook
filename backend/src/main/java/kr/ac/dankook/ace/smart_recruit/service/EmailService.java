package kr.ac.dankook.ace.smart_recruit.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import kr.ac.dankook.ace.smart_recruit.model.jobposting.JobPosting;
import kr.ac.dankook.ace.smart_recruit.model.member.Member;
import kr.ac.dankook.ace.smart_recruit.model.recommendation.UserJobRecommendation;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public void sendRecommendationEmail(Member member, List<UserJobRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(member.getEmail());
            helper.setSubject("[Smart Recruit] 오늘의 맞춤 추천 공고");
            helper.setText(buildHtml(member.getNickname(), recommendations), true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("[EmailService] 발송 실패 " + member.getEmail() + ": " + e.getMessage());
        }
    }

    private String buildHtml(String nickname, List<UserJobRecommendation> recommendations) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px;'>");
        sb.append("<h2 style='color:#4f46e5;'>")
                .append(escapeHtml(nickname))
                .append("님의 오늘 맞춤 추천 공고</h2>");
        sb.append("<p style='color:#667085;'>Smart Recruit가 선별한 추천 공고입니다.</p><hr/>");

        for (UserJobRecommendation recommendation : recommendations) {
            appendRecommendation(sb, recommendation);
        }

        sb.append("<p style='color:#9ca3af;font-size:12px;margin-top:24px;'>마이페이지에서 이메일 알림을 끌 수 있습니다.</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private void appendRecommendation(StringBuilder sb, UserJobRecommendation recommendation) {
        JobPosting posting = recommendation.getJobPosting();
        sb.append("<div style='margin:16px 0;padding:16px;border:1px solid #e5e7eb;border-radius:8px;'>");
        sb.append("<h3 style='margin:0 0 4px;color:#172033;'>")
                .append(escapeHtml(posting.getTitle()))
                .append("</h3>");
        sb.append("<p style='margin:0;color:#667085;font-size:13px;'>")
                .append(escapeHtml(posting.getCompany()))
                .append(" | ")
                .append(escapeHtml(posting.getRegionSido()))
                .append(" ")
                .append(escapeHtml(posting.getRegionSigungu()));

        if (posting.getPayType() != null) {
            sb.append(" | ").append(escapeHtml(posting.getPayType()));
            if (posting.getPayAmount() != null && posting.getPayAmount() > 0) {
                sb.append(" ").append(String.format("%,d", posting.getPayAmount())).append("원");
            }
        }
        sb.append("</p>");

        if (recommendation.getRecommendationReason() != null
                && !recommendation.getRecommendationReason().isBlank()) {
            sb.append("<p style='margin:8px 0 0;color:#4f46e5;font-size:13px;'>")
                    .append("&#128161; ")
                    .append(escapeHtml(recommendation.getRecommendationReason()))
                    .append("</p>");
        }

        if (posting.getExternalUrl() != null && !posting.getExternalUrl().isBlank()) {
            sb.append("<a href='")
                    .append(escapeHtml(posting.getExternalUrl()))
                    .append("' style='display:inline-block;margin-top:10px;padding:6px 14px;background:#4f46e5;color:#fff;border-radius:6px;text-decoration:none;font-size:13px;'>공고 보기</a>");
        }
        sb.append("</div>");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
