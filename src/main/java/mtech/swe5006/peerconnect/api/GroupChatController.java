package mtech.swe5006.peerconnect.api;

import static mtech.swe5006.peerconnect.api.ControllerUtils.resolveUser;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.dto.GroupChatDtos;
import mtech.swe5006.peerconnect.service.chat.GroupChatFacade;

@RestController
@Validated
@RequestMapping("/api/groups/{groupId}/chat")
public class GroupChatController {

    private final UserRepository userRepository;
    private final GroupChatFacade groupChatFacade;

    public GroupChatController(UserRepository userRepository, GroupChatFacade groupChatFacade) {
        this.userRepository = userRepository;
        this.groupChatFacade = groupChatFacade;
    }

    @GetMapping
    public ResponseEntity<GroupChatDtos.GroupChatSummaryResponse> getChatSummary(@PathVariable UUID groupId, Authentication auth) {
        User actor = resolveUser(auth, userRepository);
        return ResponseEntity.ok(groupChatFacade.getChatSummary(groupId, actor));
    }

    @GetMapping("/messages")
    public ResponseEntity<GroupChatDtos.GroupChatMessagesResponse> getMessages(@PathVariable UUID groupId, Authentication auth) {
        User actor = resolveUser(auth, userRepository);
        return ResponseEntity.ok(groupChatFacade.getHistory(groupId, actor));
    }

    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(@PathVariable UUID groupId,
                                         Authentication auth,
                                         @Valid @RequestBody GroupChatDtos.SendGroupChatMessageRequest request) {
        User actor = resolveUser(auth, userRepository);
        GroupChatDtos.GroupChatMessageView message = groupChatFacade.sendMessage(groupId, actor, request.content());
        return ResponseEntity.ok(Map.of("message", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleKnownException(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "Request failed";
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpectedException(Exception ex) {
        return ResponseEntity.status(500).body(Map.of(
            "error", ex.getMessage() != null ? ex.getMessage() : "Internal server error"
        ));
    }
}
