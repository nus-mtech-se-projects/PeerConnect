package mtech.swe5006.peerconnect.service.chat;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import mtech.swe5006.peerconnect.data.sql.GroupChatAttachment;
import mtech.swe5006.peerconnect.data.sql.GroupChatAttachmentRepository;
import mtech.swe5006.peerconnect.data.sql.GroupChat;
import mtech.swe5006.peerconnect.data.sql.GroupChatMessage;
import mtech.swe5006.peerconnect.data.sql.GroupChatMessageRepository;
import mtech.swe5006.peerconnect.data.sql.GroupChatRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMember;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMemberRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.dto.GroupChatDtos;

@Service
public class GroupChatFacade {

    private static final String MEMBERSHIP_APPROVED = "approved";
    private static final long MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/jpg",
        "image/gif",
        "image/webp",
        "application/pdf",
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private final GroupChatRepository groupChatRepository;
    private final GroupChatMessageRepository groupChatMessageRepository;
    private final GroupChatAttachmentRepository groupChatAttachmentRepository;
    private final StudyGroupRepository studyGroupRepository;
    private final StudyGroupMemberRepository studyGroupMemberRepository;
    private final UserRepository userRepository;
    private final GroupChatFactory groupChatFactory;
    private final GroupChatMessageFactory groupChatMessageFactory;
    private final GroupChatViewFactory groupChatViewFactory;
    private final ChatHistoryRetrievalStrategy chatHistoryRetrievalStrategy;
    private final GroupChatMessageDispatchStrategy messageDispatchStrategy;

    public GroupChatFacade(GroupChatRepository groupChatRepository,
                           GroupChatMessageRepository groupChatMessageRepository,
                           GroupChatAttachmentRepository groupChatAttachmentRepository,
                           StudyGroupRepository studyGroupRepository,
                           StudyGroupMemberRepository studyGroupMemberRepository,
                           UserRepository userRepository,
                           GroupChatFactory groupChatFactory,
                           GroupChatMessageFactory groupChatMessageFactory,
                           GroupChatViewFactory groupChatViewFactory,
                           ChatHistoryRetrievalStrategy chatHistoryRetrievalStrategy,
                           GroupChatMessageDispatchStrategy messageDispatchStrategy) {
        this.groupChatRepository = groupChatRepository;
        this.groupChatMessageRepository = groupChatMessageRepository;
        this.groupChatAttachmentRepository = groupChatAttachmentRepository;
        this.studyGroupRepository = studyGroupRepository;
        this.studyGroupMemberRepository = studyGroupMemberRepository;
        this.userRepository = userRepository;
        this.groupChatFactory = groupChatFactory;
        this.groupChatMessageFactory = groupChatMessageFactory;
        this.groupChatViewFactory = groupChatViewFactory;
        this.chatHistoryRetrievalStrategy = chatHistoryRetrievalStrategy;
        this.messageDispatchStrategy = messageDispatchStrategy;
    }

    // Facade operation used from group creation flow and also as a safe idempotent fallback.
    @Transactional
    public GroupChat initializeChatForGroup(StudyGroup group) {
        if (group == null || group.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group id is required");
        }
        return groupChatRepository.findByGroupId(group.getId())
            .orElseGet(() -> groupChatRepository.save(groupChatFactory.createForGroup(group.getId())));
    }

    @Transactional
    public GroupChatDtos.GroupChatSummaryResponse getChatSummary(UUID groupId, User actor) {
        StudyGroup group = requireGroup(groupId);
        requireMember(group, actor);
        GroupChat chat = initializeChatForGroup(group);
        long messageCount = groupChatMessageRepository.countByGroupId(groupId);
        return groupChatViewFactory.buildSummary(chat, group, messageCount, chatHistoryRetrievalStrategy.mode());
    }

