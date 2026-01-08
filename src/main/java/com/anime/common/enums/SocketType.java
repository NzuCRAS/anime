package com.anime.common.enums;

import lombok.Getter;

@Getter
public enum SocketType {
    ACCEPT_FRIEND_REQUEST,
    REJECT_FRIEND_REQUEST,
    NEW_FRIEND_REQUEST,
    PRIVATE_MESSAGES_READ,
    NEW_PRIVATE_MESSAGE,
    NEW_GROUP_MESSAGE,
    GROUP_MESSAGES_READ,
    USER_ONLINE,
    USER_OFFLINE
}
