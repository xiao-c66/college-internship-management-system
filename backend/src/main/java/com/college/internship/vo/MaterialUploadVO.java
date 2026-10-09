package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialUploadVO {
    private String fileName;
    private Long fileSize;
    private String fileHash;
    private String attachmentUrl;
}
