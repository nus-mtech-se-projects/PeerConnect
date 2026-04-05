package mtech.swe5006.peerconnect.service.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import mtech.swe5006.peerconnect.data.sql.GroupChatMessage;
import mtech.swe5006.peerconnect.data.sql.GroupChatMessageRepository;

@Component
public class PollingChatHistoryRetrievalStrategy implements ChatHistoryRetrievalStrategy {

    private final GroupChatMessageRepository groupChatMessageRepository;

    public PollingChatHistoryRetrievalStrategy(GroupChatMessageRepository groupChatMessageRepository) {
        this.groupChatMessageRepository = groupChatMessageRepository;
    }

    @Override
    public String mode() {
        return "polling";
    }

    @Override
    public List<GroupChatMessage> loadMessages(UUID groupId) {
        return groupChatMessageRepository.findByGroupIdOrderBySentAtAsc(groupId);
    }
}
