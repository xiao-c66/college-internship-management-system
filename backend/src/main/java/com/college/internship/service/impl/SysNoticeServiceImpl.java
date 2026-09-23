package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.dto.NoticeCreateDTO;
import com.college.internship.dto.NoticeUpdateDTO;
import com.college.internship.entity.SysNotice;
import com.college.internship.entity.SysNoticeRead;
import com.college.internship.mapper.SysNoticeMapper;
import com.college.internship.mapper.SysNoticeReadMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysNoticeService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.vo.SysNoticeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局教学通知公告业务实现 (API-112 ~ API-115)
 * 严格落实：
 * 1. 权限边界与可见性过滤 (SYS_ADMIN 全校, DEPT_ADMIN/TEACHER/STUDENT 仅限 ALL 或本院系)；
 * 2. 详情获取时幂等写入 sys_notice_read 阅读状态；
 * 3. 业务防重键 dedup_key 唯一索引防重，统一拦截并友好提示 400，严禁模糊去重；
 * 4. 富文本 noticeContent 在入库前强制经过 Jsoup 白名单深度清洗，过滤 XSS 脚本；
 * 5. 发布与修改/撤回全量写入 sys_operation_log 操作审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl implements ISysNoticeService {

    private final SysNoticeMapper sysNoticeMapper;
    private final SysNoticeReadMapper sysNoticeReadMapper;
    private final ISysOperationLogService operationLogService;

    @Override
    public List<SysNoticeVO> getNoticeList(Integer pageNum, Integer pageSize, Integer status, String noticeType, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "未登录认证");
        }

        LambdaQueryWrapper<SysNotice> qw = new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getIsDeleted, 0);

        String userType = loginUser.getUserType();
        // 权限可见性过滤
        if (!"SYS_ADMIN".equals(userType)) {
            Long deptId = loginUser.getDeptId();
            qw.and(w -> {
                w.eq(SysNotice::getTargetScope, "ALL");
                if (deptId != null) {
                    w.or(w2 -> w2.eq(SysNotice::getTargetScope, "DEPT").eq(SysNotice::getTargetDeptId, deptId));
                }
            });

            // 教师和学生在未指定 status 条件时，默认仅可见正常发布的通知 (status = 1)
            if (("STUDENT".equals(userType) || "TEACHER".equals(userType)) && status == null) {
                qw.eq(SysNotice::getStatus, 1);
            }
        }

        if (status != null) {
            qw.eq(SysNotice::getStatus, status);
        }
        if (StringUtils.hasText(noticeType)) {
            qw.eq(SysNotice::getNoticeType, noticeType.trim().toUpperCase());
        }

        qw.orderByDesc(SysNotice::getPublishTime)
                .orderByDesc(SysNotice::getId);

        List<SysNotice> notices = sysNoticeMapper.selectList(qw);
        if (notices.isEmpty()) {
            return Collections.emptyList();
        }

        // 分页裁剪 (若提供分页参数)
        if (pageNum != null && pageSize != null && pageNum > 0 && pageSize > 0) {
            int fromIndex = (pageNum - 1) * pageSize;
            if (fromIndex >= notices.size()) {
                return Collections.emptyList();
            }
            int toIndex = Math.min(fromIndex + pageSize, notices.size());
            notices = notices.subList(fromIndex, toIndex);
        }

        // 查询当前登录用户的阅读记录映射
        List<SysNoticeRead> reads = sysNoticeReadMapper.selectList(
                new LambdaQueryWrapper<SysNoticeRead>()
                        .eq(SysNoticeRead::getUserId, loginUser.getUserId())
        );
        Map<Long, LocalDateTime> readMap = reads.stream()
                .collect(Collectors.toMap(SysNoticeRead::getNoticeId, SysNoticeRead::getReadTime, (v1, v2) -> v1));

        return notices.stream()
                .map(n -> toVO(n, readMap.get(n.getId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysNoticeVO getNoticeDetail(Long id, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "未登录认证");
        }

        // 1. 查询通知记录
        SysNotice notice = sysNoticeMapper.selectOne(
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getId, id)
                        .eq(SysNotice::getIsDeleted, 0)
        );
        if (notice == null) {
            throw new BusinessException(404, "通知公告不存在或已被删除");
        }

        // 2. 越权范围校验：非超管若查阅本院系限定通知，必须匹配自身院系ID
        String userType = loginUser.getUserType();
        if (!"SYS_ADMIN".equals(userType)) {
            if ("DEPT".equalsIgnoreCase(notice.getTargetScope())) {
                Long userDeptId = loginUser.getDeptId();
                if (notice.getTargetDeptId() == null || !notice.getTargetDeptId().equals(userDeptId)) {
                    throw new BusinessException(403, "权限不足：无权访问其他院系的通知公告");
                }
            }
        }

        // 3. 幂等记录已读时间戳
        SysNoticeRead readRecord = sysNoticeReadMapper.selectOne(
                new LambdaQueryWrapper<SysNoticeRead>()
                        .eq(SysNoticeRead::getNoticeId, id)
                        .eq(SysNoticeRead::getUserId, loginUser.getUserId())
        );

        if (readRecord == null) {
            SysNoticeRead newRead = SysNoticeRead.builder()
                    .noticeId(id)
                    .userId(loginUser.getUserId())
                    .readTime(LocalDateTime.now())
                    .build();
            try {
                sysNoticeReadMapper.insert(newRead);
                readRecord = newRead;
            } catch (DuplicateKeyException e) {
                // 并发插入冲突时保持幂等，查询已有记录
                readRecord = sysNoticeReadMapper.selectOne(
                        new LambdaQueryWrapper<SysNoticeRead>()
                                .eq(SysNoticeRead::getNoticeId, id)
                                .eq(SysNoticeRead::getUserId, loginUser.getUserId())
                );
            }
        }

        LocalDateTime readTime = readRecord != null ? readRecord.getReadTime() : LocalDateTime.now();
        return toVO(notice, readTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysNoticeVO createNotice(NoticeCreateDTO dto, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "未登录认证");
        }

        String userType = loginUser.getUserType();
        // 1. 角色硬权限限定：仅限 SYS_ADMIN 与 DEPT_ADMIN
        if (!"SYS_ADMIN".equals(userType) && !"DEPT_ADMIN".equals(userType)) {
            throw new BusinessException(403, "权限不足：仅管理员和院系负责人允许发布通知公告");
        }

        String targetScope = dto.getTargetScope() != null ? dto.getTargetScope().trim().toUpperCase() : "DEPT";
        Long targetDeptId = dto.getTargetDeptId();

        // 2. 院系负责人发布范围强制限制
        if ("DEPT_ADMIN".equals(userType)) {
            if ("ALL".equalsIgnoreCase(dto.getTargetScope())) {
                throw new BusinessException(400, "院系管理员仅允许发布本院系通知公告，禁止发布全校范围通知");
            }
            targetScope = "DEPT";
            targetDeptId = loginUser.getDeptId();
        } else {
            // SYS_ADMIN
            if ("DEPT".equalsIgnoreCase(targetScope)) {
                if (targetDeptId == null) {
                    throw new BusinessException(400, "院系范围通知必须指定目标院系ID");
                }
            } else {
                targetScope = "ALL";
                targetDeptId = null;
            }
        }

        // 3. 业务防重键唯一性预检 (uk_notice_dedup)
        String dedupKey = dto.getDedupKey().trim();
        Long existingCount = sysNoticeMapper.selectCount(
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getDedupKey, dedupKey)
                        .eq(SysNotice::getIsDeleted, 0)
        );
        if (existingCount != null && existingCount > 0) {
            throw new BusinessException(400, "重复的业务防重键: " + dedupKey);
        }

        // 4. 类型校验
        String noticeType = dto.getNoticeType() != null ? dto.getNoticeType().trim().toUpperCase() : "NOTICE";
        if (!"NOTICE".equals(noticeType) && !"ANNOUNCE".equals(noticeType)) {
            noticeType = "NOTICE";
        }

        // 5. 服务端 Jsoup 白名单深度清洗富文本正文，严防 XSS
        String cleanedContent = Jsoup.clean(dto.getNoticeContent(), Safelist.relaxed());

        String publisherName = StringUtils.hasText(loginUser.getRealName()) ? loginUser.getRealName() : loginUser.getUsername();

        SysNotice notice = SysNotice.builder()
                .dedupKey(dedupKey)
                .noticeTitle(dto.getNoticeTitle().trim())
                .noticeType(noticeType)
                .noticeContent(cleanedContent)
                .targetScope(targetScope)
                .targetDeptId(targetDeptId)
                .status(1) // 正常发布
                .publisherId(loginUser.getUserId())
                .publisherName(publisherName)
                .publishTime(LocalDateTime.now())
                .isDeleted(0)
                .build();

        try {
            sysNoticeMapper.insert(notice);
        } catch (DuplicateKeyException e) {
            log.warn("通知发布遇到并发重复防重键 uk_notice_dedup 冲突: {}", dedupKey, e);
            throw new BusinessException(400, "重复的业务防重键: " + dedupKey);
        }

        // 6. 记录操作审计日志
        operationLogService.logOperation(
                "通知公告-发布",
                "NOTICE",
                "SysNoticeServiceImpl.createNotice",
                "MANUAL",
                loginUser.getUserId(),
                loginUser.getUsername(),
                "/api/v1/system/notices",
                "127.0.0.1",
                String.format("{\"dedupKey\":\"%s\",\"noticeTitle\":\"%s\",\"targetScope\":\"%s\",\"targetDeptId\":%s}",
                        dedupKey, notice.getNoticeTitle(), targetScope, String.valueOf(targetDeptId)),
                String.format("{\"id\":%d,\"status\":1}", notice.getId()),
                1,
                null
        );

        return toVO(notice, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysNoticeVO updateNotice(Long id, NoticeUpdateDTO dto, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "未登录认证");
        }

        String userType = loginUser.getUserType();
        if (!"SYS_ADMIN".equals(userType) && !"DEPT_ADMIN".equals(userType)) {
            throw new BusinessException(403, "权限不足：仅管理员和院系负责人允许修改通知公告");
        }

        // 1. 查询待修改通知
        SysNotice notice = sysNoticeMapper.selectOne(
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getId, id)
                        .eq(SysNotice::getIsDeleted, 0)
        );
        if (notice == null) {
            throw new BusinessException(404, "通知公告不存在或已被删除");
        }

        // 2. 院系负责人修改权限隔离：只能修改本院系或本人发布的通知
        if ("DEPT_ADMIN".equals(userType)) {
            boolean isOwner = loginUser.getUserId().equals(notice.getPublisherId());
            boolean isSameDept = "DEPT".equalsIgnoreCase(notice.getTargetScope())
                    && loginUser.getDeptId() != null
                    && loginUser.getDeptId().equals(notice.getTargetDeptId());

            if (!isOwner && !isSameDept) {
                throw new BusinessException(403, "权限不足：无权修改其他院系或全校通知公告");
            }

            if (dto.getTargetScope() != null && "ALL".equalsIgnoreCase(dto.getTargetScope().trim())) {
                throw new BusinessException(400, "院系管理员无权将通知发布范围扩大至全校范围");
            }
        }

        // 3. 更新字段
        if (StringUtils.hasText(dto.getNoticeTitle())) {
            notice.setNoticeTitle(dto.getNoticeTitle().trim());
        }
        if (StringUtils.hasText(dto.getNoticeType())) {
            String nt = dto.getNoticeType().trim().toUpperCase();
            if ("NOTICE".equals(nt) || "ANNOUNCE".equals(nt)) {
                notice.setNoticeType(nt);
            }
        }
        if (dto.getNoticeContent() != null) {
            notice.setNoticeContent(Jsoup.clean(dto.getNoticeContent(), Safelist.relaxed()));
        }
        if (dto.getStatus() != null) {
            if (dto.getStatus() != 0 && dto.getStatus() != 1) {
                throw new BusinessException(400, "状态参数不合法，仅支持 0(关闭/撤回) 或 1(发布)");
            }
            notice.setStatus(dto.getStatus());
        }

        if ("SYS_ADMIN".equals(userType)) {
            if (StringUtils.hasText(dto.getTargetScope())) {
                String sc = dto.getTargetScope().trim().toUpperCase();
                notice.setTargetScope(sc);
                if ("ALL".equals(sc)) {
                    notice.setTargetDeptId(null);
                } else if (dto.getTargetDeptId() != null) {
                    notice.setTargetDeptId(dto.getTargetDeptId());
                }
            } else if (dto.getTargetDeptId() != null) {
                notice.setTargetDeptId(dto.getTargetDeptId());
            }
        }

        sysNoticeMapper.updateById(notice);

        // 4. 记录修改审计日志
        operationLogService.logOperation(
                "通知公告-修改/撤回",
                "NOTICE",
                "SysNoticeServiceImpl.updateNotice",
                "MANUAL",
                loginUser.getUserId(),
                loginUser.getUsername(),
                "/api/v1/system/notices/" + id,
                "127.0.0.1",
                String.format("{\"id\":%d,\"status\":%s}", id, String.valueOf(dto.getStatus())),
                String.format("{\"id\":%d,\"status\":%d}", notice.getId(), notice.getStatus()),
                1,
                null
        );

        // 获取阅读状态
        SysNoticeRead readRecord = sysNoticeReadMapper.selectOne(
                new LambdaQueryWrapper<SysNoticeRead>()
                        .eq(SysNoticeRead::getNoticeId, id)
                        .eq(SysNoticeRead::getUserId, loginUser.getUserId())
        );

        return toVO(notice, readRecord != null ? readRecord.getReadTime() : null);
    }

    private SysNoticeVO toVO(SysNotice notice, LocalDateTime readTime) {
        return SysNoticeVO.builder()
                .id(notice.getId())
                .dedupKey(notice.getDedupKey())
                .noticeTitle(notice.getNoticeTitle())
                .noticeType(notice.getNoticeType())
                .noticeContent(notice.getNoticeContent())
                .targetScope(notice.getTargetScope())
                .targetDeptId(notice.getTargetDeptId())
                .status(notice.getStatus())
                .publisherId(notice.getPublisherId())
                .publisherName(notice.getPublisherName())
                .publishTime(notice.getPublishTime())
                .isRead(readTime != null)
                .readTime(readTime)
                .build();
    }
}
