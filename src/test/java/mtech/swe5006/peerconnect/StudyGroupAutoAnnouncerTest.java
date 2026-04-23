package mtech.swe5006.peerconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudySession;
import mtech.swe5006.peerconnect.dto.AnnouncementResponse;
import mtech.swe5006.peerconnect.dto.CreateAnnouncementRequest;
import mtech.swe5006.peerconnect.service.AnnouncementService;
import mtech.swe5006.peerconnect.service.StudyGroupAutoAnnouncer;
import mtech.swe5006.peerconnect.service.StudyGroupAutoAnnouncer.GroupSnapshot;
import mtech.swe5006.peerconnect.service.StudyGroupAutoAnnouncer.SessionSummary;
import mtech.swe5006.peerconnect.service.StudyGroupAutoAnnouncer.UpdateSummary;

@ExtendWith(MockitoExtension.class)
class StudyGroupAutoAnnouncerTest {

    @Mock
    private AnnouncementService announcementService;

    @InjectMocks
    private StudyGroupAutoAnnouncer autoAnnouncer;

    // ─── Pure diff logic ────────────────────────────────────────────────────

    @Test
    void buildUpdateSummary_noChanges_returnsEmpty() {
        StudyGroup before = groupWith(g -> {
            g.setName("Algo Study Gang");
            g.setModuleCode("CS3230");
            g.setLocation("Library Room 3");
        });
        GroupSnapshot snapshot = GroupSnapshot.from(before);

        UpdateSummary summary = StudyGroupAutoAnnouncer.buildUpdateSummary(snapshot, before);

        assertThat(summary.isEmpty()).isTrue();
    }

    @Test
    void buildUpdateSummary_venueChange_buildsSingleLineBody() {
        StudyGroup before = groupWith(g -> {
            g.setName("Algo Study Gang");
            g.setModuleCode("CS3230");
            g.setLocation("Library Room 3");
        });
        GroupSnapshot snapshot = GroupSnapshot.from(before);

        StudyGroup after = groupWith(g -> {
            g.setName("Algo Study Gang");
            g.setModuleCode("CS3230");
            g.setLocation("Engineering Auditorium");
        });

        UpdateSummary summary = StudyGroupAutoAnnouncer.buildUpdateSummary(snapshot, after);

        assertThat(summary.isEmpty()).isFalse();
        assertThat(summary.title()).isEqualTo("Group details updated");
        assertThat(summary.content())
            .contains("Venue")
            .contains("Library Room 3")
            .contains("Engineering Auditorium");
        // Single-field change → single line
        assertThat(summary.content().split("\n")).hasSize(1);
    }

    @Test
    void buildUpdateSummary_multipleChanges_combinesIntoOneBody() {
        StudyGroup before = groupWith(g -> {
            g.setName("Algo Gang");
            g.setModuleCode("CS3230");
            g.setLocation("Library Rm 3");
            g.setPreferredSchedule(LocalDateTime.of(2026, 5, 1, 18, 0));
            g.setMaxMembers((short) 8);
        });
        GroupSnapshot snapshot = GroupSnapshot.from(before);

        StudyGroup after = groupWith(g -> {
            g.setName("Algo Gang 2026");
            g.setModuleCode("CS3230");
            g.setLocation("Engineering Auditorium");
            g.setPreferredSchedule(LocalDateTime.of(2026, 5, 2, 19, 0));
            g.setMaxMembers((short) 12);
        });

        UpdateSummary summary = StudyGroupAutoAnnouncer.buildUpdateSummary(snapshot, after);

        assertThat(summary.isEmpty()).isFalse();
        assertThat(summary.content())
            .contains("Name")
            .contains("Venue")
            .contains("Preferred Schedule")
            .contains("Max Members");
        assertThat(summary.content().split("\n")).hasSize(4);
    }

