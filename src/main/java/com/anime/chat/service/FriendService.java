package com.anime.chat.service;

import com.anime.chat.socket.WsEventPublisher;
import com.anime.common.dto.chat.friend.*;
import com.anime.common.dto.chat.session.SessionItem;
import com.anime.common.entity.chat.UserFriend;
import com.anime.common.entity.chat.UserFriendRequest;
import com.anime.common.entity.user.User;
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

import java.util.List;
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

    @Transactional
    public AddFriendResponse addFriend(AddFriendRequest request, Long currentUserId) {
        Long friendUid = request.getFriendUid();
        if (friendUid == null) {
            throw new IllegalArgumentException("friendUuid 不能为空");
        }
        if (currentUserId.equals(friendUid)) {
            throw new IllegalArgumentException("不能添加自己为好友");
        }

        // 1. 通过 UUID（这里等价 userId）查找对方用户
        User friend = userMapper.selectById(friendUid);
        if (friend == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }
        Long friendId = friend.getId();

        // 2. 检查是否已是好友
        long count = this.count(Wrappers.<UserFriend>lambdaQuery()
                .eq(UserFriend::getUserId, currentUserId)
                .eq(UserFriend::getFriendId, friendId));
        if (count == 0) {
            // 插入双向关系
            UserFriend uf1 = new UserFriend();
            uf1.setUserId(currentUserId);
            uf1.setFriendId(friendId);
            this.save(uf1);

            UserFriend uf2 = new UserFriend();
            uf2.setUserId(friendId);
            uf2.setFriendId(currentUserId);
            this.save(uf2);
        }

        // 3. 构造响应 DTO
        AddFriendResponse resp = new AddFriendResponse();
        resp.setId(friend.getId());
        resp.setUsername(friend.getUsername());
        resp.setEmail(friend.getEmail());

        Long avatarAttId = userMapper.getAvatarAttachmentIdById(friend.getId());
        if (avatarAttId != null) {
            String url = attachmentService.generatePresignedGetUrl(avatarAttId, 3600);
            resp.setAvatarUrl(url);
        }
        return resp;
    }

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

        // check already friends
        long count = this.count(Wrappers.<UserFriend>lambdaQuery()
                .eq(UserFriend::getUserId, currentUserId)
                .eq(UserFriend::getFriendId, toUserId));
        if (count > 0) {
            SendFriendRequestResponse r = new SendFriendRequestResponse();
            r.setRequestId(null);
            r.setStatus("already_friends");
            return r;
        }

        // check existing pending request from currentUser -> toUser
        UserFriendRequest existing = userFriendRequestMapper.selectOne(
                Wrappers.<UserFriendRequest>lambdaQuery()
                        .eq(UserFriendRequest::getFromUserId, currentUserId)
                        .eq(UserFriendRequest::getToUserId, toUserId)
                        .eq(UserFriendRequest::getStatus, "pending")
        );
        if (existing != null) {
            SendFriendRequestResponse r = new SendFriendRequestResponse();
            r.setRequestId(existing.getId());
            r.setStatus("already pending");
            return r;
        }

        // create request
        UserFriendRequest fr = new UserFriendRequest();
        fr.setFromUserId(currentUserId);
        fr.setToUserId(toUserId);
        fr.setMessage(message);
        fr.setStatus("pending");
        userFriendRequestMapper.insert(fr);

        notifyNewFriendRequest(currentUserId, req);

        SendFriendRequestResponse r = new SendFriendRequestResponse();
        r.setRequestId(fr.getId());
        r.setStatus("pending");
        return r;
    }

    /**
     * 列出当前用户收到的好友请求（pending）
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
            }
            return it;
        }).collect(Collectors.toList());

        ListFriendRequestsResponse resp = new ListFriendRequestsResponse();
        resp.setItems(items);
        return resp;
    }

    /**
     * 处理好友请求（accept/reject）
     */
    @Transactional
    public void handleFriendRequest(HandleFriendRequestRequest req, Long currentUserId) {
        if (req == null || req.getRequestId() == null) throw new IllegalArgumentException("requestId required");
        UserFriendRequest fr = userFriendRequestMapper.findById(req.getRequestId());
        if (fr == null) throw new IllegalArgumentException("request not found");
        if (!fr.getToUserId().equals(currentUserId)) throw new IllegalArgumentException("not authorized to handle this request");
        String action = req.getAction();
        if (action == null) throw new IllegalArgumentException("action required");

        // 向好友申请发送方推送消息
        notifyFriendResponse(fr.getFromUserId(), req);

        if ("accept".equalsIgnoreCase(action)) {
            // create friendship if not exists
            long c1 = this.count(Wrappers.<UserFriend>lambdaQuery()
                    .eq(UserFriend::getUserId, currentUserId)
                    .eq(UserFriend::getFriendId, fr.getFromUserId()));
            if (c1 == 0) {
                UserFriend uf1 = new UserFriend();
                uf1.setUserId(currentUserId);
                uf1.setFriendId(fr.getFromUserId());
                this.save(uf1);

                UserFriend uf2 = new UserFriend();
                uf2.setUserId(fr.getFromUserId());
                uf2.setFriendId(currentUserId);
                this.save(uf2);
            }
            userFriendRequestMapper.updateStatusById(fr.getId(), "accepted");
        } else if ("reject".equalsIgnoreCase(action)) {
            userFriendRequestMapper.updateStatusById(fr.getId(), "rejected");
        } else {
            throw new IllegalArgumentException("unknown action");
        }
    }

    /**
     * 精确id搜索（严格匹配）
     */
    public SearchUserResponse searchById(SearchUserRequest req) {
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

    /**
     * 通过socket实时发送好友申请
     */
    private void notifyNewFriendRequest(Long userId, SendFriendRequestRequest req) {
        try {
            wsEventPublisher.sendToUser(userId, "NEW_FRIEND_REQUEST", req);
        } catch (Exception e) {
            log.warn("send friend request failed, userId={}, req={}, err={}",
                    userId, req, e.getMessage());
        }
    }

    /**
     * 会话因“消息变化”（新消息、删除等）导致更新
     */
    private void notifyFriendResponse(Long userId, HandleFriendRequestRequest req) {
        try {
            wsEventPublisher.sendToUser(userId, "FRIEND_REQUEST_RESPONSE", req);
        } catch (Exception e) {
            log.warn("receive friend response failed, userId={}, req={}, err={}",
                    userId, req, e.getMessage());
        }
    }
}