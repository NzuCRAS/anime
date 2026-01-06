package com.anime.common.mapper;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器：
 * 在插入/更新时自动设置时间字段。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();

        // 根据你项目中的实际字段名填充
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createdTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "created_at", LocalDateTime.class, now);
        // 有 updatedAt / updatedTime 的也可以一起填
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "joined_at", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictUpdateFill(metaObject, "updatedTime", LocalDateTime.class, now);
        strictUpdateFill(metaObject, "updated_at", LocalDateTime.class, now);
        strictUpdateFill(metaObject, "joined_at", LocalDateTime.class, now);
    }
}