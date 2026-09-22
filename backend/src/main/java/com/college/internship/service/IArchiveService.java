package com.college.internship.service;

import com.college.internship.dto.ArchiveUnlockDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.ArchivePrecheckVO;
import com.college.internship.vo.ArchiveVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 实习电子卷宗归档、锁定与特批解锁业务服务接口 (API-098 ~ API-102)
 */
public interface IArchiveService {

    ArchivePrecheckVO precheckArchive(Long taskId, Long studentId, LoginUser loginUser);

    Long freezeArchive(Long taskId, Long studentId, LoginUser loginUser);

    List<ArchiveVO> getArchiveList(Long taskId, Long deptId, String academicYear, String status, LoginUser loginUser);

    ArchiveVO getArchiveDetail(Long id, LoginUser loginUser);

    void exportArchiveBundle(Long id, HttpServletResponse response, LoginUser loginUser);

    void unlockArchive(Long id, ArchiveUnlockDTO dto, LoginUser loginUser);

    void checkWriteProtection(Long taskId, Long studentId);
}
