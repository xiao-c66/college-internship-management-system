package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 安全教育资料学习清单实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("safety_material_item")
public class SafetyMaterialItem extends BaseEntity {

    private Long taskId;
    private String title;
    private String contentType;
    private String contentBody;
    private String fileUrl;
    private Integer sortOrder;
    private Integer status;
}
