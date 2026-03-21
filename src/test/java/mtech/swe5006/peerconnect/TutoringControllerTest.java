package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.TutoringController;
import mtech.swe5006.peerconnect.data.sql.TutoringClass;
import mtech.swe5006.peerconnect.data.sql.TutoringClassRepository;
import mtech.swe5006.peerconnect.data.sql.TutoringEnrollment;
import mtech.swe5006.peerconnect.data.sql.TutoringEnrollmentRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TutoringControllerTest {

    @Mock TutoringClassRepository classRepository;
    @Mock TutoringEnrollmentRepository enrollmentRepository;
    @Mock UserRepository userRepository;

    @InjectMocks TutoringController controller;

    private User tutor;
    private User student;

    @BeforeEach
    void setup() {
        tutor = new User();
        tutor.setId(UUID.randomUUID());
        tutor.setEmail("tutor@u.nus.edu");
        tutor.setFirstName("Alice");
        tutor.setLastName("Tan");

        student = new User();
        student.setId(UUID.randomUUID());
        student.setEmail("student@u.nus.edu");
        student.setFirstName("Bob");
        student.setLastName("Lee");
    }

    private Authentication authFor(User u) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(u.getEmail());
        return auth;
    }

    private TutoringClass makeClass(UUID id, UUID createdBy, short maxStudents) {
        TutoringClass tc = new TutoringClass();
        tc.setId(id);
        tc.setTitle("CS Help Session");
        tc.setModuleCode("CS3000");
        tc.setSchedule("Fri 6pm");
        tc.setMode("online");
        tc.setMeetingLink("https://zoom.us/test");
        tc.setMaxStudents(maxStudents);
        tc.setCreatedBy(createdBy);
        return tc;
    }

    // ── GET /api/tutoring/classes ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/tutoring/classes")
    class GetAllClasses {

        @Test
        @DisplayName("returns list with enrollment info for current user")
        void returnsListWithEnrollmentInfo() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
            when(classRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(tc));
            when(userRepository.findById(tutor.getId())).thenReturn(Optional.of(tutor));
            when(enrollmentRepository.countByClassId(classId)).thenReturn(2L);
            when(enrollmentRepository.findByClassIdAndUserId(classId, tutor.getId()))
                    .thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.getAllClasses(authFor(tutor));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            List<?> body = (List<?>) res.getBody();
            assertThat(body).hasSize(1);

            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) body.get(0);
            assertThat(row.get("enrolledCount")).isEqualTo(2L);
            assertThat(row.get("isTutor")).isEqualTo(true);
            assertThat(row.get("enrolled")).isEqualTo(false);
            assertThat(row.get("tutorName")).isEqualTo("Alice Tan");
        }

        @Test
        @DisplayName("returns 404 when user not found")
        void returns404WhenUserNotFound() {
            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.getAllClasses(authFor(tutor));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("student shows enrolled=true when already enrolled")
        void enrolledFlagTrueForStudent() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);

            when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(classRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(tc));
            when(userRepository.findById(tutor.getId())).thenReturn(Optional.of(tutor));
            when(enrollmentRepository.countByClassId(classId)).thenReturn(1L);
            when(enrollmentRepository.findByClassIdAndUserId(classId, student.getId()))
                    .thenReturn(Optional.of(new TutoringEnrollment()));

            ResponseEntity<?> res = controller.getAllClasses(authFor(student));

            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) ((List<?>) res.getBody()).get(0);
            assertThat(row.get("enrolled")).isEqualTo(true);
            assertThat(row.get("isTutor")).isEqualTo(false);
        }
    }

    // ── POST /api/tutoring/classes ────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/tutoring/classes")
    class CreateClass {

        @Test
        @DisplayName("creates online class successfully")
        void createsOnlineClass() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Algo Help");
            body.put("moduleCode", "CS2040");
            body.put("schedule", "Mon 5pm");
            body.put("mode", "online");
            body.put("meetingLink", "https://zoom.us/algo");
            body.put("maxStudents", 10);

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
            when(classRepository.save(any())).thenAnswer(inv -> {
                TutoringClass saved = inv.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });
            when(userRepository.findById(tutor.getId())).thenReturn(Optional.of(tutor));
            when(enrollmentRepository.countByClassId(any())).thenReturn(0L);
            when(enrollmentRepository.findByClassIdAndUserId(any(), any())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.createClass(authFor(tutor), body);

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(classRepository).save(any(TutoringClass.class));

            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) res.getBody();
            assertThat(row.get("moduleCode")).isEqualTo("CS2040");
            assertThat(row.get("mode")).isEqualTo("online");
            assertThat(row.get("isTutor")).isEqualTo(true);
        }

        @Test
        @DisplayName("creates in-person class successfully")
        void createsInPersonClass() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "DB Tutorial");
            body.put("moduleCode", "CS4221");
            body.put("schedule", "Tue 3pm");
            body.put("mode", "in-person");
            body.put("location", "COM1-B112");
            body.put("maxStudents", 5);

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
            when(classRepository.save(any())).thenAnswer(inv -> {
                TutoringClass saved = inv.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });
            when(userRepository.findById(tutor.getId())).thenReturn(Optional.of(tutor));
            when(enrollmentRepository.countByClassId(any())).thenReturn(0L);
            when(enrollmentRepository.findByClassIdAndUserId(any(), any())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.createClass(authFor(tutor), body);

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        }

        @Test
        @DisplayName("400 when title is missing")
        void returns400WhenTitleMissing() {
            Map<String, Object> body = new HashMap<>();
            body.put("moduleCode", "CS3000");
            body.put("schedule", "Mon 5pm");
            body.put("mode", "online");
            body.put("meetingLink", "https://zoom.us/test");

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));

            ResponseEntity<?> res = controller.createClass(authFor(tutor), body);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) res.getBody();
            assertThat(err.get("error")).isEqualTo("Class title is required");
        }

        @Test
        @DisplayName("400 when module code is missing")
        void returns400WhenModuleCodeMissing() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Some Title");
            body.put("schedule", "Mon 5pm");
            body.put("mode", "online");
            body.put("meetingLink", "https://zoom.us/test");

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));

            ResponseEntity<?> res = controller.createClass(authFor(tutor), body);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) res.getBody();
            assertThat(err.get("error")).isEqualTo("Module code is required");
        }

        @Test
        @DisplayName("400 when mode is invalid")
        void returns400WhenModeInvalid() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Test Class");
            body.put("moduleCode", "CS3000");
            body.put("schedule", "Mon 5pm");
            body.put("mode", "weekend");

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));

            ResponseEntity<?> res = controller.createClass(authFor(tutor), body);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) res.getBody();
            assertThat(err.get("error")).isEqualTo("Mode must be one of: online, in-person, hybrid");
        }

        @Test
        @DisplayName("400 when in-person class has no location")
        void returns400WhenInPersonMissingLocation() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Test Class");
            body.put("moduleCode", "CS3000");
            body.put("schedule", "Mon 5pm");
            body.put("mode", "in-person");

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));

            ResponseEntity<?> res = controller.createClass(authFor(tutor), body);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) res.getBody();
            assertThat(err.get("error")).isEqualTo("Location is required for in-person/hybrid classes");
        }

        @Test
        @DisplayName("400 when online class has no meeting link")
        void returns400WhenOnlineMissingMeetingLink() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Test Class");
            body.put("moduleCode", "CS3000");
            body.put("schedule", "Mon 5pm");
            body.put("mode", "online");

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));

            ResponseEntity<?> res = controller.createClass(authFor(tutor), body);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) res.getBody();
            assertThat(err.get("error")).isEqualTo("Meeting link is required for online/hybrid classes");
        }

        @Test
        @DisplayName("defaults maxStudents to 5 when not provided")
        void defaultsMaxStudentsToFive() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Test Class");
            body.put("moduleCode", "CS3000");
            body.put("schedule", "Mon 5pm");
            body.put("mode", "online");
            body.put("meetingLink", "https://zoom.us/test");
            // maxStudents intentionally omitted

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
            when(classRepository.save(any())).thenAnswer(inv -> {
                TutoringClass saved = inv.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });
            when(userRepository.findById(tutor.getId())).thenReturn(Optional.of(tutor));
            when(enrollmentRepository.countByClassId(any())).thenReturn(0L);
            when(enrollmentRepository.findByClassIdAndUserId(any(), any())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.createClass(authFor(tutor), body);

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) res.getBody();
            assertThat(row.get("maxStudents")).isEqualTo((short) 5);
        }

        @Test
        @DisplayName("404 when user not found")
        void returns404WhenUserNotFound() {
            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.createClass(authFor(tutor), new HashMap<>());

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── DELETE /api/tutoring/classes/{id} ────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/tutoring/classes/{id}")
    class DeleteClass {

        @Test
        @DisplayName("tutor can delete their own class")
        void tutorCanDeleteOwnClass() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
            when(classRepository.findById(classId)).thenReturn(Optional.of(tc));

            ResponseEntity<?> res = controller.deleteClass(classId, authFor(tutor));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(enrollmentRepository).deleteByClassId(classId);
            verify(classRepository).delete(tc);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body.get("deleted")).isEqualTo(true);
        }

        @Test
        @DisplayName("403 when student tries to delete another user's class")
        void returns403WhenNotOwner() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);

            when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(classRepository.findById(classId)).thenReturn(Optional.of(tc));

            ResponseEntity<?> res = controller.deleteClass(classId, authFor(student));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(classRepository, never()).delete(any());
        }

        @Test
        @DisplayName("404 when class not found")
        void returns404WhenClassNotFound() {
            UUID classId = UUID.randomUUID();

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
            when(classRepository.findById(classId)).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.deleteClass(classId, authFor(tutor));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── POST /api/tutoring/classes/{id}/enroll ────────────────────────────────

    @Nested
    @DisplayName("POST /api/tutoring/classes/{id}/enroll")
    class Enroll {

        @Test
        @DisplayName("student can enroll in a class")
        void studentCanEnroll() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);

            when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(classRepository.findById(classId)).thenReturn(Optional.of(tc));
            when(enrollmentRepository.findByClassIdAndUserId(classId, student.getId()))
                    .thenReturn(Optional.empty());
            when(enrollmentRepository.countByClassId(classId)).thenReturn(2L);

            ResponseEntity<?> res = controller.enroll(classId, authFor(student));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(enrollmentRepository).save(any(TutoringEnrollment.class));

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body.get("enrolled")).isEqualTo(true);
            assertThat(body.get("enrolledCount")).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns alreadyEnrolled when student enrolls twice")
        void alreadyEnrolledReturnsOk() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);

            when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(classRepository.findById(classId)).thenReturn(Optional.of(tc));
            when(enrollmentRepository.findByClassIdAndUserId(classId, student.getId()))
                    .thenReturn(Optional.of(new TutoringEnrollment()));

            ResponseEntity<?> res = controller.enroll(classId, authFor(student));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body.get("alreadyEnrolled")).isEqualTo(true);
            verify(enrollmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("400 when class is full")
        void returns400WhenClassFull() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 3);

            when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(classRepository.findById(classId)).thenReturn(Optional.of(tc));
            when(enrollmentRepository.findByClassIdAndUserId(classId, student.getId()))
                    .thenReturn(Optional.empty());
            when(enrollmentRepository.countByClassId(classId)).thenReturn(3L);

            ResponseEntity<?> res = controller.enroll(classId, authFor(student));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) res.getBody();
            assertThat(err.get("error")).isEqualTo("Tutoring class is full");
        }

        @Test
        @DisplayName("400 when tutor tries to enroll in own class")
        void returns400WhenTutorEnrollsOwnClass() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
            when(classRepository.findById(classId)).thenReturn(Optional.of(tc));

            ResponseEntity<?> res = controller.enroll(classId, authFor(tutor));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) res.getBody();
            assertThat(err.get("error")).isEqualTo("Tutor cannot enroll in their own class");
        }

        @Test
        @DisplayName("404 when class not found")
        void returns404WhenClassNotFound() {
            UUID classId = UUID.randomUUID();

            when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(classRepository.findById(classId)).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.enroll(classId, authFor(student));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── POST /api/tutoring/classes/{id}/leave ────────────────────────────────

    @Nested
    @DisplayName("POST /api/tutoring/classes/{id}/leave")
    class Leave {

        @Test
        @DisplayName("enrolled student can leave a class")
        void studentCanLeave() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);
            TutoringEnrollment enrollment = new TutoringEnrollment();

            when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(classRepository.findById(classId)).thenReturn(Optional.of(tc));
            when(enrollmentRepository.findByClassIdAndUserId(classId, student.getId()))
                    .thenReturn(Optional.of(enrollment));
            when(enrollmentRepository.countByClassId(classId)).thenReturn(3L);

            ResponseEntity<?> res = controller.leave(classId, authFor(student));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(enrollmentRepository).deleteByClassIdAndUserId(classId, student.getId());

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body.get("enrolled")).isEqualTo(false);
            assertThat(body.get("enrolledCount")).isEqualTo(2L);
        }

        @Test
        @DisplayName("returns alreadyLeft when student not enrolled")
        void alreadyLeftReturnsOk() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);

            when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(classRepository.findById(classId)).thenReturn(Optional.of(tc));
            when(enrollmentRepository.findByClassIdAndUserId(classId, student.getId()))
                    .thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.leave(classId, authFor(student));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body.get("alreadyLeft")).isEqualTo(true);
            verify(enrollmentRepository, never()).deleteByClassIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("400 when tutor tries to leave their own class")
        void returns400WhenTutorLeaves() {
            UUID classId = UUID.randomUUID();
            TutoringClass tc = makeClass(classId, tutor.getId(), (short) 5);

            when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
            when(classRepository.findById(classId)).thenReturn(Optional.of(tc));

            ResponseEntity<?> res = controller.leave(classId, authFor(tutor));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) res.getBody();
            assertThat(err.get("error")).isEqualTo("Tutor cannot leave their own class");
        }

        @Test
        @DisplayName("404 when class not found")
        void returns404WhenClassNotFound() {
            UUID classId = UUID.randomUUID();

            when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(classRepository.findById(classId)).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.leave(classId, authFor(student));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
