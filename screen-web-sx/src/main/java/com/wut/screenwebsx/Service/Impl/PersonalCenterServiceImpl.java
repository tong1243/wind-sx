package com.wut.screenwebsx.Service.Impl;


import com.wut.screencommonsx.Request.FeedbackRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.PersonalCenterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 个人中心服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalCenterServiceImpl implements PersonalCenterService {
    @Override
    public ApiResponse<?> submitFeedback(FeedbackRequest request, String phone) {
        // Mock保存反馈（实际项目写入数据库/消息队列）
        log.info("用户{}提交反馈：类型={}, 内容={}, 联系方式={}", 
                phone, request.getType(), request.getContent(), request.getContact());
        return ApiResponse.success("反馈提交成功，我们会尽快处理", null);
    }
}