package com.college.internship.service;

import com.college.internship.dto.InspectPlanCreateDTO;
import com.college.internship.dto.InspectionSubmitDTO;
import com.college.internship.dto.RectifyCreateDTO;
import com.college.internship.dto.RectifyReviewDTO;
import com.college.internship.dto.RectifySubmitDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.InspectPlanVO;
import com.college.internship.vo.InspectionVO;
import com.college.internship.vo.RectifyVO;

import java.util.List;

/**
 * 中期检查与限期整改业务服务接口 (API-074 ~ API-081)
 */
public interface IInspectService {

    Long createInspectPlan(InspectPlanCreateDTO dto, LoginUser loginUser);

    Integer executeSampling(Long planId, LoginUser loginUser);

    List<InspectPlanVO> getInspectPlans(Long taskId, LoginUser loginUser);

    Long submitInspection(InspectionSubmitDTO dto, LoginUser loginUser);

    List<InspectionVO> getInspectionList(Long planId, Long taskId, String status, LoginUser loginUser);

    Long createRectification(RectifyCreateDTO dto, LoginUser loginUser);

    void submitRectification(Long id, RectifySubmitDTO dto, LoginUser loginUser);

    void reviewRectification(Long id, RectifyReviewDTO dto, LoginUser loginUser);

    void closeRectification(Long id, LoginUser loginUser);

    List<RectifyVO> getRectificationList(Long taskId, String status, LoginUser loginUser);
}
