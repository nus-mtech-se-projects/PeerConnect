package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final StudyGroupRepository groupRepository;
    private final UserRepository userRepository;

    public GroupController(StudyGroupRepository groupRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<StudyGroup>> getAllGroups() {
        List<StudyGroup> groups = groupRepository.findByStatusOrderByCreatedAtDesc("active");
        return ResponseEntity.ok(groups);
    }

    @PostMapping
    public ResponseEntity<?> createGroup(Authentication auth, @RequestBody Map<String, Object> body) {
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        StudyGroup group = new StudyGroup();
        group.setTopic((String) body.get("topic"));
        group.setStudyMode((String) body.getOrDefault("studyMode", "online"));
        group.setLocation((String) body.get("location"));
        group.setCreatedBy(user.getId());
        group.setStatus("active");

        if (body.containsKey("maxMembers")) {
            Object mm = body.get("maxMembers");
            if (mm instanceof Number) group.setMaxMembers(((Number) mm).shortValue());
        } else {
            group.setMaxMembers((short) 10);
        }

        if (body.containsKey("courseId") && body.get("courseId") != null) {
            try {
                group.setCourseId(java.util.UUID.fromString((String) body.get("courseId")));
            } catch (Exception ignored) { }
        }

        groupRepository.save(group);

        return ResponseEntity.ok(group);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getGroup(@PathVariable java.util.UUID id) {
        return groupRepository.findById(id)
            .map(g -> ResponseEntity.ok((Object) g))
            .orElse(ResponseEntity.status(404).body(Map.of("error", "Group not found")));
    }
}
