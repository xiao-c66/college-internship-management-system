package com.college.internship.service;

import com.college.internship.dto.ScoreAppealDTO;
import com.college.internship.dto.ScoreArbitrateDTO;
import com.college.internship.dto.ScoreSubmitDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.ScoreSummaryVO;

import java.util.List;

/**
 * 实习成绩五维综合评定与异议申诉业务服务接口 (API-091 ~ API-097)
 */
public interface IScoreService {

    Long submitScore(ScoreSubmitDTO dto, LoginUser loginUser);

    List<ScoreSummaryVO> getScoreList(Long taskId, Long deptId, Long majorId, Long classId, String scoreLevel, String status, LoginUser loginUser);

    ScoreSummaryVO getScoreDetail(Long id, LoginUser loginUser);

    ScoreSummaryVO getStudentScore(Long taskId, Long studentId, LoginUser loginUser);

    void auditScore(Long id, LoginUser loginUser);

    void publishScores(Long taskId, LoginUser loginUser);

    Long submitAppeal(ScoreAppealDTO dto, LoginUser loginUser);

    void arbitrateAppeal(Long id, ScoreArbitrateDTO dto, LoginUser loginUser);
}