    @Transactional
    public GroupChatDtos.GroupChatMessagesResponse getHistory(UUID groupId, User actor) {
        StudyGroup group = requireGroup(groupId);
        requireMember(group, actor);
        GroupChat chat = initializeChatForGroup(group);

        List<GroupChatDtos.GroupChatMessageView> views = chatHistoryRetrievalStrategy.loadMessages(groupId).stream()
            .collect(Collectors.collectingAndThen(Collectors.toList(), this::toViewsWithAttachments));

        GroupChatDtos.GroupChatSummaryResponse summary = groupChatViewFactory.buildSummary(
            chat,
            group,
            views.size(),
            chatHistoryRetrievalStrategy.mode()
        );

        return new GroupChatDtos.GroupChatMessagesResponse(summary, views);
    }

    @Transactional
    public GroupChatDtos.GroupChatMessageView sendMessage(UUID groupId, User actor, String content) {
        StudyGroup group = requireGroup(groupId);
        requireMember(group, actor);
        GroupChat chat = initializeChatForGroup(group);

        String normalizedContent = normalizeContent(content, false);
        GroupChatMessage message = groupChatMessageFactory.create(chat, actor, normalizedContent);
        GroupChatMessage saved = groupChatMessageRepository.save(message);

        messageDispatchStrategy.dispatch(saved, actor);
        return groupChatViewFactory.buildMessage(saved, actor, null);
    }

    @Transactional
    public List<GroupChatDtos.GroupChatListItem> listAccessibleChats(User actor) {
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        List<UUID> memberGroupIds = studyGroupMemberRepository
            .findByUserIdAndMembershipStatus(actor.getId(), MEMBERSHIP_APPROVED)
            .stream()
            .map(StudyGroupMember::getGroupId)
            .toList();

        List<UUID> ownerGroupIds = studyGroupRepository.findByCreatedBy(actor.getId())
            .stream()
            .map(StudyGroup::getId)
            .toList();

        LinkedHashMap<UUID, UUID> accessibleGroupIds = new LinkedHashMap<>();
        memberGroupIds.forEach(id -> accessibleGroupIds.put(id, id));
        ownerGroupIds.forEach(id -> accessibleGroupIds.put(id, id));

        List<StudyGroup> groups = studyGroupRepository.findAllById(accessibleGroupIds.keySet());
        return groups.stream()
            .map(group -> {
                GroupChat chat = initializeChatForGroup(group);
                long messageCount = groupChatMessageRepository.countByGroupId(group.getId());
                return groupChatViewFactory.buildListItem(chat, group, messageCount, chatHistoryRetrievalStrategy.mode());
            })
            .sorted((a, b) -> a.groupName().compareToIgnoreCase(b.groupName()))
            .collect(Collectors.toList());
    }

    @Transactional
    public GroupChatDtos.GroupChatSummaryResponse getChatSummaryByChatId(UUID chatId, User actor) {
        GroupChat chat = requireChat(chatId);
        StudyGroup group = requireGroup(chat.getGroupId());
        requireMember(group, actor);

        long messageCount = groupChatMessageRepository.countByGroupId(group.getId());
        return groupChatViewFactory.buildSummary(chat, group, messageCount, chatHistoryRetrievalStrategy.mode());
    }

    @Transactional
    public GroupChatDtos.GroupChatMessagesResponse getHistoryByChatId(UUID chatId, User actor) {
        GroupChat chat = requireChat(chatId);
        StudyGroup group = requireGroup(chat.getGroupId());
        requireMember(group, actor);

        List<GroupChatDtos.GroupChatMessageView> views = chatHistoryRetrievalStrategy
            .loadMessages(group.getId())
            .stream()
            .collect(Collectors.collectingAndThen(Collectors.toList(), this::toViewsWithAttachments));

        GroupChatDtos.GroupChatSummaryResponse summary = groupChatViewFactory.buildSummary(
            chat,
            group,
            views.size(),
            chatHistoryRetrievalStrategy.mode()
        );

        return new GroupChatDtos.GroupChatMessagesResponse(summary, views);
    }

    @Transactional
    public GroupChatDtos.GroupChatMessageView sendMessageByChatId(UUID chatId, User actor, String content) {
        GroupChat chat = requireChat(chatId);
        StudyGroup group = requireGroup(chat.getGroupId());
        requireMember(group, actor);

        String normalizedContent = normalizeContent(content, false);
        GroupChatMessage message = groupChatMessageFactory.create(chat, actor, normalizedContent);
        GroupChatMessage saved = groupChatMessageRepository.save(message);

        messageDispatchStrategy.dispatch(saved, actor);
        return groupChatViewFactory.buildMessage(saved, actor, null);
    }

