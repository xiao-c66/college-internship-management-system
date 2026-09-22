package com.college.internship.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 过程指导走访台账 EasyExcel 导出模型 (API-066)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidanceExportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "序号", index = 0)
    @ColumnWidth(8)
    private Integer index;

    @ExcelProperty(value = "指导时间", index = 1)
    @ColumnWidth(20)
    private String guidanceTime;

    @ExcelProperty(value = "指导方式", index = 2)
    @ColumnWidth(14)
    private String guidanceType;

    @ExcelProperty(value = "学生学号", index = 3)
    @ColumnWidth(16)
    private String studentNumber;

    @ExcelProperty(value = "学生姓名", index = 4)
    @ColumnWidth(12)
    private String studentName;

    @ExcelProperty(value = "班级", index = 5)
    @ColumnWidth(18)
    private String className;

    @ExcelProperty(value = "实习单位", index = 6)
    @ColumnWidth(25)
    private String companyName;

    @ExcelProperty(value = "指导教师", index = 7)
    @ColumnWidth(12)
    private String teacherName;

    @ExcelProperty(value = "交流内容要点", index = 8)
    @ColumnWidth(35)
    private String contentSummary;

    @ExcelProperty(value = "走访地点", index = 9)
    @ColumnWidth(20)
    private String location;

    @ExcelProperty(value = "学生反馈内容", index = 10)
    @ColumnWidth(30)
    private String studentFeedback;

    @ExcelProperty(value = "首次反馈时间", index = 11)
    @ColumnWidth(20)
    private String feedbackTime;

    @ExcelProperty(value = "反馈确认状态", index = 12)
    @ColumnWidth(14)
    private String feedbackStatus;

    @ExcelProperty(value = "凭证附件URL", index = 13)
    @ColumnWidth(30)
    private String attachmentUrl;
}
