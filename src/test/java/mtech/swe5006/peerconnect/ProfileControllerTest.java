package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.ProfileController;
import mtech.swe5006.peerconnect.data.sql.Profile;
import mtech.swe5006.peerconnect.data.sql.ProfileRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.service.AuditService;
import mtech.swe5006.peerconnect.service.AzureBlobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    ProfileRepository profileRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    AzureBlobService azureBlobService;
    @Mock
    AuditService auditService;

    @InjectMocks
    ProfileController controller;

    private User alice;
    private Profile profile;

    @BeforeEach
    void setup() {
        alice = new User();
        alice.setId(UUID.randomUUID());
        alice.setEmail("alice@u.nus.edu");
        alice.setFirstName("Alice");
        alice.setLastName("Tan");
        alice.setPhone("91234567");
        alice.setNusStudentId("A0123456X");

        profile = new Profile();
        profile.setUserId(alice.getId());
        profile.setFaculty("Computing");
        profile.setMajor("CS");
        profile.setYearOfStudy((short) 2);
        profile.setBio("Hello");
        profile.setAvatarUrl("https://blob/avatar.png");
        profile.setFullTimeInd("Y");
    }

    private Authentication authFor(User u) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(u.getEmail());
        return auth;
    }

    // ──────────────────────────────────────────
    //  GET /api/profile
    // ──────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/profile")
    class GetProfile {

        @Test
        void userNotFound_returns404() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.getProfile(authFor(alice));

            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        void withProfile_returnsAllFields() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));

            ResponseEntity<?> res = controller.getProfile(authFor(alice));

            assertThat(res.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("userId", alice.getId().toString());
            assertThat(body).containsEntry("email", "alice@u.nus.edu");
            assertThat(body).containsEntry("faculty", "Computing");
            assertThat(body).containsEntry("major", "CS");
            assertThat(body).containsEntry("yearOfStudy", (short) 2);
            assertThat(body).containsEntry("bio", "Hello");
            assertThat(body).containsEntry("avatarUrl", "https://blob/avatar.png");
            assertThat(body).containsEntry("fullTime", true);
        }

        @Test
        void withoutProfile_returnsDefaults() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.getProfile(authFor(alice));

            assertThat(res.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("faculty", "");
            assertThat(body).containsEntry("major", "");
            assertThat(((Number) body.get("yearOfStudy")).intValue()).isZero();
            assertThat(body).containsEntry("bio", "");
            assertThat(body).containsEntry("avatarUrl", "");
            assertThat(body).containsEntry("fullTime", false);
        }

        @Test
        void fullTimeInd_N_returnsFalse() {
            profile.setFullTimeInd("N");
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));

            ResponseEntity<?> res = controller.getProfile(authFor(alice));

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("fullTime", false);
        }
    }

    // ──────────────────────────────────────────
    //  PUT /api/profile
    // ──────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/profile")
    class UpdateProfile {

        @Test
        void userNotFound_returns404() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.updateProfile(authFor(alice), new HashMap<>());

            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        void updatesUserFields() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> body = new HashMap<>();
            body.put("firstName", "Alicia");
            body.put("lastName", "Wong");
            body.put("phone", "98765432");

            controller.updateProfile(authFor(alice), body);

            ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCap.capture());
            assertThat(userCap.getValue().getFirstName()).isEqualTo("Alicia");
            assertThat(userCap.getValue().getLastName()).isEqualTo("Wong");
            assertThat(userCap.getValue().getPhone()).isEqualTo("98765432");
        }

        @Test
        void createsProfileWhenAbsent() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.empty());
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> body = new HashMap<>();
            body.put("faculty", "Engineering");

            controller.updateProfile(authFor(alice), body);

            ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
            verify(profileRepository).save(cap.capture());
            assertThat(cap.getValue().getUserId()).isEqualTo(alice.getId());
            assertThat(cap.getValue().getFaculty()).isEqualTo("Engineering");
        }

        @Test
        void yearOfStudy_parsedFromNumber() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> body = new HashMap<>();
            body.put("yearOfStudy", 3);  // Integer (Number subtype)

            controller.updateProfile(authFor(alice), body);

            ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
            verify(profileRepository).save(cap.capture());
            assertThat(cap.getValue().getYearOfStudy()).isEqualTo((short) 3);
        }

        @Test
        void fullTime_true_setsY() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> body = new HashMap<>();
            body.put("fullTime", true);

            controller.updateProfile(authFor(alice), body);

            ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
            verify(profileRepository).save(cap.capture());
            assertThat(cap.getValue().getFullTimeInd()).isEqualTo("Y");
        }

        @Test
        void fullTime_false_setsN() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> body = new HashMap<>();
            body.put("fullTime", false);

            controller.updateProfile(authFor(alice), body);

            ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
            verify(profileRepository).save(cap.capture());
            assertThat(cap.getValue().getFullTimeInd()).isEqualTo("N");
        }

        @Test
        void returnsSuccessMessage() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResponseEntity<?> res = controller.updateProfile(authFor(alice), new HashMap<>());

            assertThat(res.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsKey("message");
        }
    }

    // ──────────────────────────────────────────
    //  POST /api/profile/avatar
    // ──────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/profile/avatar")
    class UploadAvatar {

        private MultipartFile mockFile(boolean empty, String contentType, long size) {
            MultipartFile f = mock(MultipartFile.class);
            when(f.isEmpty()).thenReturn(empty);
            if (!empty) {
                lenient().when(f.getContentType()).thenReturn(contentType);
                lenient().when(f.getSize()).thenReturn(size);
            }
            return f;
        }

        @Test
        void emptyFile_returns400() {
            MultipartFile f = mockFile(true, null, 0);

            ResponseEntity<?> res = controller.uploadAvatar(authFor(alice), f);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void invalidContentType_returns400() {
            MultipartFile f = mockFile(false, "image/gif", 100);

            ResponseEntity<?> res = controller.uploadAvatar(authFor(alice), f);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void fileTooLarge_returns400() {
            long overLimit = 2L * 1024 * 1024 + 1;
            MultipartFile f = mockFile(false, "image/png", overLimit);

            ResponseEntity<?> res = controller.uploadAvatar(authFor(alice), f);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void userNotFound_returns404() {
            MultipartFile f = mockFile(false, "image/png", 100);
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.uploadAvatar(authFor(alice), f);

            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        void success_png_returnsUrlWithTimestamp() throws Exception {
            MultipartFile f = mockFile(false, "image/png", 100);
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));
            String blobUrl = "https://blob.azure.com/" + alice.getId() + ".png";
            when(azureBlobService.upload(eq(alice.getId() + ".png"), eq(f))).thenReturn(blobUrl);

            ResponseEntity<?> res = controller.uploadAvatar(authFor(alice), f);

            assertThat(res.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            String avatarUrl = (String) body.get("avatarUrl");
            assertThat(avatarUrl).startsWith(blobUrl + "?t=");
        }

        @Test
        void success_jpg_deletesOtherExtension() throws Exception {
            MultipartFile f = mockFile(false, "image/jpeg", 100);
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));
            String blobUrl = "https://blob.azure.com/" + alice.getId() + ".jpg";
            when(azureBlobService.upload(eq(alice.getId() + ".jpg"), eq(f))).thenReturn(blobUrl);

            controller.uploadAvatar(authFor(alice), f);

            // Should delete the opposite extension
            verify(azureBlobService).delete(alice.getId() + ".png");
        }

        @Test
        void success_createsProfileWhenAbsent() throws Exception {
            MultipartFile f = mockFile(false, "image/png", 100);
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.empty());
            when(azureBlobService.upload(any(), any())).thenReturn("https://blob.azure.com/avatar.png");

            controller.uploadAvatar(authFor(alice), f);

            ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
            verify(profileRepository).save(cap.capture());
            assertThat(cap.getValue().getUserId()).isEqualTo(alice.getId());
        }

        @Test
        void success_avatarUrlSavedToProfile() throws Exception {
            MultipartFile f = mockFile(false, "image/png", 100);
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));
            String blobUrl = "https://blob.azure.com/avatar.png";
            when(azureBlobService.upload(any(), any())).thenReturn(blobUrl);

            controller.uploadAvatar(authFor(alice), f);

            ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
            verify(profileRepository).save(cap.capture());
            assertThat(cap.getValue().getAvatarUrl()).startsWith(blobUrl + "?t=");
        }
    }

    // ──────────────────────────────────────────
    //  DELETE /api/profile/avatar
    // ──────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/profile/avatar")
    class DeleteAvatar {

        @Test
        void userNotFound_returns404() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.deleteAvatar(authFor(alice));

            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        void noProfile_returnsNoAvatarMessage() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.deleteAvatar(authFor(alice));

            assertThat(res.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body.get("message").toString()).contains("No avatar");
        }

        @Test
        void blankAvatarUrl_returnsNoAvatarMessage() {
            profile.setAvatarUrl("   ");
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));

            ResponseEntity<?> res = controller.deleteAvatar(authFor(alice));

            assertThat(res.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body.get("message").toString()).contains("No avatar");
        }

        @Test
        void success_deletesBothExtensions() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));

            ResponseEntity<?> res = controller.deleteAvatar(authFor(alice));

            assertThat(res.getStatusCode().value()).isEqualTo(200);
            verify(azureBlobService).delete(alice.getId() + ".png");
            verify(azureBlobService).delete(alice.getId() + ".jpg");
        }

        @Test
        void success_clearsAvatarUrlInDb() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(profileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));

            controller.deleteAvatar(authFor(alice));

            ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
            verify(profileRepository).save(cap.capture());
            assertThat(cap.getValue().getAvatarUrl()).isNull();
        }
    }
}
