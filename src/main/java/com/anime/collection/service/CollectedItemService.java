package com.anime.collection.service;

import com.anime.common.dto.collection.items.ItemResultDTO;
import com.anime.common.entity.collection.CollectedItem;
import com.anime.common.mapper.collection.CollectedItemMapper;
import com.anime.common.service.AttachmentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CollectedItemService {

    private final CollectedItemMapper collectedItemMapper;
    private AttachmentService attachmentService;
    private static final long ATTACHMENT_URL_TTL_SECONDS = 600L;

    public CollectedItemService(CollectedItemMapper collectedItemMapper) {
        this.collectedItemMapper = collectedItemMapper;
    }

    /**
     * 创建收藏项（自定义封面、名称、描述）
     */
    public boolean createWithCustom(Long attachmentId, String name, String description, Long folderLevel2Id) {
        if (attachmentId == null || attachmentId <= 0 || name == null || name.trim().isEmpty() || folderLevel2Id == null || folderLevel2Id <= 0) {
            return false;
        }
        CollectedItem item = new CollectedItem();
        item.setAttachmentId(attachmentId);
        item.setName(name.trim());
        item.setDescription(description != null ? description.trim() : "");
        item.setFolderLevel2Id(folderLevel2Id);
        return collectedItemMapper.insert(item) > 0;
    }

    /**
     * 更新收藏项的封面（attachmentId）
     */
    public boolean updateAttachmentId(Long itemId, Long newAttachmentId) {
        if (itemId == null || itemId <= 0 || newAttachmentId == null || newAttachmentId <= 0) {
            return false;
        }

        CollectedItem item = new CollectedItem();
        item.setId(itemId);
        item.setAttachmentId(newAttachmentId);
        // lastModifiedTime 由 MP 自动填充
        return collectedItemMapper.updateById(item) > 0;
    }

    /**
     * 更新收藏项的名称
     */
    public boolean updateName(Long itemId, String newName) {
        if (itemId == null || itemId <= 0 || newName == null || newName.trim().isEmpty()) {
            return false;
        }

        CollectedItem item = new CollectedItem();
        item.setId(itemId);
        item.setName(newName.trim());
        return collectedItemMapper.updateById(item) > 0;
    }

    /**
     * 获取收藏物
     */
    public List<ItemResultDTO> getItems(Long level2Id) {
        if(level2Id == null){
            return new ArrayList<>();
        }
        List<CollectedItem> items = collectedItemMapper.selectList(
                new QueryWrapper<CollectedItem>().eq("level2_id", level2Id)
        );
        List<ItemResultDTO> results = new ArrayList<>();
        ItemResultDTO itemResultDTO = new  ItemResultDTO();
        for (CollectedItem collectionFolderLevel1 : items) {
            itemResultDTO.setId(collectionFolderLevel1.getId());
            itemResultDTO.setName(collectionFolderLevel1.getName());
            itemResultDTO.setURL(attachmentService.generatePresignedGetUrl(collectionFolderLevel1.getAttachmentId(),ATTACHMENT_URL_TTL_SECONDS));
            results.add(itemResultDTO);
        }
        return results;
    }

    /**
     * 更新收藏项的描述
     */
    public boolean updateDescription(Long itemId, String newDescription) {
        if (itemId == null || itemId <= 0) {
            return false;
        }

        CollectedItem item = new CollectedItem();
        item.setId(itemId);
        item.setDescription(newDescription != null ? newDescription.trim() : "");
        return collectedItemMapper.updateById(item) > 0;
    }

     /**
     *  删除收藏项
     */
     public boolean deleteCollectionItem(Long itemId) {
         if (itemId == null || itemId <= 0) {
             return false;
         }
         return collectedItemMapper.deleteById(itemId) > 0;
     }
}
