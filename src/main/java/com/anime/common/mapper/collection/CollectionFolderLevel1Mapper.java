package com.anime.common.mapper.collection;

import com.anime.common.entity.collection.CollectionFolderLevel1;
import com.anime.common.entity.user.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户数据访问层 - MyBatis-Plus 版本
 */
@Mapper
public interface CollectionFolderLevel1Mapper extends BaseMapper<User> {

    //首先判断有没有这个收藏夹
    @Select("SELECT * From collection_folders_level1 WHERE name = #{name} AND user_id = #{userId}")
    List<CollectionFolderLevel1> getCollectionFolderLevel1ByName(@Param("name") String name, @Param("userId") Long userId);

    //插入收藏夹，给我附件id，收藏夹名字,用户id
    @Insert("INSERT INTO collection_folders_level1 VALUES  (#{name},#{attachment_id},NOW(),#{user_id})")
    boolean createCollectionFolderLevel1(@Param("name") String name,@Param("attachment_id") Long attachmentId, @Param("user_id") Long user_id);

    //查询所有一级文件夹，给我用户id
    @Select("SELECT * FROM collection_folders_level1 WHERE id = #{user_id}")
    List<CollectionFolderLevel1> findByUserId(@Param("user_id") Long user_id);

    //修改文件夹名字，给我新名字，被修改的一级文件夹id
    @Update("UPDATE collection_floders_level1 SET name = #{new_name} WHERE id = #{level1_id}" )
    boolean updateCollectionFolderName(@Param("new_name") String new_name,@Param("level1_id") Long level1_id);

    //更新一级文件夹封面，给我新封面id，被修改的一级文件夹id
    @Update("UPDATE collection_floders_level1 SET attachment_id = #{new_attachment_id} WHERE id =#{level1_id}")
    boolean updateCollectionFolderPath(@Param("new_attachment_id") Long new_attachment_id ,@Param("level1_id") Long level1_id);

    //删除一级收藏夹，级联删除，给我被修改的一级文件夹id
    @Delete("DELETE FREOM collection_folders_level1 WHERE id = #{level1_id}")
    boolean deleteCollectionFolderLevel1(@Param("level1_id") Long level1_id);



}