    @Transactional
    public GroupChatDtos.GroupChatMessageView sendMessageWithAttachmentByChatId(UUID chatId,
                                                                                 User actor,
                                                                                 String content,
                                                                                 MultipartFile file) {
        GroupChat chat = requireChat(chatId);
        StudyGroup group = requireGroup(chat.getGroupId());
        requireMember(group, actor);

        validateAttachment(file);
        String normalizedContent = normalizeContent(content, true);

        GroupChatMessage message = groupChatMessageFactory.create(chat, actor, normalizedContent);
        GroupChatMessage savedMessage = groupChatMessageRepository.save(message);

        GroupChatAttachment attachment = new GroupChatAttachment();
        attachment.setMessageId(savedMessage.getId());
        attachment.setChatId(chat.getId());
        attachment.setGroupId(group.getId());
        attachment.setOriginalFileName(safeFileName(file.getOriginalFilename()));
        attachment.setContentType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setStoragePath("db://group-chat-attachments/" + savedMessage.getId());

        try {
            attachment.setFileData(file.getBytes());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read attachment");
        }

        GroupChatAttachment savedAttachment = groupChatAttachmentRepository.save(attachment);
        messageDispatchStrategy.dispatch(savedMessage, actor);
        return groupChatViewFactory.buildMessage(savedMessage, actor, savedAttachment);
    }

    @Transactional(readOnly = true)
    public GroupChatAttachment getAttachmentForDownload(UUID attachmentId, User actor) {
        GroupChatAttachment attachment = groupChatAttachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

        StudyGroup group = requireGroup(attachment.getGroupId());
        requireMember(group, actor);
        return attachment;
    }

    private GroupChatDtos.GroupChatMessageView toView(GroupChatMessage message) {
        return toView(message, null);
    }

    private GroupChatDtos.GroupChatMessageView toView(GroupChatMessage message, GroupChatAttachment attachment) {
        User sender = userRepository.findById(message.getSenderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message sender not found"));
        return groupChatViewFactory.buildMessage(message, sender, attachment);
    }

    private List<GroupChatDtos.GroupChatMessageView> toViewsWithAttachments(List<GroupChatMessage> messages) {
        List<UUID> messageIds = messages.stream().map(GroupChatMessage::getId).toList();
        Map<UUID, GroupChatAttachment> attachmentByMessageId = new HashMap<>();
        groupChatAttachmentRepository.findByMessageIdIn(messageIds)
            .forEach(attachment -> attachmentByMessageId.put(attachment.getMessageId(), attachment));

        return messages.stream()
            .map(message -> toView(message, attachmentByMessageId.get(message.getId())))
            .toList();
    }

    private String normalizeContent(String content, boolean attachmentAllowedWhenBlank) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank() && !attachmentAllowedWhenBlank) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content must not be blank");
        }
        if (normalized.isBlank()) {
            normalized = "[Attachment]";
        }
        if (normalized.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content must not exceed 2000 characters");
        }
        return normalized;
    }

    private void validateAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment file is required");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment exceeds 5MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_ATTACHMENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported attachment type");
        }
    }

    private String safeFileName(String fileName) {
        String fallback = "attachment";
        if (fileName == null || fileName.isBlank()) {
            return fallback;
        }
        return fileName.replaceAll("[\\r\\n]", "_");
    }

    private StudyGroup requireGroup(UUID groupId) {
        return studyGroupRepository.findById(groupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
    }

    private GroupChat requireChat(UUID chatId) {
        return groupChatRepository.findById(chatId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
    }

    private void requireMember(StudyGroup group, User actor) {
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        boolean isOwner = group.getCreatedBy() != null && group.getCreatedBy().equals(actor.getId());
        if (isOwner) return;

        StudyGroupMember membership = studyGroupMemberRepository
            .findByGroupIdAndUserId(group.getId(), actor.getId())
            .orElse(null);

        if (membership == null || !MEMBERSHIP_APPROVED.equalsIgnoreCase(membership.getMembershipStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this group chat");
        }
    }
}
