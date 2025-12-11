package com.anime.collection.service;

import com.anime.common.entity.collection.CollectedItem;
import com.anime.common.mapper.collection.CollectedItemMapper;
import org.springframework.stereotype.Service;

@Service
public class CollectedItemService {

    private final CollectedItemMapper collectedItemMapper;

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
     * 创建收藏项（使用默认封面和名称，仅描述可自定义）
     */
    public boolean createWithDefault(String description, Long folderLevel2Id) {
        // 默认值
        Long defaultAttachmentId = 1L; // 可配置化，此处硬编码示例
        String defaultName = "默认收藏项";

        if (folderLevel2Id == null || folderLevel2Id <= 0) {
            return false;
        }
        CollectedItem item = new CollectedItem();
        item.setAttachmentId(defaultAttachmentId);
        item.setName(defaultName);
        item.setDescription(description != null ? description.trim() : "");
        item.setFolderLevel2Id(folderLevel2Id);
        return collectedItemMapper.insert(item) > 0;
    }

    /**
     * 更新收藏项的封面（attachmentId）
     */
    public boolean updateAttachmentId(Long itemId, Long newAttachmentId, Long userId) {
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
    public boolean updateName(Long itemId, String newName, Long userId) {
        if (itemId == null || itemId <= 0 || newName == null || newName.trim().isEmpty()) {
            return false;
        }

        CollectedItem item = new CollectedItem();
        item.setId(itemId);
        item.setName(newName.trim());
        return collectedItemMapper.updateById(item) > 0;
    }

    /**
     * 更新收藏项的描述
     */
    public boolean updateDescription(Long itemId, String newDescription, Long userId) {
        if (itemId == null || itemId <= 0) {
            return false;
        }

        CollectedItem item = new CollectedItem();
        item.setId(itemId);
        item.setDescription(newDescription != null ? newDescription.trim() : "");
        return collectedItemMapper.updateById(item) > 0;
    }
}
