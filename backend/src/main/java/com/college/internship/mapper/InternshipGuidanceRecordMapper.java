package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.InternshipGuidanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InternshipGuidanceRecordMapper extends BaseMapper<InternshipGuidanceRecord> {

    /**
     * API-067 CAS 条件更新在岗反馈，防止并发重复提交与覆盖
     * 只有处于 UNCONFIRMED 状态且属于当前学生时才允许更新
     */
    @Update("UPDATE internship_guidance_record " +
            "SET student_feedback = #{feedback}, " +
            "    feedback_time = #{feedbackTime}, " +
            "    feedback_status = 'CONFIRMED', " +
            "    update_time = NOW() " +
            "WHERE id = #{id} " +
            "  AND student_id = #{studentId} " +
            "  AND feedback_status = 'UNCONFIRMED' " +
            "  AND is_deleted = 0")
    int updateFeedbackWithCondition(@Param("id") Long id,
                                   @Param("studentId") Long studentId,
                                   @Param("feedback") String feedback,
                                   @Param("feedbackTime") LocalDateTime feedbackTime);
}
