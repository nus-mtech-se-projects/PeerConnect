package mtech.swe5006.peerconnect.service.chat;

import mtech.swe5006.peerconnect.data.sql.GroupChatMessage;
import mtech.swe5006.peerconnect.data.sql.User;

public interface GroupChatMessageDispatchStrategy {
    void dispatch(GroupChatMessage message, User sender);
}
