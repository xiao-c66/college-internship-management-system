package com.college.internship.service;

import com.college.internship.dto.ApplyChangeAuditDTO;
import com.college.internship.dto.ApplyChangeDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.ApplyChangeVO;

import java.util.List;

/**
 * 实习重大信息变更申请与双级审批服务接口
 */
public interface IInternshipApplyChangeService {

    /**
     * 学生提交实习重大信息变更申请 (APPLY-009 生效锁定后发起)
     */
    ApplyChangeVO submitChange(ApplyChangeDTO dto, LoginUser loginUser);

    /**
     * 指导教师初审
     */
    ApplyChangeVO teacherInitialAudit(Long changeId, ApplyChangeAuditDTO dto, LoginUser loginUser);

    /**
     * 二级院系管理员终审 (通过后在同一事务内原子同步主表数据)
     */
    ApplyChangeVO deptFinalAudit(Long changeId, ApplyChangeAuditDTO dto, LoginUser loginUser);

    /**
     * 获取变更详情 (含审批轨迹)
     */
    ApplyChangeVO getChangeDetail(Long changeId, LoginUser loginUser);

    /**
     * 条件检索变更申请列表 (支持 RBAC 行级角色隔离)
     */
    List<ApplyChangeVO> listChanges(Long applyId, Long taskId, String status, LoginUser loginUser);

    /**
     * 获取指定实习申请当前活跃/处理中的变更申请 (供学生端回显进度)
     */
    ApplyChangeVO getActiveChangeByApplyId(Long applyId, LoginUser loginUser);
}
