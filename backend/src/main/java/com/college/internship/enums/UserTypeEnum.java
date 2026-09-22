package com.college.internship.enums;

import lombok.Getter;

/**
 * 四类核心用户类型枚举
 */
@Getter
public enum UserTypeEnum {

    STUDENT("STUDENT", "ROLE_STUDENT", "学生"),
    TEACHER("TEACHER", "ROLE_TEACHER", "指导教师"),
    DEPT_ADMIN("DEPT_ADMIN", "ROLE_DEPT_ADMIN", "院系负责人"),
    SYS_ADMIN("SYS_ADMIN", "ROLE_SYS_ADMIN", "学校管理员");

    private final String code;
    private final String roleCode;
    private final String name;

    UserTypeEnum(String code, String roleCode, String name) {
        this.code = code;
        this.roleCode = roleCode;
        this.name = name;
    }

    public static UserTypeEnum fromCode(String code) {
        for (UserTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }
}
