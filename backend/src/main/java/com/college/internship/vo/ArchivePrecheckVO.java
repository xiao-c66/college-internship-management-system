package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchivePrecheckVO {
    private Long studentId;
    private String studentName;
    private String studentNo;
    private Long taskId;
    private Boolean passed;
    private Integer passedCount;
    private Integer totalCount;
    private List<CheckItem> checkItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckItem {
        private String code;
        private String name;
        private Boolean passed;
        private String detail;
        private String blockReason;
    }
}
