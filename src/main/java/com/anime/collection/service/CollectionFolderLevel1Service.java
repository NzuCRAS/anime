package com.anime.collection.service;

import com.anime.common.entity.CollectionFolderLevel1;
import com.anime.common.mapper.CollectionFolderLevel1Mapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CollectionFolderLevel1Service {

    CollectionFolderLevel1Mapper collectionFolderLevel1Mapper;

    public CollectionFolderLevel1Service(CollectionFolderLevel1Mapper collectionFolderLevel1Mapper) {
        this.collectionFolderLevel1Mapper = collectionFolderLevel1Mapper;
    }


    //判断有无默认收藏夹
    public List<CollectionFolderLevel1> getCollectionFolderLevel1ByName() {
        return collectionFolderLevel1Mapper.getCollectionFolderLevel1ByName();
    }
    //新建新的收藏夹（给我附件id，用户id）
    public boolean createNewFolder(Long user_id) {
        return collectionFolderLevel1Mapper.createCollectionFolderLevel1(user_id);
    }

    //查询所有收藏夹（给我用户id）
    public List<CollectionFolderLevel1> getCollectionFolderLevel1(Long user_id) {
        return collectionFolderLevel1Mapper.findByUserId(user_id);
    }

    //更新收藏夹名（给我新名字，被修改的收藏夹id）
    public boolean UpdateName(String new_name, Long level1_id) {
        return collectionFolderLevel1Mapper.updateCollectionFolderName(new_name,level1_id);
    }

    //更新收藏夹封面（给我新封面附件id，被修改的收藏夹id）
    public boolean UpdateCover(Long new_attachment_id, Long level1_id) {
        return collectionFolderLevel1Mapper.updateCollectionFolderPath(new_attachment_id,level1_id);
    }


}
