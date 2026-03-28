package mtech.swe5006.peerconnect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final String DEFAULT_FROM = "peerconnectsg@gmail.com";
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    /**
     * Send a password-reset verification code to the user's email.
     */
    public void sendResetCode(String toEmail, String code) {
        sendGeneralNotification(
            toEmail,
            "PeerConnect — Password Reset Code",
            "Hi,\n\n"
            + "Your password reset verification code is:\n\n"
            + "    " + code + "\n\n"
            + "This code expires in 15 minutes.\n"
            + "If you did not request a password reset, please ignore this email.\n\n"
            + "— PeerConnect Team"
        );
    }

    /**
     * Send a change-password verification code to the user's email.
     */
    public void sendChangePasswordCode(String toEmail, String code) {
        sendGeneralNotification(
            toEmail,
            "PeerConnect — Change Password Verification Code",
            "Hi,\n\n"
            + "You requested to change your password.\n\n"
            + "Your verification code is:\n\n"
            + "    " + code + "\n\n"
            + "This code expires in 15 minutes.\n"
            + "If you did not request this, please secure your account immediately.\n\n"
            + "— PeerConnect Team"
        );
    }

    public void sendGeneralNotification(String toEmail, String subject, String body) {
        if (mailSender == null) {
            log.warn("JavaMailSender not configured. Skipping email to {}", toEmail);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(DEFAULT_FROM);
        msg.setTo(toEmail);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }
}
