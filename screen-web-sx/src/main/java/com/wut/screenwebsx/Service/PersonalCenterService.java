package com.wut.screenwebsx.Service;


import com.wut.screencommonsx.Request.FeedbackRequest;
import com.wut.screencommonsx.Response.ApiResponse;

/**
 * 2. 个人中心服务接口
 */
public interface PersonalCenterService {
    // 2.1 提交问题反馈
    ApiResponse<?> submitFeedback(FeedbackRequest request, String phone);
}