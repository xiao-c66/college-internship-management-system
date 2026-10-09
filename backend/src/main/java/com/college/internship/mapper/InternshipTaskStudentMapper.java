package com.college.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.internship.entity.InternshipTaskStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InternshipTaskStudentMapper extends BaseMapper<InternshipTaskStudent> {

    @Update("UPDATE internship_task_student SET is_deleted = 0, student_number=#{studentNumber}, student_name=#{studentName}, class_id=#{classId} WHERE task_id = #{taskId} AND student_id = #{studentId} AND is_deleted = 1")
    int restoreDeletedStudent(InternshipTaskStudent student);
}
