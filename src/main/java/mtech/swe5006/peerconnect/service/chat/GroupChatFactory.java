package mtech.swe5006.peerconnect.service.chat;

import java.util.UUID;

import org.springframework.stereotype.Component;

import mtech.swe5006.peerconnect.data.sql.GroupChat;

@Component
public class GroupChatFactory {

    public GroupChat createForGroup(UUID groupId) {
        GroupChat chat = new GroupChat();
        chat.setGroupId(groupId);
        return chat;
    }
}
