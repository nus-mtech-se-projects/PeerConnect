package mtech.swe5006.peerconnect.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final String FROM_ADDRESS = "peerconnectsg@gmail.com";

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send a password-reset verification code to the user's email.
     */
    public void sendResetCode(String toEmail, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
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
        msg.setFrom(FROM_ADDRESS);
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

    /**
     * Send an invitation email to a user who has been invited to join a study group.
     */
    public void sendGroupInvitation(String inviteeEmail, String inviteeName,
                                     String groupName, String moduleSubject,
                                     String topic, String preferredSchedule,
                                     String ownerName, String ownerEmail) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
        msg.setTo(inviteeEmail);
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            msg.setCc(ownerEmail);
        }
        msg.setSubject("[PeerConnectSG] Invitation to Join Group: " + groupName);
        msg.setText(
            "Dear " + inviteeName + ",\n\n"
            + "Congratulations! You have been invited to join the following study group on PeerConnectSG:\n\n"
            + "    Group Name:       " + groupName + "\n"
            + "    Module / Subject: " + moduleSubject + "\n"
            + "    Topic:            " + topic + "\n"
            + "    Scheduled At:     " + preferredSchedule + "\n\n"
            + "Please log in to PeerConnectSG to accept or decline this invitation.\n\n"
            + "Regards,\n"
            + ownerName + "\n"
            + "Group Owner\n\n"
            + "---\n"
            + "This is a system-generated email. Please do not reply."
        );
        mailSender.send(msg);
    }

    /**
     * Send a dissolution notification email to all members of a dissolved group.
     */
    public void sendGroupDissolved(String[] memberEmails, String groupName,
                                    String moduleSubject, String topic,
                                    String preferredSchedule,
                                    String ownerName, String ownerEmail) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
        msg.setTo(memberEmails);
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            msg.setCc(ownerEmail);
        }
        msg.setSubject("[PeerConnectSG] Notice of Group Dissolution: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "We regret to inform you that the following study group has been dissolved "
            + "and is no longer active:\n\n"
            + "    Group Name:       " + groupName + "\n"
            + "    Module / Subject: " + moduleSubject + "\n"
            + "    Topic:            " + topic + "\n"
            + "    Scheduled At:     " + preferredSchedule + "\n\n"
            + "We apologise for any inconvenience caused. You are welcome to create "
            + "or join other study groups on PeerConnectSG.\n\n"
            + "Regards,\n"
            + ownerName + "\n"
            + "Group Owner\n\n"
            + "---\n"
            + "This is a system-generated email. Please do not reply."
        );
        mailSender.send(msg);
    }

    /**
     * Send a notification email to a member whose request has been approved.
     */
    public void sendMemberApproved(String memberEmail, String memberName,
                                    String groupName, String moduleSubject,
                                    String topic, String preferredSchedule,
                                    String ownerName, String ownerEmail) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
        msg.setTo(memberEmail);
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            msg.setCc(ownerEmail);
        }
        msg.setSubject("[PeerConnectSG] Membership Approved: " + groupName);
        msg.setText(
            "Dear " + memberName + ",\n\n"
            + "Great news! Your request to join the following study group has been approved:\n\n"
            + "    Group Name:       " + groupName + "\n"
            + "    Module / Subject: " + moduleSubject + "\n"
            + "    Topic:            " + topic + "\n"
            + "    Scheduled At:     " + preferredSchedule + "\n\n"
            + "You now have full access to this group. "
            + "Please log in to PeerConnectSG to start collaborating with your peers.\n\n"
            + "Regards,\n"
            + ownerName + "\n"
            + "Group Owner\n\n"
            + "---\n"
            + "This is a system-generated email. Please do not reply."
        );
        mailSender.send(msg);
    }

    /**
     * Send a notification email to a member whose request has been rejected.
     */
    public void sendMemberRejected(String memberEmail, String memberName,
                                    String groupName, String moduleSubject,
                                    String topic, String preferredSchedule,
                                    String ownerName, String ownerEmail) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
        msg.setTo(memberEmail);
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            msg.setCc(ownerEmail);
        }
        msg.setSubject("[PeerConnectSG] Membership Request Declined: " + groupName);
        msg.setText(
            "Dear " + memberName + ",\n\n"
            + "We regret to inform you that your request to join the following study group "
            + "has been declined by the group owner:\n\n"
            + "    Group Name:       " + groupName + "\n"
            + "    Module / Subject: " + moduleSubject + "\n"
            + "    Topic:            " + topic + "\n"
            + "    Scheduled At:     " + preferredSchedule + "\n\n"
            + "You are welcome to explore and join other study groups on PeerConnectSG.\n\n"
            + "Regards,\n"
            + ownerName + "\n"
            + "Group Owner\n\n"
            + "---\n"
            + "This is a system-generated email. Please do not reply."
        );
        mailSender.send(msg);
    }

    /**
     * Send a notification email when a study group's details have been updated.
     */
    public void sendGroupUpdated(String[] memberEmails, String groupName,
                                  String moduleSubject, String topic,
                                  String preferredSchedule,
                                  String ownerName, String ownerEmail) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
        msg.setTo(memberEmails);
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            msg.setCc(ownerEmail);
        }
        msg.setSubject("[PeerConnectSG] UPDATED Group: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "The following study group has been updated. Please review the latest details:\n\n"
            + "    Group Name:       " + groupName + "\n"
            + "    Module / Subject: " + moduleSubject + "\n"
            + "    Topic:            " + topic + "\n"
            + "    Scheduled At:     " + preferredSchedule + "\n\n"
            + "Please log in to PeerConnectSG to view the full updated details.\n\n"
            + "Regards,\n"
            + ownerName + "\n"
            + "Group Owner\n\n"
            + "---\n"
            + "This is a system-generated email. Please do not reply."
        );
        mailSender.send(msg);
    }

    /**
     * Send a notification email when a new study session has been created.
     */
    public void sendSessionCreated(String[] memberEmails, String groupName,
                                    String sessionTitle, String startsAt,
                                    String endsAt, String location,
                                    String meetingLink,
                                    String ownerName, String ownerEmail) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
        msg.setTo(memberEmails);
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            msg.setCc(ownerEmail);
        }
        msg.setSubject("[PeerConnectSG] NEW Session for Group: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "A new study session has been scheduled for your group \"" + groupName + "\":\n\n"
            + "    Session Title: " + sessionTitle + "\n"
            + "    Starts At:     " + startsAt + "\n"
            + "    Ends At:       " + (endsAt != null ? endsAt : "TBD") + "\n"
            + "    Location:      " + (location != null && !location.isBlank() ? location : "N/A") + "\n"
            + "    Meeting Link:  " + (meetingLink != null && !meetingLink.isBlank() ? meetingLink : "N/A") + "\n\n"
            + "Please log in to PeerConnectSG for more details.\n\n"
            + "Regards,\n"
            + ownerName + "\n"
            + "Group Owner\n\n"
            + "---\n"
            + "This is a system-generated email. Please do not reply."
        );
        mailSender.send(msg);
    }

    /**
     * Send a notification email when a study session has been updated.
     */
    public void sendSessionUpdated(String[] memberEmails, String groupName,
                                    String sessionTitle, String startsAt,
                                    String endsAt, String location,
                                    String meetingLink,
                                    String ownerName, String ownerEmail) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
        msg.setTo(memberEmails);
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            msg.setCc(ownerEmail);
        }
        msg.setSubject("[PeerConnectSG] UPDATED Session for Group: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "A study session in your group \"" + groupName + "\" has been updated. "
            + "Please review the latest details:\n\n"
            + "    Session Title: " + sessionTitle + "\n"
            + "    Starts At:     " + startsAt + "\n"
            + "    Ends At:       " + (endsAt != null ? endsAt : "TBD") + "\n"
            + "    Location:      " + (location != null && !location.isBlank() ? location : "N/A") + "\n"
            + "    Meeting Link:  " + (meetingLink != null && !meetingLink.isBlank() ? meetingLink : "N/A") + "\n\n"
            + "Please log in to PeerConnectSG for more details.\n\n"
            + "Regards,\n"
            + ownerName + "\n"
            + "Group Owner\n\n"
            + "---\n"
            + "This is a system-generated email. Please do not reply."
        );
        mailSender.send(msg);
    }

    /**
     * Send a notification email when a study session has been cancelled/deleted.
     */
    public void sendSessionDeleted(String[] memberEmails, String groupName,
                                    String sessionTitle, String startsAt,
                                    String ownerName, String ownerEmail) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
        msg.setTo(memberEmails);
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            msg.setCc(ownerEmail);
        }
        msg.setSubject("[PeerConnectSG] CANCELLED Session for Group: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "The following study session in your group \"" + groupName + "\" has been cancelled:\n\n"
            + "    Session Title: " + sessionTitle + "\n"
            + "    Was Scheduled: " + startsAt + "\n\n"
            + "We apologise for any inconvenience. Please log in to PeerConnectSG "
            + "to check for any rescheduled sessions.\n\n"
            + "Regards,\n"
            + ownerName + "\n"
            + "Group Owner\n\n"
            + "---\n"
            + "This is a system-generated email. Please do not reply."
        );
        mailSender.send(msg);
    }
}
