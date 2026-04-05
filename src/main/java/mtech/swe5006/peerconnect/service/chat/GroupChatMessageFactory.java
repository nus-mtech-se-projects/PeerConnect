package mtech.swe5006.peerconnect.service.chat;

import org.springframework.stereotype.Component;

import mtech.swe5006.peerconnect.data.sql.GroupChat;
import mtech.swe5006.peerconnect.data.sql.GroupChatMessage;
import mtech.swe5006.peerconnect.data.sql.User;

@Component
public class GroupChatMessageFactory {

    public GroupChatMessage create(GroupChat chat, User sender, String content) {
        GroupChatMessage message = new GroupChatMessage();
        message.setChatId(chat.getId());
        message.setGroupId(chat.getGroupId());
        message.setSenderId(sender.getId());
        message.setContent(content);
        return message;
    }
}
