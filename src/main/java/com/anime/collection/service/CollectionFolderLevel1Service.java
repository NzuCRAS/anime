package com.anime.collection.service;

import com.anime.common.entity.collection.CollectionFolderLevel1;
import com.anime.common.mapper.collection.CollectionFolderLevel1Mapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CollectionFolderLevel1Service {

    CollectionFolderLevel1Mapper collectionFolderLevel1Mapper;

    public CollectionFolderLevel1Service(CollectionFolderLevel1Mapper collectionFolderLevel1Mapper) {
        this.collectionFolderLevel1Mapper = collectionFolderLevel1Mapper;
    }

    //判断有无同名收藏夹
    public List<CollectionFolderLevel1> getCollectionFolderLevel1ByName(String name,Long userId) {
        return collectionFolderLevel1Mapper.getCollectionFolderLevel1ByName(name,userId);
    }
    //新建新的收藏夹（给我收藏夹名字，收藏夹封面id，用户id）
    public boolean createNewFolder(String name,Long attachmentId,Long user_id) {
        if(user_id == null && attachmentId == null){
            return false;
        }
        return collectionFolderLevel1Mapper.createCollectionFolderLevel1(name,attachmentId,user_id);
    }

    //查询所有收藏夹（给我用户id）
    public List<CollectionFolderLevel1> getCollectionFolderLevel1(Long user_id) {
        if(user_id == null){
            return new ArrayList<>();
        }
        return collectionFolderLevel1Mapper.findByUserId(user_id);
    }

    //更新收藏夹名（给我新名字，被修改的收藏夹id）
    public boolean UpdateName(String new_name, Long level1_id) {
        if(level1_id == null){
            return false;
        }
        return collectionFolderLevel1Mapper.updateCollectionFolderName(new_name,level1_id);
    }

    //更新收藏夹封面（给我新封面附件id，被修改的收藏夹id）
    public boolean UpdateCover(Long new_attachment_id, Long level1_id) {
        if(level1_id == null){
            return false;
        }
        return collectionFolderLevel1Mapper.updateCollectionFolderPath(new_attachment_id,level1_id);
    }

    //删除收藏夹，给我一级收藏夹id
    public boolean DeleteCollectionFolderLevel1(Long level1_id) {
        if(level1_id == null){
            return false;
        }
        return collectionFolderLevel1Mapper.deleteCollectionFolderLevel1(level1_id);
    }


}
