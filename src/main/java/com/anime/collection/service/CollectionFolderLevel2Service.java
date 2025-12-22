package com.anime.collection.service;

import com.anime.common.dto.collection.level2.Levev2ResultDTO;
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

    //判断有无同名收藏夹
    public List<CollectionFolderLevel2> getCollectionFolderLevel2ByName(String name, Long  fatherId) {
        return collectionFolderLevel2Mapper.getCollectionFolderLevel2ByName(name, fatherId);
    }

    //新建新的收藏夹（给我父文件夹id）
    public boolean createNewFolder(String name, Long father_id) {
        if (collectionFolderLevel2Mapper.getCollectionFolderLevel2ByName(name, father_id) == null){
            return false;
        }
        return collectionFolderLevel2Mapper.createCollectionFolderLevel2(name, father_id);
    }

    //查询所有收藏夹（给我父文件夹id）
    public List<Levev2ResultDTO> getCollectionFolderLevel(Long father_id) {
        if (father_id==null){
            return new ArrayList<>();
        }
        List<CollectionFolderLevel2> level2 = collectionFolderLevel2Mapper.findByFatherId(father_id);
        List<Levev2ResultDTO> results = new ArrayList<>();
        Levev2ResultDTO levev2ResultDTO = new Levev2ResultDTO();
        for (CollectionFolderLevel2 collectionFolderLevel2 : level2) {
            levev2ResultDTO.setId(collectionFolderLevel2.getId());
            levev2ResultDTO.setName(collectionFolderLevel2.getName());
            levev2ResultDTO.setFather_id(collectionFolderLevel2.getParentFolderId());
            results.add(levev2ResultDTO);
        }
        return  results;
    }

    //更新收藏夹名（给我新名字，被修改的收藏夹id）
    public boolean UpdateName(String new_name, Long level2_id) {
        if (level2_id==null) return false;
        return collectionFolderLevel2Mapper.updateCollectionFolderName(new_name,level2_id);
    }

    //删除二级收藏夹，级联删除，给我二级文件夹id
    public boolean DeleteCollectionFolderLevel2(Long level2_id) {
        if(level2_id==null) return false;
        return collectionFolderLevel2Mapper.deleteCollectionFolderLevel2(level2_id);
    }


}
