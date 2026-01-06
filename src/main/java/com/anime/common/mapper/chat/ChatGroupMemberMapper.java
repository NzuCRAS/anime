package com.anime.common.mapper.chat;

import com.anime.common.entity.chat.ChatGroupMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatGroupMemberMapper extends BaseMapper<ChatGroupMember> {
    /**
     * 查询某个群的所有成员 userId 列表
     */
    @Select("SELECT user_id FROM chat_group_members WHERE group_id = #{groupId}")
    List<Long> listUserIdsByGroupId(Long groupId);
}