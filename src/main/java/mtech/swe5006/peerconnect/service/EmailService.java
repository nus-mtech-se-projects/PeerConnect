package mtech.swe5006.peerconnect.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send a password-reset verification code to the user's email.
     */
    public void sendResetCode(String toEmail, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("peerconnectsg@gmail.com");
        msg.setTo(toEmail);
        msg.setSubject("PeerConnect — Password Reset Code");
        msg.setText(
            "Hi,\n\n"
            + "Your password reset verification code is:\n\n"
            + "    " + code + "\n\n"
            + "This code expires in 15 minutes.\n"
            + "If you did not request a password reset, please ignore this email.\n\n"
            + "— PeerConnect Team"
        );
        mailSender.send(msg);
    }

    /**
     * Send a change-password verification code to the user's email.
     */
    public void sendChangePasswordCode(String toEmail, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("peerconnectsg@gmail.com");
        msg.setTo(toEmail);
        msg.setSubject("PeerConnect — Change Password Verification Code");
        msg.setText(
            "Hi,\n\n"
            + "You requested to change your password.\n\n"
            + "Your verification code is:\n\n"
            + "    " + code + "\n\n"
            + "This code expires in 15 minutes.\n"
            + "If you did not request this, please secure your account immediately.\n\n"
            + "— PeerConnect Team"
        );
        mailSender.send(msg);
    }
}