    @Test
    void buildUpdateSummary_trimsAndTreatsBlankAsNull() {
        StudyGroup before = groupWith(g -> g.setLocation("  Library Room 3  "));
        GroupSnapshot snapshot = GroupSnapshot.from(before);

        StudyGroup afterNoChange = groupWith(g -> g.setLocation("Library Room 3"));
        assertThat(StudyGroupAutoAnnouncer.buildUpdateSummary(snapshot, afterNoChange).isEmpty()).isTrue();

        StudyGroup afterBlanked = groupWith(g -> g.setLocation("   "));
        UpdateSummary summary = StudyGroupAutoAnnouncer.buildUpdateSummary(snapshot, afterBlanked);
        assertThat(summary.isEmpty()).isFalse();
        assertThat(summary.content()).contains("(empty)");
    }

    @Test
    void buildUpdateSummary_formatsScheduleHumanReadably() {
        StudyGroup before = groupWith(g -> g.setPreferredSchedule(LocalDateTime.of(2026, 5, 1, 18, 0)));
        GroupSnapshot snapshot = GroupSnapshot.from(before);

        StudyGroup after = groupWith(g -> g.setPreferredSchedule(LocalDateTime.of(2026, 5, 2, 19, 0)));
        UpdateSummary summary = StudyGroupAutoAnnouncer.buildUpdateSummary(snapshot, after);

        assertThat(summary.content()).contains("Fri, 01 May 2026 18:00");
        assertThat(summary.content()).contains("Sat, 02 May 2026 19:00");
    }

    // ─── Integration path: announce or stay silent ─────────────────────────

    @Test
    void maybePostUpdateAnnouncement_skipsWhenFeatureDisabled() {
        StudyGroup before = groupWith(g -> g.setLocation("A"));
        StudyGroup after = groupWith(g -> {
            g.setId(before.getId());
            g.setLocation("B");
            g.setAutoAnnounceEnabled(false); // disabled
        });

        autoAnnouncer.maybePostUpdateAnnouncement(GroupSnapshot.from(before), after, UUID.randomUUID());

        verify(announcementService, never()).createAnnouncement(any(), any(), any());
    }

    @Test
    void maybePostUpdateAnnouncement_skipsWhenNoTrackedFieldChanged() {
        StudyGroup before = groupWith(g -> g.setLocation("A"));
        StudyGroup after = groupWith(g -> {
            g.setId(before.getId());
            g.setLocation("A"); // same
            g.setAutoAnnounceEnabled(true);
        });

        autoAnnouncer.maybePostUpdateAnnouncement(GroupSnapshot.from(before), after, UUID.randomUUID());

        verify(announcementService, never()).createAnnouncement(any(), any(), any());
    }

    @Test
    void maybePostUpdateAnnouncement_postsWhenEnabledAndFieldChanged() {
        UUID editorId = UUID.randomUUID();
        StudyGroup before = groupWith(g -> g.setLocation("A"));
        StudyGroup after = groupWith(g -> {
            g.setId(before.getId());
            g.setLocation("B");
            g.setAutoAnnounceEnabled(true);
        });

        ArgumentCaptor<CreateAnnouncementRequest> reqCaptor = ArgumentCaptor.forClass(CreateAnnouncementRequest.class);
        when(announcementService.createAnnouncement(eq(after.getId()), eq(editorId), reqCaptor.capture()))
            .thenReturn(new AnnouncementResponse(
                UUID.randomUUID(), after.getId(), "x", "y", editorId,
                LocalDateTime.now(), "e@e.com", "Editor", after.getName(), after.getModuleCode()));

        autoAnnouncer.maybePostUpdateAnnouncement(GroupSnapshot.from(before), after, editorId);

        verify(announcementService, times(1)).createAnnouncement(eq(after.getId()), eq(editorId), any());
        assertThat(reqCaptor.getValue().title()).isEqualTo("Group details updated");
        assertThat(reqCaptor.getValue().content()).contains("Venue").contains("A").contains("B");
    }

    @Test
    void maybePostUpdateAnnouncement_swallowsServiceFailures() {
        StudyGroup before = groupWith(g -> g.setLocation("A"));
        StudyGroup after = groupWith(g -> {
            g.setId(before.getId());
            g.setLocation("B");
            g.setAutoAnnounceEnabled(true);
        });
        when(announcementService.createAnnouncement(any(), any(), any()))
            .thenThrow(new RuntimeException("announcement service down"));

        // Must not throw — auto-announce failure is never allowed to break the update path.
        autoAnnouncer.maybePostUpdateAnnouncement(GroupSnapshot.from(before), after, UUID.randomUUID());
    }

