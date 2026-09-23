package com.college.internship.service;

import com.college.internship.dto.NoticeCreateDTO;
import com.college.internship.dto.NoticeUpdateDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.SysNoticeVO;

import java.util.List;

/**
 * 全局教学通知公告业务接口 (API-112 ~ API-115)
 */
public interface ISysNoticeService {

    /**
     * 查询当前用户可见的通知公告列表 (API-112)
     *
     * @param pageNum    页码 (可选)
     * @param pageSize   每页条数 (可选)
     * @param status     状态过滤 (0-关闭, 1-发布, 可选)
     * @param noticeType 类型过滤 (NOTICE, ANNOUNCE, 可选)
     * @param loginUser  当前登录用户
     * @return 通知列表
     */
    List<SysNoticeVO> getNoticeList(Integer pageNum, Integer pageSize, Integer status, String noticeType, LoginUser loginUser);

    /**
     * 查询通知公告详情并幂等记录已读 (API-113)
     *
     * @param id        公告ID
     * @param loginUser 当前登录用户
     * @return 公告详情视图
     */
    SysNoticeVO getNoticeDetail(Long id, LoginUser loginUser);

    /**
     * 人工发布通知公告 (API-114)
     *
     * @param dto       发布参数
     * @param loginUser 当前登录用户
     * @return 发布成功的公告视图
     */
    SysNoticeVO createNotice(NoticeCreateDTO dto, LoginUser loginUser);

    /**
     * 修改/撤回通知公告 (API-115)
     *
     * @param id        公告ID
     * @param dto       修改参数
     * @param loginUser 当前登录用户
     * @return 修改后的公告视图
     */
    SysNoticeVO updateNotice(Long id, NoticeUpdateDTO dto, LoginUser loginUser);
}
