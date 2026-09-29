package com.college.internship.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户列表查询入参 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQueryDTO implements Serializable {

    private String keyword;
    private String userType;
    private Long deptId;
    private Long majorId;
    private Long classId;
    private Integer status;
}
