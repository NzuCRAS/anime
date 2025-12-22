package com.anime.collection.service;

import com.anime.common.dto.collection.leve1.Level1ResultDTO;
import com.anime.common.entity.collection.CollectionFolderLevel1;
import com.anime.common.mapper.collection.CollectionFolderLevel1Mapper;
import com.anime.common.service.AttachmentService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class CollectionFolderLevel1Service {

    private CollectionFolderLevel1Mapper collectionFolderLevel1Mapper;
    private static final long ATTACHMENT_URL_TTL_SECONDS = 600L;
    private AttachmentService attachmentService;

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
    public List<Level1ResultDTO> getCollectionFolderLevel1(Long user_id) {
        if(user_id == null){
            return new ArrayList<>();
        }
        List<CollectionFolderLevel1> level1s = collectionFolderLevel1Mapper.findByUserId(user_id);
        log.info(level1s.toString());
        List<Level1ResultDTO> results = new ArrayList<>();
        Level1ResultDTO level1ResultDTO = new Level1ResultDTO();
        for (CollectionFolderLevel1 collectionFolderLevel1 : level1s) {
            level1ResultDTO.setId(collectionFolderLevel1.getId());
            level1ResultDTO.setName(collectionFolderLevel1.getName());
            level1ResultDTO.setURL(attachmentService.generatePresignedGetUrl(collectionFolderLevel1.getAttachmentId(),ATTACHMENT_URL_TTL_SECONDS));
            results.add(level1ResultDTO);
        }
        return results;
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
