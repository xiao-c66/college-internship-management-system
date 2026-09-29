package com.college.internship.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 教师批量导入 Excel 模板与行映射模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherImportTemplateVO implements Serializable {

    @ExcelProperty(value = "教师工号 (必填)", index = 0)
    @ColumnWidth(18)
    private String userNumber;

    @ExcelProperty(value = "登录用户名 (必填)", index = 1)
    @ColumnWidth(18)
    private String username;

    @ExcelProperty(value = "教师姓名 (必填)", index = 2)
    @ColumnWidth(14)
    private String realName;

    @ExcelProperty(value = "手机号码", index = 3)
    @ColumnWidth(16)
    private String phone;

    @ExcelProperty(value = "电子邮箱", index = 4)
    @ColumnWidth(22)
    private String email;

    @ExcelProperty(value = "所属学院 (必填)", index = 5)
    @ColumnWidth(22)
    private String deptName;
}