    // ─── Session-created summary path ──────────────────────────────────────

    @Test
    void buildSessionCreatedSummary_includesTitleScheduleAndDetails() {
        StudySession session = sessionWith(s -> {
            s.setTitle("Mock Paper Review");
            s.setStartsAt(LocalDateTime.of(2026, 5, 10, 18, 0));
            s.setEndsAt(LocalDateTime.of(2026, 5, 10, 20, 0));
            s.setLocation("Library Room 3");
            s.setNotes("Bring your laptops.");
        });

        SessionSummary summary = StudyGroupAutoAnnouncer.buildSessionCreatedSummary(session);

        assertThat(summary.isEmpty()).isFalse();
        assertThat(summary.title()).contains("Mock Paper Review");
        assertThat(summary.content())
            .contains("When:")
            .contains("Sun, 10 May 2026 18:00")
            .contains("Sun, 10 May 2026 20:00")
            .contains("Location: Library Room 3")
            .contains("Notes: Bring your laptops.");
    }

    @Test
    void maybePostSessionCreated_skipsWhenDisabled() {
        StudyGroup group = groupWith(g -> g.setName("Algo"));
        StudySession session = sessionWith(s -> {
            s.setTitle("Session A");
            s.setStartsAt(LocalDateTime.of(2026, 5, 10, 18, 0));
        });

        autoAnnouncer.maybePostSessionCreated(group, session, UUID.randomUUID(), false);

        verify(announcementService, never()).createAnnouncement(any(), any(), any());
    }

    @Test
    void maybePostSessionCreated_postsWhenEnabled() {
        UUID editorId = UUID.randomUUID();
        StudyGroup group = groupWith(g -> g.setName("Algo"));
        StudySession session = sessionWith(s -> {
            s.setTitle("Session A");
            s.setStartsAt(LocalDateTime.of(2026, 5, 10, 18, 0));
            s.setLocation("Library Room 3");
        });
        ArgumentCaptor<CreateAnnouncementRequest> reqCaptor = ArgumentCaptor.forClass(CreateAnnouncementRequest.class);
        when(announcementService.createAnnouncement(eq(group.getId()), eq(editorId), reqCaptor.capture()))
            .thenReturn(new AnnouncementResponse(
                UUID.randomUUID(), group.getId(), "x", "y", editorId,
                LocalDateTime.now(), "e@e.com", "Editor", group.getName(), group.getModuleCode()));

        autoAnnouncer.maybePostSessionCreated(group, session, editorId, true);

        verify(announcementService, times(1)).createAnnouncement(eq(group.getId()), eq(editorId), any());
        assertThat(reqCaptor.getValue().title()).contains("Session A");
        assertThat(reqCaptor.getValue().content()).contains("Library Room 3");
    }

    @Test
    void maybePostSessionCreated_swallowsServiceFailures() {
        StudyGroup group = groupWith(g -> g.setName("Algo"));
        StudySession session = sessionWith(s -> {
            s.setTitle("Session A");
            s.setStartsAt(LocalDateTime.of(2026, 5, 10, 18, 0));
        });
        when(announcementService.createAnnouncement(any(), any(), any()))
            .thenThrow(new RuntimeException("announcement service down"));

        // Must not throw — auto-announce failure must never break the session save path.
        autoAnnouncer.maybePostSessionCreated(group, session, UUID.randomUUID(), true);
    }

    // ─── Fixture helpers ───────────────────────────────────────────────────

    private static StudyGroup groupWith(java.util.function.Consumer<StudyGroup> customizer) {
        StudyGroup g = new StudyGroup();
        g.setId(UUID.randomUUID());
        customizer.accept(g);
        return g;
    }

    private static StudySession sessionWith(java.util.function.Consumer<StudySession> customizer) {
        StudySession s = new StudySession();
        s.setId(UUID.randomUUID());
        s.setGroupId(UUID.randomUUID());
        customizer.accept(s);
        return s;
    }
}
