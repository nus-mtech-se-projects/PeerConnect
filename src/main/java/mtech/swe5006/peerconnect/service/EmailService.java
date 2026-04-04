

package mtech.swe5006.peerconnect.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String FROM_ADDRESS = "peerconnectsg@gmail.com";
    private static final String SYS_FOOTER = "---\nThis is a system-generated email. Please do not reply.";
    private static final Set<String> BLOCKED_DOMAINS = Set.of("example.com", "test.com");
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send a confirmation email to a tutor after creating a tutoring class.
     */
    public void sendTutoringClassCreated(String tutorEmail, String tutorName,
                                         String classTitle, String moduleCode,
                                         String topic, String schedule,
                                         String mode, String location,
                                         String meetingLink) {
        if (isBlockedEmail(tutorEmail)) { log.info("Skipping email to blocked address: {}", tutorEmail); return; }
        SimpleMailMessage msg = prepareMsg(null, tutorEmail);
        msg.setSubject("[PeerConnectSG] Peer Tutor Group Created: " + classTitle);
        msg.setText(
            "Dear " + tutorName + ",\n\n"
            + "Your peer tutor group has been created successfully on PeerConnectSG.\n\n"
            + tutoringClassInfoBlock(classTitle, moduleCode, topic, schedule, mode, location, meetingLink)
            + "You can now manage your group and welcome tutees from the platform.\n\n"
            + SYS_FOOTER
        );
        mailSender.send(msg);
    }

    /**
     * Send a confirmation email when a tutee joins a tutoring class.
     */
    public void sendTutoringEnrollmentConfirmed(String studentEmail, String studentName,
                                                String classTitle, String moduleCode,
                                                String topic, String schedule,
                                                String tutorName, String tutorEmail,
                                                String mode, String location,
                                                String meetingLink) {
        if (isBlockedEmail(studentEmail)) { log.info("Skipping email to blocked address: {}", studentEmail); return; }
        SimpleMailMessage msg = prepareMsg(tutorEmail, studentEmail);
        msg.setSubject("[PeerConnectSG] Joined Peer Tutor Group: " + classTitle);
        msg.setText(
            "Dear " + studentName + ",\n\n"
            + "You have successfully joined the following peer tutor group on PeerConnectSG:\n\n"
            + tutoringClassInfoBlock(classTitle, moduleCode, topic, schedule, mode, location, meetingLink)
            + "Tutor:            " + tutorName + "\n\n"
            + "Please log in to PeerConnectSG to view the latest class details and updates.\n\n"
            + SYS_FOOTER
        );
        mailSender.send(msg);
    }

    /**
     * Notify a tutor when a tutee leaves a tutoring class.
     */
    public void sendTutoringStudentLeft(String tutorEmail, String studentEmail,
                                        String studentName, String classTitle,
                                        String moduleCode, String topic,
                                        String schedule, String mode,
                                        String location, String meetingLink) {
        if (isBlockedEmail(tutorEmail)) { log.info("Skipping email to blocked address: {}", tutorEmail); return; }
        SimpleMailMessage msg = prepareMsg(studentEmail, tutorEmail);
        msg.setSubject("[PeerConnectSG] Tutee Left Peer Tutor Group: " + classTitle);
        msg.setText(
            "Dear Tutor,\n\n"
            + studentName + " has left your peer tutor group on PeerConnectSG.\n\n"
            + tutoringClassInfoBlock(classTitle, moduleCode, topic, schedule, mode, location, meetingLink)
            + "This is a notification for your records.\n\n"
            + SYS_FOOTER
        );
        mailSender.send(msg);
    }

    /**
     * Notify enrolled tutees when a tutoring class is deleted by the tutor.
     */
    public void sendTutoringClassDeleted(String[] studentEmails, String classTitle,
                                         String moduleCode, String topic,
                                         String schedule, String tutorName,
                                         String tutorEmail, String mode,
                                         String location, String meetingLink) {
        studentEmails = java.util.Arrays.stream(studentEmails).filter(e -> !isBlockedEmail(e)).toArray(String[]::new);
        String[] recipients = studentEmails.length > 0
            ? studentEmails
            : (tutorEmail != null && !tutorEmail.isBlank() ? new String[]{tutorEmail} : new String[0]);
        String cc = studentEmails.length > 0 ? tutorEmail : null;
        if (recipients.length == 0) { log.info("Skipping tutoring-class-deleted email - no valid recipients"); return; }
        SimpleMailMessage msg = prepareMsg(cc, recipients);
        msg.setSubject("[PeerConnectSG] Peer Tutor Group Deleted: " + classTitle);
        msg.setText(
            "Dear Student,\n\n"
            + "The following peer tutor group has been deleted and is no longer available on PeerConnectSG:\n\n"
            + tutoringClassInfoBlock(classTitle, moduleCode, topic, schedule, mode, location, meetingLink)
            + "Tutor:            " + tutorName + "\n\n"
            + "You are welcome to browse and join other peer tutor groups on the platform.\n\n"
            + SYS_FOOTER
        );
        mailSender.send(msg);
    }

    /**
     * Notify enrolled tutees when a tutoring class is updated by the tutor.
     */
    public void sendTutoringClassUpdated(String[] studentEmails, String classTitle,
                                         String moduleCode, String topic,
                                         String schedule, String tutorName,
                                         String tutorEmail, String mode,
                                         String location, String meetingLink,
                                         String description) {
        studentEmails = java.util.Arrays.stream(studentEmails).filter(e -> !isBlockedEmail(e)).toArray(String[]::new);
        String[] recipients = studentEmails.length > 0
            ? studentEmails
            : (tutorEmail != null && !tutorEmail.isBlank() ? new String[]{tutorEmail} : new String[0]);
        String cc = studentEmails.length > 0 ? tutorEmail : null;
        if (recipients.length == 0) { log.info("Skipping tutoring-class-updated email - no valid recipients"); return; }
        SimpleMailMessage msg = prepareMsg(cc, recipients);
        msg.setSubject("[PeerConnectSG] Peer Tutor Group Updated: " + classTitle);
        msg.setText(
            "Dear Student,\n\n"
            + "The following peer tutor group has been updated on PeerConnectSG. Please review the latest details:\n\n"
            + tutoringClassInfoBlock(classTitle, moduleCode, topic, schedule, mode, location, meetingLink)
            + "    Description:    " + (description != null && !description.isBlank() ? description : "N/A") + "\n"
            + "    Tutor:          " + tutorName + "\n\n"
            + "Please log in to PeerConnectSG to view the latest class details.\n\n"
            + SYS_FOOTER
        );
        mailSender.send(msg);
    }

    /**
     * Notify owner when a user joins a group.
     */
    public void sendUserJoinedGroup(String ownerEmail, String userEmail, String userName, String groupName, String moduleSubject, String topic, String preferredSchedule) {
        if (isBlockedEmail(ownerEmail)) { log.info("Skipping email to blocked address: {}", ownerEmail); return; }
        SimpleMailMessage msg = prepareMsg(userEmail, ownerEmail);
        msg.setSubject("[PeerConnectSG] User Joined Group: " + groupName);
        msg.setText(
            "Dear Group Owner,\n\n"
            + userName + " has joined your study group:\n\n"
            + groupInfoBlock(groupName, moduleSubject, topic, preferredSchedule)
            + "\nThis is a notification for your records.\n\n"
            + SYS_FOOTER
        );
        mailSender.send(msg);
    }

    /**
     * Notify owner when a user leaves a group.
     */
    public void sendUserLeftGroup(String ownerEmail, String userEmail, String userName, String groupName, String moduleSubject, String topic, String preferredSchedule) {
        if (isBlockedEmail(ownerEmail)) { log.info("Skipping email to blocked address: {}", ownerEmail); return; }
        SimpleMailMessage msg = prepareMsg(userEmail, ownerEmail);
        msg.setSubject("[PeerConnectSG] User Left Group: " + groupName);
        msg.setText(
            "Dear Group Owner,\n\n"
            + userName + " has left your study group:\n\n"
            + groupInfoBlock(groupName, moduleSubject, topic, preferredSchedule)
            + "\nThis is a notification for your records.\n\n"
            + SYS_FOOTER
        );
        mailSender.send(msg);
    }

    /**
     * Send a password-reset verification code to the user's email.
     */
    public void sendResetCode(String toEmail, String code) {
        if (isBlockedEmail(toEmail)) { log.info("Skipping email to blocked address: {}", toEmail); return; }
        SimpleMailMessage msg = prepareMsg(null, toEmail);
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
        if (isBlockedEmail(toEmail)) { log.info("Skipping email to blocked address: {}", toEmail); return; }
        SimpleMailMessage msg = prepareMsg(null, toEmail);
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
        if (isBlockedEmail(inviteeEmail)) { log.info("Skipping email to blocked address: {}", inviteeEmail); return; }
        SimpleMailMessage msg = prepareMsg(ownerEmail, inviteeEmail);
        msg.setSubject("[PeerConnectSG] Invitation to Join Group: " + groupName);
        msg.setText(
            "Dear " + inviteeName + ",\n\n"
            + "Congratulations! You have been invited to join the following study group on PeerConnectSG:\n\n"
            + groupInfoBlock(groupName, moduleSubject, topic, preferredSchedule)
            + "\nPlease log in to PeerConnectSG to accept or decline this invitation.\n\n"
            + regardsOwnerFooter(ownerName)
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
        memberEmails = java.util.Arrays.stream(memberEmails).filter(e -> !isBlockedEmail(e)).toArray(String[]::new);
        if (memberEmails.length == 0) { log.info("Skipping group-dissolved email — all recipients blocked"); return; }
        SimpleMailMessage msg = prepareMsg(ownerEmail, memberEmails);
        msg.setSubject("[PeerConnectSG] Notice of Group Dissolution: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "We regret to inform you that the following study group has been dissolved "
            + "and is no longer active:\n\n"
            + groupInfoBlock(groupName, moduleSubject, topic, preferredSchedule)
            + "\nWe apologise for any inconvenience caused. You are welcome to create "
            + "or join other study groups on PeerConnectSG.\n\n"
            + regardsOwnerFooter(ownerName)
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
        if (isBlockedEmail(memberEmail)) { log.info("Skipping email to blocked address: {}", memberEmail); return; }
        SimpleMailMessage msg = prepareMsg(ownerEmail, memberEmail);
        msg.setSubject("[PeerConnectSG] Membership Approved: " + groupName);
        msg.setText(
            "Dear " + memberName + ",\n\n"
            + "Great news! Your request to join the following study group has been approved:\n\n"
            + groupInfoBlock(groupName, moduleSubject, topic, preferredSchedule)
            + "\nYou now have full access to this group. "
            + "Please log in to PeerConnectSG to start collaborating with your peers.\n\n"
            + regardsOwnerFooter(ownerName)
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
        if (isBlockedEmail(memberEmail)) { log.info("Skipping email to blocked address: {}", memberEmail); return; }
        SimpleMailMessage msg = prepareMsg(ownerEmail, memberEmail);
        msg.setSubject("[PeerConnectSG] Membership Request Declined: " + groupName);
        msg.setText(
            "Dear " + memberName + ",\n\n"
            + "We regret to inform you that your request to join the following study group "
            + "has been declined by the group owner:\n\n"
            + groupInfoBlock(groupName, moduleSubject, topic, preferredSchedule)
            + "\nYou are welcome to explore and join other study groups on PeerConnectSG.\n\n"
            + regardsOwnerFooter(ownerName)
        );
        mailSender.send(msg);
    }

    /**
     * Send a notification email when a study group's details have been updated.
     */
    public void sendGroupUpdated(String[] memberEmails, String groupName,
                                  String moduleSubject, String topic,
                                  String preferredSchedule,
                                  String location, String meetingLink,
                                  String description,
                                  String ownerName, String ownerEmail) {
        memberEmails = java.util.Arrays.stream(memberEmails).filter(e -> !isBlockedEmail(e)).toArray(String[]::new);
        if (memberEmails.length == 0) { log.info("Skipping group-updated email — all recipients blocked"); return; }
        SimpleMailMessage msg = prepareMsg(ownerEmail, memberEmails);
        msg.setSubject("[PeerConnectSG] UPDATED Group: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "The following study group has been updated. Please review the latest details:\n\n"
            + groupInfoBlock(groupName, moduleSubject, topic, preferredSchedule)
            + "    Location:         " + (location != null && !location.isBlank() ? location : "N/A") + "\n"
            + "    Meeting Link:     " + (meetingLink != null && !meetingLink.isBlank() ? meetingLink : "N/A") + "\n"
            + "    Description:      " + (description != null && !description.isBlank() ? description : "N/A") + "\n\n"
            + "Please log in to PeerConnectSG to view the full updated details.\n\n"
            + regardsOwnerFooter(ownerName)
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
        memberEmails = java.util.Arrays.stream(memberEmails).filter(e -> !isBlockedEmail(e)).toArray(String[]::new);
        if (memberEmails.length == 0) { log.info("Skipping session-created email — all recipients blocked"); return; }
        SimpleMailMessage msg = prepareMsg(ownerEmail, memberEmails);
        msg.setSubject("[PeerConnectSG] NEW Session for Group: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "A new study session has been scheduled for your group \"" + groupName + "\":\n\n"
            + sessionInfoBlock(sessionTitle, startsAt, endsAt, location, meetingLink)
            + "Please log in to PeerConnectSG for more details.\n\n"
            + regardsOwnerFooter(ownerName)
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
        memberEmails = java.util.Arrays.stream(memberEmails).filter(e -> !isBlockedEmail(e)).toArray(String[]::new);
        if (memberEmails.length == 0) { log.info("Skipping session-updated email — all recipients blocked"); return; }
        SimpleMailMessage msg = prepareMsg(ownerEmail, memberEmails);
        msg.setSubject("[PeerConnectSG] UPDATED Session for Group: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "A study session in your group \"" + groupName + "\" has been updated. "
            + "Please review the latest details:\n\n"
            + sessionInfoBlock(sessionTitle, startsAt, endsAt, location, meetingLink)
            + "Please log in to PeerConnectSG for more details.\n\n"
            + regardsOwnerFooter(ownerName)
        );
        mailSender.send(msg);
    }

    /**
     * Send a notification email when a study session has been cancelled/deleted.
     */
    public void sendSessionDeleted(String[] memberEmails, String groupName,
                                    String sessionTitle, String startsAt,
                                    String ownerName, String ownerEmail) {
        memberEmails = java.util.Arrays.stream(memberEmails).filter(e -> !isBlockedEmail(e)).toArray(String[]::new);
        if (memberEmails.length == 0) { log.info("Skipping session-deleted email — all recipients blocked"); return; }
        SimpleMailMessage msg = prepareMsg(ownerEmail, memberEmails);
        msg.setSubject("[PeerConnectSG] CANCELLED Session for Group: " + groupName);
        msg.setText(
            "Dear Members,\n\n"
            + "The following study session in your group \"" + groupName + "\" has been cancelled:\n\n"
            + "    Session Title: " + sessionTitle + "\n"
            + "    Was Scheduled: " + startsAt + "\n\n"
            + "We apologise for any inconvenience. Please log in to PeerConnectSG "
            + "to check for any rescheduled sessions.\n\n"
            + regardsOwnerFooter(ownerName)
        );
        mailSender.send(msg);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Creates a SimpleMailMessage with FROM set, recipient(s) as TO, and optional CC. */
    private SimpleMailMessage prepareMsg(String cc, String... to) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(FROM_ADDRESS);
        msg.setTo(to);
        if (cc != null && !cc.isBlank()) msg.setCc(cc);
        return msg;
    }

    /** Returns the standard 4-line group details block (ends with a single newline). */
    private String groupInfoBlock(String groupName, String moduleSubject, String topic, String preferredSchedule) {
        return "    Group Name:       " + groupName + "\n"
            + "    Module / Subject: " + moduleSubject + "\n"
            + "    Topic:            " + topic + "\n"
            + "    Scheduled At:     " + preferredSchedule + "\n";
    }

    /** Returns the 5-line session details block (ends with a double newline). */
    private String sessionInfoBlock(String sessionTitle, String startsAt, String endsAt, String location, String meetingLink) {
        return "    Session Title: " + sessionTitle + "\n"
            + "    Starts At:     " + startsAt + "\n"
            + "    Ends At:       " + (endsAt != null ? endsAt : "TBD") + "\n"
            + "    Location:      " + (location != null && !location.isBlank() ? location : "N/A") + "\n"
            + "    Meeting Link:  " + (meetingLink != null && !meetingLink.isBlank() ? meetingLink : "N/A") + "\n\n";
    }

    /** Returns the tutoring class details block (ends with a double newline). */
    private String tutoringClassInfoBlock(String classTitle, String moduleCode, String topic,
                                          String schedule, String mode, String location,
                                          String meetingLink) {
        return "    Class Title:    " + classTitle + "\n"
            + "    Module Code:    " + moduleCode + "\n"
            + "    Topic:          " + (topic != null && !topic.isBlank() ? topic : "N/A") + "\n"
            + "    Schedule:       " + schedule + "\n"
            + "    Mode:           " + (mode != null && !mode.isBlank() ? mode : "N/A") + "\n"
            + "    Location:       " + (location != null && !location.isBlank() ? location : "N/A") + "\n"
            + "    Meeting Link:   " + (meetingLink != null && !meetingLink.isBlank() ? meetingLink : "N/A") + "\n\n";
    }

    /** Returns "Regards, <owner> / Group Owner" followed by the system footer. */
    private String regardsOwnerFooter(String ownerName) {
        return "Regards,\n" + ownerName + "\nGroup Owner\n\n" + SYS_FOOTER;
    }

    /** Checks if the given email is in a blocked domain. */
    private boolean isBlockedEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) return false;
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        return BLOCKED_DOMAINS.contains(domain);
    }

}

