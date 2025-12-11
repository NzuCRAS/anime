package com.anime.collection.service;

import com.anime.common.entity.collection.CollectionFolderLevel2;
import com.anime.common.mapper.collection.CollectionFolderLevel2Mapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CollectionFolderLevel2Service {

    CollectionFolderLevel2Mapper collectionFolderLevel2Mapper;

    public CollectionFolderLevel2Service(CollectionFolderLevel2Mapper collectionFolderLevel2Mapper) {
        this.collectionFolderLevel2Mapper = collectionFolderLevel2Mapper;
    }

    //判断有无默认收藏夹
    public List<CollectionFolderLevel2> getCollectionFolderLevel2ByName() {
        if(collectionFolderLevel2Mapper.getCollectionFolderLevel2ByName() == null){
            return new ArrayList<>();
        }
        return collectionFolderLevel2Mapper.getCollectionFolderLevel2ByName();
    }

    //新建新的收藏夹（给我父文件夹id）
    public boolean createNewFolder(Long father_id) {
        if (collectionFolderLevel2Mapper.getCollectionFolderLevel2ByName() == null){
            return false;
        }
        return collectionFolderLevel2Mapper.createCollectionFolderLevel2(father_id);
    }

    //查询所有收藏夹（给我父文件夹id）
    public List<CollectionFolderLevel2> getCollectionFolderLevel(Long father_id) {
        if (collectionFolderLevel2Mapper.getCollectionFolderLevel2ByName() == null){
            return new ArrayList<>();
        }
        return collectionFolderLevel2Mapper.findByFatherId(father_id);
    }

    //更新收藏夹名（给我新名字，被修改的收藏夹id）
    public boolean UpdateName(String new_name, Long level2_id) {
        if (collectionFolderLevel2Mapper.getCollectionFolderLevel2ByName() == null){
            return false;
        }
        return collectionFolderLevel2Mapper.updateCollectionFolderName(new_name,level2_id);
    }

    //删除二级收藏夹，级联删除，给我二级文件夹id
    public boolean DeleteCollectionFolderLevel2(Long level2_id) {
        if (collectionFolderLevel2Mapper.getCollectionFolderLevel2ByName() == null){
            return false;
        }
        return collectionFolderLevel2Mapper.deleteCollectionFolderLevel2(level2_id);
    }


}
