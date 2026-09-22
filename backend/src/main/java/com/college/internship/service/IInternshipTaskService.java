package com.college.internship.service;

import com.college.internship.dto.TaskCreateDTO;
import com.college.internship.dto.TaskUpdateDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.TaskVO;

import java.util.List;

/**
 * 实习批次任务管理服务接口
 */
public interface IInternshipTaskService extends IBaseService {

    /**
     * 创建实习任务 (严格校验五项权重合计等于100%)
     */
    TaskVO createTask(TaskCreateDTO dto, LoginUser loginUser);

    /**
     * 修改实习任务
     */
    TaskVO updateTask(Long id, TaskUpdateDTO dto, LoginUser loginUser);

    /**
     * 正式发布实习任务 (圈定生成学生参与范围)
     */
    void publishTask(Long id, LoginUser loginUser);

    /**
     * 删除实习任务 (仅限草稿状态且未产生业务数据)
     */
    void deleteTask(Long id, LoginUser loginUser);

    /**
     * 查询任务详情 (受数据范围隔离)
     */
    TaskVO getTaskDetail(Long id, LoginUser loginUser);

    /**
     * 分页/条件查询任务列表
     */
    List<TaskVO> listTasks(Long deptId, String status, LoginUser loginUser);

    /**
     * 查询任务圈定学生名单与指导教师分配状态 (ASSIGN-001/003)
     */
    List<com.college.internship.vo.TaskStudentVO> getTaskStudents(Long taskId, Long classId, Long teacherId, String keyword, LoginUser loginUser);

    /**
     * 获取任务所在院系可指派的指导教师列表
     */
    List<com.college.internship.vo.TeacherSimpleVO> getAvailableTeachers(Long taskId, LoginUser loginUser);

    /**
     * 单个或批量指派/调整指导教师 (ASSIGN-001 ~ ASSIGN-003)
     */
    void assignTeacher(Long taskId, com.college.internship.dto.TeacherAssignDTO dto, LoginUser loginUser);
}
