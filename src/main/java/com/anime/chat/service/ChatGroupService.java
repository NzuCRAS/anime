package com.anime.chat.service;

import com.anime.common.dto.chat.group.*;
import com.anime.common.entity.chat.ChatGroup;
import com.anime.common.entity.chat.ChatGroupMember;
import com.anime.common.entity.user.User;
import com.anime.common.mapper.chat.ChatGroupMapper;
import com.anime.common.mapper.chat.ChatGroupMemberMapper;
import com.anime.common.mapper.user.UserMapper;
import com.anime.common.service.AttachmentService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatGroupService extends ServiceImpl<ChatGroupMapper, ChatGroup> {

    private final ChatGroupMemberMapper groupMemberMapper;
    private final UserMapper userMapper;
    private final AttachmentService attachmentService;

    @Transactional
    public CreateGroupResponse createGroup(CreateGroupRequest request, Long ownerId) {
        // 1. 创建群记录
        ChatGroup group = new ChatGroup();
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setOwnerId(ownerId);
        this.save(group);

        Long groupId = group.getId();

        // 2. 群主加入为 owner
        addMemberInternal(groupId, ownerId, "owner");

        // 3. 其他成员加入为 member
        if (request.getMemberUuids() != null) {
            for (Long uid : request.getMemberUuids()) {
                if (uid != null && !uid.equals(ownerId)) {
                    addMemberInternal(groupId, uid, "member");
                }
            }
        }

        // 4. 构造响应
        CreateGroupResponse resp = new CreateGroupResponse();
        resp.setGroupId(groupId);
        resp.setName(group.getName());
        resp.setDescription(group.getDescription());
        resp.setOwnerId(ownerId);
        return resp;
    }

    private void addMemberInternal(Long groupId, Long userId, String role) {
        ChatGroupMember m = new ChatGroupMember();
        m.setGroupId(groupId);
        m.setUserId(userId);
        m.setRole(role);
        groupMemberMapper.insert(m);
    }

    public ListGroupsResponse listMyGroups(ListGroupsRequest request, Long currentUserId) {
        // 1. 查 group_members
        List<ChatGroupMember> links = groupMemberMapper.selectList(
                Wrappers.<ChatGroupMember>lambdaQuery().eq(ChatGroupMember::getUserId, currentUserId)
        );

        ListGroupsResponse resp = new ListGroupsResponse();
        if (links.isEmpty()) {
            resp.setGroups(List.of());
            return resp;
        }

        // 2. 查 group 表
        List<Long> groupIds = links.stream()
                .map(ChatGroupMember::getGroupId)
                .distinct()
                .toList();
        List<ChatGroup> groups = this.listByIds(groupIds);

        // 3. 转为 DTO
        List<GroupItem> items = groups.stream().map(g -> {
            GroupItem item = new GroupItem();
            item.setGroupId(g.getId());
            item.setName(g.getName());
            item.setDescription(g.getDescription());
            item.setOwnerId(g.getOwnerId());
            return item;
        }).toList();
        resp.setGroups(items);
        return resp;
    }

    public ListGroupMembersResponse listGroupMembers(ListGroupMembersRequest request, Long currentUserId) {
        Long groupId = request.getGroupId();
        // 可选：校验 currentUserId 是否为群成员

        // 1. 查链接表
        List<ChatGroupMember> links = groupMemberMapper.selectList(
                Wrappers.<ChatGroupMember>lambdaQuery().eq(ChatGroupMember::getGroupId, groupId)
        );

        ListGroupMembersResponse resp = new ListGroupMembersResponse();
        if (links.isEmpty()) {
            resp.setMembers(List.of());
            return resp;
        }

        // 2. 查用户信息
        List<Long> userIds = links.stream().map(ChatGroupMember::getUserId).distinct().toList();
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, ChatGroupMember> roleMap = links.stream()
                .collect(Collectors.toMap(ChatGroupMember::getUserId, m -> m));

        // 3. 转 DTO
        List<GroupMember> members = users.stream().map(u -> {
            GroupMember gm = new GroupMember();
            gm.setUserId(u.getId());
            gm.setUsername(u.getUsername());
            gm.setEmail(u.getEmail());
            gm.setRole(roleMap.get(u.getId()).getRole());
            Long avatarAttId = userMapper.getAvatarAttachmentIdById(u.getId());
            if (avatarAttId != null) {
                gm.setAvatarUrl(attachmentService.generatePresignedGetUrl(avatarAttId, 3600));
            }
            return gm;
        }).toList();
        resp.setMembers(members);
        return resp;
    }
}