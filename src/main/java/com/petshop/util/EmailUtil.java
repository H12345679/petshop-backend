package com.petshop.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

/**
 * 邮件发送工具类：发送 HTML 格式的验证码邮件。
 */
@Component
public class EmailUtil {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    /** 发送验证码邮件 */
    public void sendVerifyCode(String to, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("【PetShop】邮箱验证码");

            String html = buildHtml(code);
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("邮件发送失败，请稍后重试", e);
        }
    }

    /** 构建 HTML 邮件内容 */
    private String buildHtml(String code) {
        return "<div style=\"max-width:600px;margin:0 auto;padding:30px;font-family:Arial,sans-serif;\">"
                + "<h2 style=\"color:#333;text-align:center;\">PetShop 宠物商店</h2>"
                + "<div style=\"background:#f5f5f5;padding:30px;border-radius:8px;margin:20px 0;\">"
                + "<p style=\"font-size:16px;color:#555;\">您的邮箱验证码为：</p>"
                + "<p style=\"font-size:36px;font-weight:bold;color:#e74c3c;text-align:center;letter-spacing:8px;margin:20px 0;\">"
                + code + "</p>"
                + "<p style=\"font-size:14px;color:#999;\">验证码 5 分钟内有效，请勿泄露给他人。</p>"
                + "</div>"
                + "<p style=\"font-size:12px;color:#bbb;text-align:center;\">"
                + "如非本人操作，请忽略此邮件。</p>"
                + "</div>";
    }
}
