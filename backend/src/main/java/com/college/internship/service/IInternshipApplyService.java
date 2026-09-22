package com.college.internship.service;

import com.college.internship.dto.ApplyDTO;
import com.college.internship.dto.AuditDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.ApplyVO;
import com.college.internship.vo.AuditHistoryVO;

import java.util.List;

/**
 * 学生实习申报与双级审核核心服务接口
 */
public interface IInternshipApplyService extends IBaseService {

    /**
     * 学生暂存实习申报草稿 (APPLY-006)
     */
    ApplyVO saveDraft(ApplyDTO dto, LoginUser loginUser);

    /**
     * 学生正式提交实习申报 (APPLY-006)
     */
    ApplyVO submitApply(ApplyDTO dto, LoginUser loginUser);

    /**
     * 修改实习申报 (APPLY-009 强防篡改拦截)
     */
    ApplyVO updateApply(Long id, ApplyDTO dto, LoginUser loginUser);

    /**
     * 获取学生当前任务的实习申报及流转状态
     */
    ApplyVO getMyApply(Long taskId, LoginUser loginUser);

    /**
     * 根据ID获取申报详情与审批历史
     */
    ApplyVO getApplyById(Long id, LoginUser loginUser);

    /**
     * 审核中心查询待审/已审申请列表 (分角色隔离数据范围)
     */
    List<ApplyVO> listAppliesForAudit(Long taskId, String status, LoginUser loginUser);

    /**
     * 审核流转：指导教师初审与院系负责人终审 (REVIEW-001 ~ REVIEW-006)
     * 退回时强制校验 auditOpinion.trim().length() >= 5
     */
    void auditApply(Long id, AuditDTO dto, LoginUser loginUser);

    /**
     * 查询某申报的审批历史快照版本
     */
    List<AuditHistoryVO> getAuditHistories(Long applyId, LoginUser loginUser);
}
