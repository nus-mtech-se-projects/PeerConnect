package mtech.swe5006.peerconnect.service;

import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.User;

import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final EmailService emailService;
    private final SmsService smsService;

    public NotificationService(EmailService emailService, SmsService smsService) {
        this.emailService = emailService;
        this.smsService = smsService;
    }

    public void sendPasswordReset(User user, String code, String channel) {
        String emailSubject = "PeerConnect — Password Reset Code";
        String emailBody =
            "Hi,\n\n"
            + "Your password reset verification code is:\n\n"
            + "    " + code + "\n\n"
            + "This code expires in 15 minutes.\n"
            + "If you did not request a password reset, please ignore this email.\n\n"
            + "— PeerConnect Team";
        String smsMessage = "PeerConnect: Your reset code is " + code + ". It expires in 15 minutes.";
        send(user, emailSubject, emailBody, smsMessage, channel);
    }

    public void sendChangePassword(User user, String code, String channel) {
        String emailSubject = "PeerConnect — Change Password Verification Code";
        String emailBody =
            "Hi,\n\n"
            + "You requested to change your password.\n\n"
            + "Your verification code is:\n\n"
            + "    " + code + "\n\n"
            + "This code expires in 15 minutes.\n"
            + "If you did not request this, please secure your account immediately.\n\n"
            + "— PeerConnect Team";
        String smsMessage = "PeerConnect: Your change-password code is " + code + ". It expires in 15 minutes.";
        send(user, emailSubject, emailBody, smsMessage, channel);
    }

    public void sendGeneral(String email, String phone, String subject, String message, String channel) {
        sendMessage(email, phone, subject, message, message, channel);
    }

    public void sendStudyGroupMemberAdded(
        User actor,
        User target,
        StudyGroup group,
        String membershipStatus,
        String channel) {

        if (group == null || target == null) {
            throw new IllegalArgumentException("Group and target user are required for study group member notifications.");
        }

        String groupName = firstNonBlank(group.getName(), group.getTopic(), group.getModuleCode(), "PeerConnect Group");
        String actorName = actor == null ? "A group admin" : formatName(actor);
        String targetName = formatName(target);
        String status = normalizeMembershipStatus(membershipStatus);
        String subject = "PeerConnect — Group Membership";

        String emailText;
        String smsText;

        if ("invited".equalsIgnoreCase(status)) {
            subject = "PeerConnect — Group Invitation";
            emailText =
                "Hi " + targetName + ",\n\n"
                    + actorName + " invited you to join \"" + groupName + "\".\n\n"
                    + "Check your account for your invitation status.\n\n"
                    + "— PeerConnect Team";
            smsText = actorName + " invited you to join \"" + groupName + "\" on PeerConnect.";
        } else if ("pending".equalsIgnoreCase(status)) {
            subject = "PeerConnect — Group Join Request";
            emailText =
                "Hi " + targetName + ",\n\n"
                    + "Your request to join \"" + groupName + "\" was submitted.\n"
                    + "You will be notified when the admin approves your request.\n\n"
                    + "— PeerConnect Team";
            smsText = "Your request to join \"" + groupName + "\" is pending approval.";
        } else {
            subject = "PeerConnect — Added to Group";
            emailText =
                "Hi " + targetName + ",\n\n"
                    + "You have been added to \"" + groupName + "\" by " + actorName + ".\n"
                    + "You can now access the group and coordinate with members.\n\n"
                    + "— PeerConnect Team";
            smsText = "You have been added to \"" + groupName + "\" on PeerConnect.";
        }

        send(target, subject, emailText, smsText, channel);
    }

    public void sendStudyGroupMemberAdded(StudyGroup group, User target, String membershipStatus, String channel) {
        sendStudyGroupMemberAdded(null, target, group, membershipStatus, channel);
    }

    private void send(User user, String subject, String emailText, String smsText, String channel) {
        if (user == null) {
            throw new IllegalArgumentException("Recipient user is required.");
        }
        sendMessage(user.getEmail(), user.getPhone(), subject, emailText, smsText, channel);
    }

    private void sendMessage(String email, String phone, String subject, String emailText,
                            String smsText, String channel) {

        String chosenChannel = normalizeChannel(channel);
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        boolean smsAvailable = smsService.canSend() && hasPhone;

        boolean sendEmail = false;
        boolean sendSms = false;

        switch (chosenChannel) {
            case "email" -> sendEmail = hasEmail;
            case "sms" -> sendSms = smsAvailable;
            case "both" -> {
                sendEmail = hasEmail;
                sendSms = smsAvailable;
            }
            default -> {
                sendEmail = hasEmail;
                sendSms = smsAvailable && !hasEmail;
            }
        }

        if (!sendEmail && !sendSms) {
            throw new IllegalStateException("No available delivery channel for this recipient.");
        }

        if (sendEmail) {
            emailService.sendGeneralNotification(email, subject, emailText);
        }
        if (sendSms) {
            smsService.sendSms(phone, smsText);
        }
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "auto";
        }
        return channel.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMembershipStatus(String membershipStatus) {
        if (membershipStatus == null || membershipStatus.isBlank()) {
            return "approved";
        }
        return membershipStatus.trim().toLowerCase(Locale.ROOT);
    }

    private String formatName(User user) {
        if (user == null) return "Unknown";
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? user.getEmail() : full;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
