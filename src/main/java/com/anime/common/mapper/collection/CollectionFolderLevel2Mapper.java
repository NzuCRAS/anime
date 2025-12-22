package com.anime.common.mapper.collection;

import com.anime.common.entity.collection.CollectionFolderLevel2;
import com.anime.common.entity.user.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户数据访问层 - MyBatis-Plus 版本
 */
@Mapper
public interface CollectionFolderLevel2Mapper extends BaseMapper<User> {

    //首先判断有没有同名收藏夹
    @Select("SELECT * From collection_folders_level2 WHERE name = #{name} AND parent_folder_id = #{fatherId}")
    List<CollectionFolderLevel2> getCollectionFolderLevel2ByName(@Param("name") String name, @Param("fatherId")  Long fatherId);

    //插入收藏夹，给我父文件夹id
    @Insert("INSERT INTO collection_folders_level2 VALUES  (NULL,#{name},#{father_id})")
    boolean createCollectionFolderLevel2(@Param("name") String name, @Param("father_id") Long father_id);

    //查询所有二级文件夹，给我父文件夹id
    @Select("SELECT * FROM collection_folders_level2 WHERE parent_folder_id = #{father_id}")
    List<CollectionFolderLevel2> findByFatherId(@Param("father_id") Long father_id);

    //修改文件夹名字，给我新名字，被修改的二级文件夹id
    @Update("UPDATE collection_floders_level2 SET name = #{new_name} WHERE id = #{level2_id}" )
    boolean updateCollectionFolderName(@Param("new_name") String new_name,@Param("level2_id") Long level2_id);

    //删除二级收藏夹,给我被修改的二级文件夹id，级联删除
    @Delete("DELETE FREOM collection_folders_level2 WHERE id = #{level2_id}")
    boolean deleteCollectionFolderLevel2(@Param("level2_id") Long level2_id);



}