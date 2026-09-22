package com.college.internship.service;

import com.college.internship.dto.WeeklyReportReviewDTO;
import com.college.internship.dto.WeeklyReportSaveDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.WeeklyMonitorSummaryVO;
import com.college.internship.vo.WeeklyReportDetailVO;
import com.college.internship.vo.WeeklyReportVO;

import java.util.List;

/**
 * 阶段6 实习周报核心业务服务接口 (API-056 ~ API-059)
 */
public interface IWeeklyReportService {

    /**
     * API-058 学生撰写/暂存草稿/正式提交周报
     */
    WeeklyReportVO saveOrSubmitReport(WeeklyReportSaveDTO dto, LoginUser loginUser);

    /**
     * API-056 查询周报列表 (支持行级数据权限隔离与状态过滤)
     */
    List<WeeklyReportVO> listReports(Long taskId, Long studentId, String status, LoginUser loginUser);

    /**
     * API-057 获取周报详情与版本历史快照
     */
    WeeklyReportDetailVO getReportDetail(Long id, LoginUser loginUser);

    /**
     * API-059 指导教师批阅或退回周报
     */
    WeeklyReportDetailVO reviewReport(Long id, WeeklyReportReviewDTO dto, LoginUser loginUser);

    /**
     * 院系/校级周报过程管理与提交率监控看板
     */
    WeeklyMonitorSummaryVO getMonitorSummary(Long taskId, Long deptId, LoginUser loginUser);
}
