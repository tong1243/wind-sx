package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Response.ApiResponse;

public interface UserNoticeService {
    ApiResponse<?> listMyNotices(String phone, long pageNo, long pageSize, Integer isRead);

    ApiResponse<?> markNoticeRead(String phone, Long noticeId);

    ApiResponse<?> markAllNoticeRead(String phone);

    ApiResponse<?> deleteNotice(String phone, Long noticeId);
}
