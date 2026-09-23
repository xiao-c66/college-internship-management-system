import request from '@/utils/request';

export interface SysNoticeVO {
  id: number;
  dedupKey: string;
  noticeTitle: string;
  noticeType: 'NOTICE' | 'ANNOUNCE' | string;
  noticeContent: string;
  targetScope: 'ALL' | 'DEPT' | string;
  targetDeptId?: number | null;
  status: number; // 0-关闭/撤回, 1-正常发布
  publisherId: number;
  publisherName: string;
  publishTime: string;
  isRead?: boolean;
  readTime?: string | null;
}

export interface NoticeCreateDTO {
  dedupKey: string;
  noticeTitle: string;
  noticeType: string;
  noticeContent: string;
  targetScope: string;
  targetDeptId?: number | null;
}

export interface NoticeUpdateDTO {
  noticeTitle?: string;
  noticeType?: string;
  noticeContent?: string;
  targetScope?: string;
  targetDeptId?: number | null;
  status?: number;
}

/**
 * 查询通知公告列表 (API-112)
 */
export function getNotices(params?: {
  pageNum?: number;
  pageSize?: number;
  status?: number;
  noticeType?: string;
}) {
  return request.get<any, { code: number; message: string; data: SysNoticeVO[] }>('/system/notices', { params });
}

/**
 * 查询通知详情并记录已读 (API-113)
 */
export function getNoticeDetail(id: number) {
  return request.get<any, { code: number; message: string; data: SysNoticeVO }>(`/system/notices/${id}`);
}

/**
 * 人工发布通知公告 (API-114)
 */
export function createNotice(data: NoticeCreateDTO) {
  return request.post<any, { code: number; message: string; data: SysNoticeVO }>('/system/notices', data);
}

/**
 * 修改/撤回通知公告 (API-115)
 */
export function updateNotice(id: number, data: NoticeUpdateDTO) {
  return request.put<any, { code: number; message: string; data: SysNoticeVO }>(`/system/notices/${id}`, data);
}
