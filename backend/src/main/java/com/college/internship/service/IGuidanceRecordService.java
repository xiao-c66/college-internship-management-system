package com.college.internship.service;

import com.college.internship.dto.GuidanceCreateDTO;
import com.college.internship.dto.GuidanceFeedbackDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.GuidanceRecordVO;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 阶段6 过程指导与走访台账核心服务接口 (API-065 ~ API-067)
 */
public interface IGuidanceRecordService {

    /**
     * API-065 教师登记过程指导/实地走访台账
     */
    GuidanceRecordVO createGuidance(GuidanceCreateDTO dto, LoginUser loginUser);

    /**
     * API-066 查询过程指导走访台账列表 (学生仅查本人, 教师查带教, 院系查本院)
     */
    List<GuidanceRecordVO> listGuidances(Long taskId, Long studentId, String guidanceType,
                                        LocalDate startDate, LocalDate endDate, LoginUser loginUser);

    /**
     * API-066 导出过程走访台账 Excel (复用API-066，带 export=excel，学生调用报403)
     */
    void exportGuidanceExcel(Long taskId, Long studentId, String guidanceType,
                            LocalDate startDate, LocalDate endDate,
                            HttpServletResponse response, LoginUser loginUser);

    /**
     * API-067 学生确认指导记录并提交在岗反馈 (CAS事务条件更新防重)
     */
    GuidanceRecordVO submitFeedback(Long id, GuidanceFeedbackDTO dto, LoginUser loginUser);
}
