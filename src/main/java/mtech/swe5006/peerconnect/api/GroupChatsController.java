package mtech.swe5006.peerconnect.api;

import static mtech.swe5006.peerconnect.api.ControllerUtils.resolveUser;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.data.sql.GroupChatAttachment;
import mtech.swe5006.peerconnect.dto.GroupChatDtos;
import mtech.swe5006.peerconnect.service.chat.GroupChatFacade;

@RestController
@Validated
@RequestMapping("/api/group-chats")
public class GroupChatsController {

    private final UserRepository userRepository;
    private final GroupChatFacade groupChatFacade;

    public GroupChatsController(UserRepository userRepository, GroupChatFacade groupChatFacade) {
        this.userRepository = userRepository;
        this.groupChatFacade = groupChatFacade;
    }

    @GetMapping
    public ResponseEntity<List<GroupChatDtos.GroupChatListItem>> listChats(Authentication auth) {
        User actor = resolveUser(auth, userRepository);
        return ResponseEntity.ok(groupChatFacade.listAccessibleChats(actor));
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<GroupChatDtos.GroupChatSummaryResponse> getChat(@PathVariable UUID chatId,
                                                                           Authentication auth) {
        User actor = resolveUser(auth, userRepository);
        return ResponseEntity.ok(groupChatFacade.getChatSummaryByChatId(chatId, actor));
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<GroupChatDtos.GroupChatMessagesResponse> getMessages(@PathVariable UUID chatId,
                                                                                Authentication auth) {
        User actor = resolveUser(auth, userRepository);
        return ResponseEntity.ok(groupChatFacade.getHistoryByChatId(chatId, actor));
    }

    @PostMapping("/{chatId}/messages")
    public ResponseEntity<?> sendMessage(@PathVariable UUID chatId,
                                         Authentication auth,
                                         @Valid @RequestBody GroupChatDtos.SendGroupChatMessageRequest request) {
        User actor = resolveUser(auth, userRepository);
        GroupChatDtos.GroupChatMessageView message = groupChatFacade.sendMessageByChatId(chatId, actor, request.content());
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping(path = "/{chatId}/messages/with-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sendMessageWithAttachment(@PathVariable UUID chatId,
                                                       Authentication auth,
                                                       @RequestParam(value = "content", required = false) String content,
                                                       @RequestParam("file") MultipartFile file) {
        User actor = resolveUser(auth, userRepository);
        GroupChatDtos.GroupChatMessageView message = groupChatFacade
            .sendMessageWithAttachmentByChatId(chatId, actor, content, file);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable UUID attachmentId, Authentication auth) {
        User actor = resolveUser(auth, userRepository);
        GroupChatAttachment attachment = groupChatFacade.getAttachmentForDownload(attachmentId, actor);

        HttpHeaders headers = new HttpHeaders();
        String contentType = attachment.getContentType() != null
            ? attachment.getContentType()
            : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(attachment.getFileSize());

        ContentDisposition disposition = ContentDisposition
            .builder(contentType.startsWith("image/") ? "inline" : "attachment")
            .filename(attachment.getOriginalFileName())
            .build();
        headers.setContentDisposition(disposition);

        return ResponseEntity.ok()
            .headers(headers)
            .body(attachment.getFileData());
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
