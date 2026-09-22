package com.college.internship.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * 为持久层实体自动填充 createTime, updateTime, isDeleted
 */
@Slf4j
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", () -> now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updateTime", () -> now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "createdAt", () -> now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updatedAt", () -> now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "isDeleted", () -> 0, Integer.class);

        // 兜底保障填充
        if (metaObject.hasSetter("createTime") && getFieldValByName("createTime", metaObject) == null) {
            setFieldValByName("createTime", now, metaObject);
        }
        if (metaObject.hasSetter("createdAt") && getFieldValByName("createdAt", metaObject) == null) {
            setFieldValByName("createdAt", now, metaObject);
        }
        if (metaObject.hasSetter("updateTime") && getFieldValByName("updateTime", metaObject) == null) {
            setFieldValByName("updateTime", now, metaObject);
        }
        if (metaObject.hasSetter("updatedAt") && getFieldValByName("updatedAt", metaObject) == null) {
            setFieldValByName("updatedAt", now, metaObject);
        }
        if (metaObject.hasSetter("isDeleted") && getFieldValByName("isDeleted", metaObject) == null) {
            setFieldValByName("isDeleted", 0, metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, "updateTime", () -> now, LocalDateTime.class);
        this.strictUpdateFill(metaObject, "updatedAt", () -> now, LocalDateTime.class);
        if (metaObject.hasSetter("updateTime")) {
            setFieldValByName("updateTime", now, metaObject);
        }
        if (metaObject.hasSetter("updatedAt")) {
            setFieldValByName("updatedAt", now, metaObject);
        }
    }
}
