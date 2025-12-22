package com.anime.collection.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.collection.service.CollectedItemService;
import com.anime.common.dto.attachment.PresignRequestDTO;
import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.dto.collection.items.*;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.common.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 收藏项控制器
 */
@Tag(name = "CollectedItem", description = "收藏项相关接口（创建/更新/删除）")
@RestController
@Slf4j
@RequestMapping("/api/collection/item")
@RequiredArgsConstructor
public class CollectedItemController {

    private final CollectedItemService collectedItemService;
    private final AttachmentService attachmentService;

    @Operation(summary = "获取 presign（Items 专用）", description = "生成 presigned PUT URL，供前端上传  相关附件")
    @PostMapping("/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequestDTO req, @CurrentUser Long userId) {
        String storagePath = "/Items/" + userId + "/"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        log.info("presign request storagePath={} originalFilename={} mimeType={} uploadedBy={}",
                storagePath, req.getOriginalFilename(), req.getMimeType(), userId);
        try {
            PresignResponseDTO resp = attachmentService.preCreateAndPresign(
                    storagePath, req.getMimeType(), userId, req.getOriginalFilename(), null, null);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            log.error("presign failed", ex);
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body("presign failed");
        }
    }

    @Operation(summary = "创建自定义收藏项", description = "创建带自定义封面、名称和描述的收藏项")
    @PostMapping("/createCustom")
    public ResponseEntity<Result<String>> createCustom(@RequestBody ItemCreateDTO dto,
                                                       @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (dto.getAttachment_id() == null || dto.getAttachment_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "封面附件ID无效"));
            }
            if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "名称不能为空"));
            }
            if (dto.getFather_level2_id() == null || dto.getFather_level2_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "所属二级收藏夹ID无效"));
            }

            boolean created = collectedItemService.createWithCustom(
                    dto.getAttachment_id(),
                    dto.getName(),
                    dto.getDescription(),
                    dto.getFather_level2_id()
            );

            if (created) {
                return ResponseEntity.ok(Result.success("收藏项创建成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "收藏项创建失败"));
            }
        } catch (RuntimeException e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if ("INVALID_USER".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "用户ID无效"));
            }
            throw e;
        }
    }

    @Operation(summary = "获取用户收藏", description = "获取当前用户的所有收藏")
    @GetMapping("/getItems")
    public ResponseEntity<Result<List<ItemResultDTO>>> getItems(@RequestBody ItemGetDTO dto,
                                                                @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, null));
            }
            if (dto.getFather_level2_id() == null || dto.getFather_level2_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, null));
            }
            List<ItemResultDTO> items = collectedItemService.getItems(dto.getFather_level2_id());
            if (items  == null) {
                items = new ArrayList<>();
            }
            return ResponseEntity.ok(Result.success(items));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, null));
        }
    }

    @Operation(summary = "更新收藏项封面", description = "更新指定收藏项的封面 attachment id")
    @PutMapping("/update/cover")
    public ResponseEntity<Result<String>> updateCover(@RequestBody ItemUpdateCoverDTO dto,
                                                      @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (dto.getId() == null || dto.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏项ID无效"));
            }
            if (dto.getAttachment_id() == null || dto.getAttachment_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "封面附件ID无效"));
            }
            boolean updated = collectedItemService.updateAttachmentId(dto.getId(), dto.getAttachment_id());
            if (updated) {
                return ResponseEntity.ok(Result.success("封面更新成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "封面更新失败"));
            }
        } catch (RuntimeException e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if ("INVALID_USER".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "用户ID无效"));
            }
            throw e;
        }
    }

    @Operation(summary = "更新收藏项名称", description = "更新指定收藏项的名称")
    @PutMapping("/update/name")
    public ResponseEntity<Result<String>> updateName(@RequestBody ItemUpdateNameDTO dto,
                                                     @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (dto.getId() == null || dto.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏项ID无效"));
            }
            if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "名称不能为空"));
            }
            boolean updated = collectedItemService.updateName(dto.getId(), dto.getName());
            if (updated) {
                return ResponseEntity.ok(Result.success("名称更新成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "名称更新失败"));
            }
        } catch (RuntimeException e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if ("INVALID_USER".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "用户ID无效"));
            }
            throw e;
        }
    }

    @Operation(summary = "更新收藏项描述", description = "更新指定收藏项的描述")
    @PutMapping("/update/description")
    public ResponseEntity<Result<String>> updateDescription(@RequestBody ItemUpdateDescriptionDTO dto,
                                                            @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (dto.getId() == null || dto.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏项ID无效"));
            }
            boolean updated = collectedItemService.updateDescription(dto.getId(), dto.getDescription());
            if (updated) {
                return ResponseEntity.ok(Result.success("描述更新成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "描述更新失败"));
            }
        } catch (RuntimeException e) {
            if ("UNAUTHORIZED".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if ("INVALID_USER".equals(e.getMessage())) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "用户ID无效"));
            }
            throw e;
        }
    }

    @Operation(summary = "删除收藏项", description = "删除指定收藏项（逻辑/物理删除由 service 决定）")
    @DeleteMapping("/delete")
    public ResponseEntity<Result<String>> deleteItem(@RequestBody ItemDeleteDTO dto,
                                                     @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (dto.getId() == null || dto.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏项ID无效"));
            }
            boolean deleted = collectedItemService.deleteCollectionItem(dto.getId());
            if (deleted) {
                return ResponseEntity.ok(Result.success("收藏项删除成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "收藏项删除失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "系统异常: " + e.getMessage()));
        }
    }
}