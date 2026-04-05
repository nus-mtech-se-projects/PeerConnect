package mtech.swe5006.peerconnect.service.chat;

import java.util.List;
import java.util.UUID;

import mtech.swe5006.peerconnect.data.sql.GroupChatMessage;

public interface ChatHistoryRetrievalStrategy {
    String mode();
    List<GroupChatMessage> loadMessages(UUID groupId);
}
