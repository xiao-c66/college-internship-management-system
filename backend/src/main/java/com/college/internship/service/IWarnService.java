package com.college.internship.service;

import com.college.internship.dto.WarnFeedbackDTO;
import com.college.internship.dto.WarnHandleDTO;
import com.college.internship.dto.WarnRuleUpdateDTO;
import com.college.internship.dto.WarnTicketDispatchDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.WarnRuleVO;
import com.college.internship.vo.WarnTicketVO;

import java.util.List;
import java.util.Map;

/**
 * 异常预警全景引擎业务服务接口 (API-082 ~ API-090)
 */
public interface IWarnService {

    List<WarnRuleVO> getRuleList(LoginUser loginUser);

    void updateRule(Long id, WarnRuleUpdateDTO dto, LoginUser loginUser);

    void toggleRule(Long id, LoginUser loginUser);

    Map<String, Object> executeScan(Long taskId, LoginUser loginUser);

    List<WarnTicketVO> getTicketList(Long taskId, String warnLevel, String status, Integer isUpgraded, LoginUser loginUser);

    WarnTicketVO getTicketDetail(Long id, LoginUser loginUser);

    void dispatchTicket(Long id, WarnTicketDispatchDTO dto, LoginUser loginUser);

    void submitFeedback(Long id, WarnFeedbackDTO dto, LoginUser loginUser);

    void handleTicket(Long id, WarnHandleDTO dto, LoginUser loginUser);
}
