package com.anime.chat.service;

import com.anime.chat.socket.WsEventPublisher;
import com.anime.common.dto.chat.friend.*;
import com.anime.common.dto.chat.session.SessionItem;
import com.anime.common.entity.chat.UserFriend;
import com.anime.common.entity.chat.UserFriendRequest;
import com.anime.common.entity.user.User;
import com.anime.common.enums.SocketType;
import com.anime.common.enums.FriendStatus;
import com.anime.common.mapper.chat.UserFriendMapper;
import com.anime.common.mapper.chat.UserFriendRequestMapper;
import com.anime.common.mapper.user.UserMapper;
import com.anime.common.service.AttachmentService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 好友相关业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService extends ServiceImpl<UserFriendMapper, UserFriend> {

    private final UserMapper userMapper;
    private final AttachmentService attachmentService;
    private final UserFriendRequestMapper userFriendRequestMapper;
    private final WsEventPublisher wsEventPublisher;

    public ListFriendsResponse listFriends(ListFriendsRequest request, Long currentUserId) {
        // 1. 查询好友关系
        List<UserFriend> links = this.list(Wrappers.<UserFriend>lambdaQuery()
                .eq(UserFriend::getUserId, currentUserId));

        ListFriendsResponse resp = new ListFriendsResponse();
        if (links.isEmpty()) {
            resp.setFriends(List.of());
            return resp;
        }

        // 2. 查出好友用户信息
        List<Long> friendIds = links.stream()
                .map(UserFriend::getFriendId)
                .distinct()
                .collect(Collectors.toList());
        List<User> friends = userMapper.selectBatchIds(friendIds);

        // 3. 转为 DTO
        List<FriendItem> items = friends.stream().map(u -> {
            FriendItem item = new FriendItem();
            item.setId(u.getId());
            item.setUsername(u.getUsername());
            item.setPersonalSignature(u.getPersonalSignature());
            Long avatarAttId = userMapper.getAvatarAttachmentIdById(u.getId());
            if (avatarAttId != null) {
                item.setAvatarUrl(attachmentService.generatePresignedGetUrl(avatarAttId, 3600));
            }
            return item;
        }).collect(Collectors.toList());
        resp.setFriends(items);
        return resp;
    }

    /**
     * 发送好友请求
     */
    @Transactional
    public SendFriendRequestResponse sendFriendRequest(SendFriendRequestRequest req, Long currentUserId) {
        Long toUserId = req.getToUserId();
        String message = req.getMessage();

        if (toUserId == null) throw new IllegalArgumentException("toUserId required");
        if (currentUserId.equals(toUserId)) throw new IllegalArgumentException("cannot add yourself");

        User toUser = userMapper.selectById(toUserId);
        if (toUser == null) throw new IllegalArgumentException("target user not found");

        // 1) check already friends (either direction)
        long countDirect = this.count(Wrappers.<UserFriend>lambdaQuery()
                .eq(UserFriend::getUserId, currentUserId)
                .eq(UserFriend::getFriendId, toUserId));
        long countReverse = this.count(Wrappers.<UserFriend>lambdaQuery()
                .eq(UserFriend::getUserId, toUserId)
                .eq(UserFriend::getFriendId, currentUserId));
        if (countDirect > 0 || countReverse > 0) {
            SendFriendRequestResponse r = new SendFriendRequestResponse();
            r.setRequestId(null);
            r.setStatus("already_friends");
            return r;
        }

        // 2) Check if there already exists an ACTIVE request between A and B (either direction) with status != 'rejected'
        UserFriendRequest active = userFriendRequestMapper.findActiveBetween(currentUserId, toUserId);
        if (active != null) {
            SendFriendRequestResponse r = new SendFriendRequestResponse();
            r.setRequestId(active.getId());
            r.setStatus("already_pending_or_handled");
            return r;
        }

        // create request
        UserFriendRequest fr = new UserFriendRequest();
        fr.setFromUserId(currentUserId);
        fr.setToUserId(toUserId);
        fr.setMessage(message);
        fr.setStatus("pending");
        userFriendRequestMapper.insert(fr);

        // notify the target user after commit
        final Long reqId = fr.getId();
        final Long fromUserId = currentUserId;
        final Long targetId = toUserId;
        final String msg = message == null ? "" : message;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    var payload = Map.of(
                            "requestId", reqId,
                            "fromUserId", fromUserId,
                            "message", msg
                    );
                    wsEventPublisher.sendToUser(targetId, SocketType.NEW_FRIEND_REQUEST.toString(), payload);
                } catch (Exception e) {
                    log.error("notify NEW_FRIEND_REQUEST failed for targetId={} reqId={} err={}", targetId, reqId, e.getMessage());
                }
            }
        });

        SendFriendRequestResponse r = new SendFriendRequestResponse();
        r.setRequestId(fr.getId());
        r.setStatus("pending");
        return r;
    }

    /**
     * 列出当前用户收到的好友请求（pending）
     *
     * 新增：把发起人的个性签名（signature）包含在返回项中
     */
    public ListFriendRequestsResponse listIncomingRequests(Long currentUserId) {
        List<UserFriendRequest> list = userFriendRequestMapper.listPendingForUser(currentUserId);
        List<FriendRequestItem> items = list.stream().map(fr -> {
            FriendRequestItem it = new FriendRequestItem();
            it.setRequestId(fr.getId());
            it.setFromUserId(fr.getFromUserId());
            User fromUser = userMapper.selectById(fr.getFromUserId());
            if (fromUser != null) {
                it.setFromUsername(fromUser.getUsername());
                Long avatar = userMapper.getAvatarAttachmentIdById(fromUser.getId());
                if (avatar != null) {
                    it.setFromAvatarUrl(attachmentService.generatePresignedGetUrl(avatar, 300));
                }
                it.setMessage(fr.getMessage());
                it.setCreatedAt(fr.getCreatedAt());
                // 新增：设置发起人的个性签名（signature）
                String signature = userMapper.getPersonalSignatureById(fromUser.getId());
                it.setSignature(signature);
            }
            return it;
        }).collect(Collectors.toList());

        ListFriendRequestsResponse resp = new ListFriendRequestsResponse();
        resp.setItems(items);
        return resp;
    }

    /**
     * 处理好友请求（accept/reject）
     *
     * 规则：仅允许处理 status == 'pending' 的请求。否则抛 IllegalArgumentException("request already handled")
     */
    @Transactional
    public void handleFriendRequest(HandleFriendRequestRequest req, Long currentUserId) {
        if (req == null || req.getRequestId() == null) throw new IllegalArgumentException("requestId required");
        UserFriendRequest fr = userFriendRequestMapper.findById(req.getRequestId());
        if (fr == null) throw new IllegalArgumentException("request not found");
        if (!fr.getToUserId().equals(currentUserId)) throw new IllegalArgumentException("not authorized to handle this request");

        // Only allow handling when current status is 'pending'
        if (!"pending".equalsIgnoreCase(fr.getStatus())) {
            throw new IllegalArgumentException("request already handled");
        }

        String action = req.getAction();
        if (action == null) throw new IllegalArgumentException("action required");

        final long fromUserId = fr.getFromUserId();
        final long toUserId = fr.getToUserId();
        final long requestId = fr.getId();

        String newStatus;
        if ("accept".equalsIgnoreCase(action)) newStatus = "accepted";
        else if ("reject".equalsIgnoreCase(action)) newStatus = "rejected";
        else throw new IllegalArgumentException("unknown action");

        // Atomic update: only change if current status is 'pending'
        int updated = userFriendRequestMapper.updateStatusIfCurrent(requestId, newStatus, "pending");
        if (updated <= 0) {
            // someone else modified concurrently or status not pending
            throw new IllegalArgumentException("request already handled");
        }

        // If accepted, create friendship (双向) — idempotent check
        if ("accepted".equalsIgnoreCase(newStatus)) {
            long c1 = this.count(Wrappers.<UserFriend>lambdaQuery()
                    .eq(UserFriend::getUserId, toUserId)
                    .eq(UserFriend::getFriendId, fromUserId));
            long c2 = this.count(Wrappers.<UserFriend>lambdaQuery()
                    .eq(UserFriend::getUserId, fromUserId)
                    .eq(UserFriend::getFriendId, toUserId));
            if (c1 == 0 && c2 == 0) {
                UserFriend uf1 = new UserFriend();
                uf1.setUserId(toUserId);
                uf1.setFriendId(fromUserId);
                this.save(uf1);

                UserFriend uf2 = new UserFriend();
                uf2.setUserId(fromUserId);
                uf2.setFriendId(toUserId);
                this.save(uf2);
            }
        }

        // Register WS notifications to be sent AFTER transaction commit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // notify requester about the response
                    var respPayload = java.util.Map.of(
                            "requestId", requestId,
                            "status", newStatus,
                            "fromUserId", fromUserId,
                            "toUserId", toUserId
                    );
                } catch (Exception e) {
                    log.warn("notify requester failed for requestId={}, err={}", requestId, e.getMessage());
                }
                if ("accepted".equalsIgnoreCase(newStatus)) {
                    try {
                        // 给发起好友申请的用户发 FRIEND_LIST_UPDATED 事件
                        wsEventPublisher.sendToUser(fromUserId, SocketType.ACCEPT_FRIEND_REQUEST.toString(), Map.of("userId", fromUserId));
                    } catch (Exception e) {
                        log.warn("notify FRIEND_LIST_UPDATED failed for requestId={}, err={}", requestId, e.getMessage());
                    }
                } else {
                    wsEventPublisher.sendToUser(fromUserId, SocketType.REJECT_FRIEND_REQUEST.toString(), Map.of("userId", fromUserId));
                }
            }
        });
    }

    /**
     * 精确id搜索（严格匹配） - now includes relationship status relative to currentUserId
     */
    public SearchUserResponse searchById(SearchUserRequest req, Long currentUserId) {
        if (req == null || req.getUserId() == null || req.getUserId() <= 0) {
            SearchUserResponse r = new SearchUserResponse();
            r.setItems(List.of());
            return r;
        }
        User u = userMapper.findById(req.getUserId());
        SearchUserResponse resp = new SearchUserResponse();
        if (u == null) {
            resp.setItems(List.of());
            return resp;
        }
        FriendSearchItem it = new FriendSearchItem();
        it.setId(u.getId());
        it.setUsername(u.getUsername());
        Long avatarId = userMapper.getAvatarAttachmentIdById(u.getId());
        if (avatarId != null) {
            it.setAvatarUrl(attachmentService.generatePresignedGetUrl(avatarId, 300));
        }
        it.setPersonalSignature(userMapper.getPersonalSignatureById(u.getId()));

        // Determine relation status relative to currentUserId
        FriendStatus status = FriendStatus.NOT_FRIENDS;
        if (currentUserId != null) {
            // 1) check friendship (either direction should be present as we create two rows on accept)
            long count = this.count(Wrappers.<UserFriend>lambdaQuery()
                    .eq(UserFriend::getUserId, currentUserId)
                    .eq(UserFriend::getFriendId, u.getId()));
            if (count > 0) {
                status = FriendStatus.ALREADY_FRIENDS;
            } else {
                // 2) check pending friend request in either direction
                UserFriendRequest sent = userFriendRequestMapper.findByFromTo(currentUserId, u.getId());
                if (sent != null && "pending".equalsIgnoreCase(sent.getStatus())) {
                    status = FriendStatus.PENDING_REQUEST;
                } else {
                    UserFriendRequest received = userFriendRequestMapper.findByFromTo(u.getId(), currentUserId);
                    if (received != null && "pending".equalsIgnoreCase(received.getStatus())) {
                        status = FriendStatus.PENDING_REQUEST;
                    } else {
                        status = FriendStatus.NOT_FRIENDS;
                    }
                }
            }
        } else {
            status = FriendStatus.NOT_FRIENDS;
        }
        it.setStatus(status);

        resp.setItems(List.of(it));
        return resp;
    }

    @Transactional
    public boolean removeFriend(RemoveFriendRequest request, Long currentUserId) {
        Long friendId = request.getFriendId();
        if (friendId == null) {
            throw new IllegalArgumentException("friendId 不能为空");
        }

        // 删除 currentUser -> friend
        this.remove(Wrappers.<UserFriend>lambdaQuery()
                .eq(UserFriend::getUserId, currentUserId)
                .eq(UserFriend::getFriendId, friendId));

        // 删除 friend -> currentUser
        this.remove(Wrappers.<UserFriend>lambdaQuery()
                .eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, currentUserId));

        return true;
    }
}