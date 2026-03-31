package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    JavaMailSender mailSender;

    @InjectMocks
    EmailService emailService;

    private ArgumentCaptor<SimpleMailMessage> msgCaptor;

    @BeforeEach
    void setup() {
        msgCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    }

    @Nested
    @DisplayName("sendTutoringClassCreated")
    class TutoringClassCreated {

        @Test
        @DisplayName("sends class-created confirmation to the tutor")
        void sendsCreatedEmail() {
            emailService.sendTutoringClassCreated(
                "alice@u.nus.edu", "Alice Tan",
                "Advanced Java", "CS5000", "Concurrency",
                "Fridays 6pm", "online", null, "https://zoom.us/java"
            );

            verify(mailSender).send(msgCaptor.capture());
            SimpleMailMessage msg = msgCaptor.getValue();

            assertThat(msg.getTo()).containsExactly("alice@u.nus.edu");
            assertThat(msg.getCc()).isNull();
            assertThat(msg.getSubject()).contains("Peer Tutor Group Created");
            assertThat(msg.getSubject()).contains("Advanced Java");
            assertThat(msg.getText()).contains("Alice Tan");
            assertThat(msg.getText()).contains("CS5000");
            assertThat(msg.getText()).contains("Concurrency");
            assertThat(msg.getText()).contains("Fridays 6pm");
            assertThat(msg.getFrom()).isEqualTo("peerconnectsg@gmail.com");
        }
    }

    @Nested
    @DisplayName("sendTutoringEnrollmentConfirmed")
    class TutoringEnrollmentConfirmed {

        @Test
        @DisplayName("sends enrollment confirmation to the tutee and cc's the tutor")
        void sendsEnrollmentEmail() {
            emailService.sendTutoringEnrollmentConfirmed(
                "bob@u.nus.edu", "Bob Lim",
                "Advanced Java", "CS5000", "Concurrency",
                "Fridays 6pm", "Alice Tan", "alice@u.nus.edu",
                "online", null, "https://zoom.us/java"
            );

            verify(mailSender).send(msgCaptor.capture());
            SimpleMailMessage msg = msgCaptor.getValue();

            assertThat(msg.getTo()).containsExactly("bob@u.nus.edu");
            assertThat(msg.getCc()).containsExactly("alice@u.nus.edu");
            assertThat(msg.getSubject()).contains("Joined Peer Tutor Group");
            assertThat(msg.getSubject()).contains("Advanced Java");
            assertThat(msg.getText()).contains("Bob Lim");
            assertThat(msg.getText()).contains("Alice Tan");
            assertThat(msg.getText()).contains("CS5000");
            assertThat(msg.getFrom()).isEqualTo("peerconnectsg@gmail.com");
        }
    }

    @Nested
    @DisplayName("sendTutoringStudentLeft")
    class TutoringStudentLeft {

        @Test
        @DisplayName("sends leave notification to the tutor and cc's the student")
        void sendsLeaveEmail() {
            emailService.sendTutoringStudentLeft(
                "alice@u.nus.edu", "bob@u.nus.edu",
                "Bob Lim", "Advanced Java", "CS5000",
                "Concurrency", "Fridays 6pm", "online",
                null, "https://zoom.us/java"
            );

            verify(mailSender).send(msgCaptor.capture());
            SimpleMailMessage msg = msgCaptor.getValue();

            assertThat(msg.getTo()).containsExactly("alice@u.nus.edu");
            assertThat(msg.getCc()).containsExactly("bob@u.nus.edu");
            assertThat(msg.getSubject()).contains("Tutee Left Peer Tutor Group");
            assertThat(msg.getSubject()).contains("Advanced Java");
            assertThat(msg.getText()).contains("Bob Lim");
            assertThat(msg.getText()).contains("CS5000");
            assertThat(msg.getFrom()).isEqualTo("peerconnectsg@gmail.com");
        }
    }

    @Nested
    @DisplayName("sendTutoringClassDeleted")
    class TutoringClassDeleted {

        @Test
        @DisplayName("sends class deletion notice to enrolled students and cc's the tutor")
        void sendsDeletedEmail() {
            emailService.sendTutoringClassDeleted(
                new String[]{"bob@u.nus.edu"},
                "Advanced Java", "CS5000", "Concurrency",
                "Fridays 6pm", "Alice Tan", "alice@u.nus.edu",
                "online", null, "https://zoom.us/java"
            );

            verify(mailSender).send(msgCaptor.capture());
            SimpleMailMessage msg = msgCaptor.getValue();

            assertThat(msg.getTo()).containsExactly("bob@u.nus.edu");
            assertThat(msg.getCc()).containsExactly("alice@u.nus.edu");
            assertThat(msg.getSubject()).contains("Peer Tutor Group Deleted");
            assertThat(msg.getSubject()).contains("Advanced Java");
            assertThat(msg.getText()).contains("Alice Tan");
            assertThat(msg.getText()).contains("CS5000");
            assertThat(msg.getFrom()).isEqualTo("peerconnectsg@gmail.com");
        }
    }

    @Nested
    @DisplayName("sendMemberApproved")
    class MemberApproved {

        @Test
        @DisplayName("sends approval email with correct subject and recipient")
        void sendsApprovalEmail() {
            emailService.sendMemberApproved(
                "bob@u.nus.edu", "Bob Lee",
                "Study Group A", "CS5000", "Algorithms",
                "01 Apr 2026, 06:00 PM",
                "Alice Tan", "alice@u.nus.edu"
            );

            verify(mailSender).send(msgCaptor.capture());
            SimpleMailMessage msg = msgCaptor.getValue();

            assertThat(msg.getTo()).containsExactly("bob@u.nus.edu");
            assertThat(msg.getCc()).containsExactly("alice@u.nus.edu");
            assertThat(msg.getSubject()).contains("Membership Approved");
            assertThat(msg.getSubject()).contains("Study Group A");
            assertThat(msg.getText()).contains("Bob Lee");
            assertThat(msg.getText()).contains("CS5000");
            assertThat(msg.getText()).contains("Algorithms");
            assertThat(msg.getFrom()).isEqualTo("peerconnectsg@gmail.com");
        }

        @Test
        @DisplayName("does not set CC when owner email is blank")
        void noCcWhenBlank() {
            emailService.sendMemberApproved(
                "bob@u.nus.edu", "Bob Lee",
                "Study Group A", "CS5000", "Algorithms",
                "01 Apr 2026, 06:00 PM",
                "Alice Tan", ""
            );

            verify(mailSender).send(msgCaptor.capture());
            SimpleMailMessage msg = msgCaptor.getValue();
            assertThat(msg.getCc()).isNull();
        }

        @Test
        @DisplayName("does not set CC when owner email is null")
        void noCcWhenNull() {
            emailService.sendMemberApproved(
                "bob@u.nus.edu", "Bob Lee",
                "Study Group A", "CS5000", "Algorithms",
                "01 Apr 2026, 06:00 PM",
                "Alice Tan", null
            );

            verify(mailSender).send(msgCaptor.capture());
            SimpleMailMessage msg = msgCaptor.getValue();
            assertThat(msg.getCc()).isNull();
        }
    }

    @Nested
    @DisplayName("sendMemberRejected")
    class MemberRejected {

        @Test
        @DisplayName("sends rejection email with correct subject and recipient")
        void sendsRejectionEmail() {
            emailService.sendMemberRejected(
                "bob@u.nus.edu", "Bob Lee",
                "Study Group B", "CS6000", "Data Structures",
                "02 Apr 2026, 07:00 PM",
                "Alice Tan", "alice@u.nus.edu"
            );

            verify(mailSender).send(msgCaptor.capture());
            SimpleMailMessage msg = msgCaptor.getValue();

            assertThat(msg.getTo()).containsExactly("bob@u.nus.edu");
            assertThat(msg.getCc()).containsExactly("alice@u.nus.edu");
            assertThat(msg.getSubject()).contains("Membership Request Declined");
            assertThat(msg.getSubject()).contains("Study Group B");
            assertThat(msg.getText()).contains("Bob Lee");
            assertThat(msg.getText()).contains("declined by the group owner");
            assertThat(msg.getText()).contains("CS6000");
            assertThat(msg.getFrom()).isEqualTo("peerconnectsg@gmail.com");
        }

        @Test
        @DisplayName("body encourages exploring other groups")
        void bodyContent() {
            emailService.sendMemberRejected(
                "bob@u.nus.edu", "Bob Lee",
                "Study Group B", "CS6000", "Data Structures",
                "02 Apr 2026, 07:00 PM",
                "Alice Tan", "alice@u.nus.edu"
            );

            verify(mailSender).send(msgCaptor.capture());
            SimpleMailMessage msg = msgCaptor.getValue();
            assertThat(msg.getText()).contains("explore and join other study groups");
        }
    }
}
