package com.college.internship.service;

import com.college.internship.dto.MaterialAuditDTO;
import com.college.internship.dto.MaterialSubmitDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.MaterialItemVO;
import com.college.internship.vo.MaterialUploadVO;
import com.college.internship.vo.MaterialVersionVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 阶段材料与实习总结报告业务服务接口 (API-060 ~ API-064)
 */
public interface IMaterialService {

    List<MaterialItemVO> getMaterialList(Long taskId, Long studentId, LoginUser loginUser);

    MaterialItemVO getMaterialDetail(Long id, LoginUser loginUser);

    Long submitMaterial(MaterialSubmitDTO dto, LoginUser loginUser);

    void auditMaterial(Long id, MaterialAuditDTO dto, LoginUser loginUser);

    List<MaterialVersionVO> getMaterialVersions(Long id, LoginUser loginUser);

    MaterialUploadVO uploadMaterialFile(MultipartFile file, Long taskId, String materialCode, LoginUser loginUser);

    void downloadMaterialFile(Long id, HttpServletResponse response, LoginUser loginUser);

    void downloadVersionFile(Long versionId, HttpServletResponse response, LoginUser loginUser);
}
