package com.anime.collection.service;

import com.anime.common.dto.collection.items.ItemResultDTO;
import com.anime.common.entity.collection.CollectedItem;
import com.anime.common.mapper.collection.CollectedItemMapper;
import com.anime.common.service.AttachmentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CollectedItemService {

    private final CollectedItemMapper collectedItemMapper;
    private final AttachmentService attachmentService;
    private static final long ATTACHMENT_URL_TTL_SECONDS = 600L;

    // 注入 AttachmentService
    public CollectedItemService(CollectedItemMapper collectedItemMapper, AttachmentService attachmentService) {
        this.collectedItemMapper = collectedItemMapper;
        this.attachmentService = attachmentService;
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

        // 显式设置时间，避免依赖 MyBatis-Plus 自动填充（项目中可能未配置 MetaObjectHandler）
        LocalDateTime now = LocalDateTime.now();
        item.setCreatedTime(now);
        item.setLastModifiedTime(now);

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
        // lastModifiedTime 由我们手动设置（如果需要）
        item.setLastModifiedTime(LocalDateTime.now());
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
        item.setLastModifiedTime(LocalDateTime.now());
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
                new QueryWrapper<CollectedItem>().eq("folder_level2_id", level2Id)
        );
        List<ItemResultDTO> results = new ArrayList<>();
        for (CollectedItem collectionItem : items) {
            ItemResultDTO itemResultDTO = new ItemResultDTO();
            itemResultDTO.setId(collectionItem.getId());
            itemResultDTO.setName(collectionItem.getName());
            // 生成短期预签名 URL 用于前端展示
            try {
                if (collectionItem.getAttachmentId() != null) {
                    String url = attachmentService.generatePresignedGetUrl(collectionItem.getAttachmentId(), ATTACHMENT_URL_TTL_SECONDS);
                    // 方法名视 ItemResultDTO 的字段名而定，下面尝试 setUrl / setAttachmentUrl / setURL 等，你需要使用实际 DTO 的 setter
                    // 我假设 DTO 有 setUrl 方法（如果 DTO 字段名不同，请相应调整）
                    itemResultDTO.setURL(url);
                }
            } catch (Exception ex) {
                // 忽略单个 attachment 的 URL 生成错误
            }
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
        item.setLastModifiedTime(LocalDateTime.now());
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