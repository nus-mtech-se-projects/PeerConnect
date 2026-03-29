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
