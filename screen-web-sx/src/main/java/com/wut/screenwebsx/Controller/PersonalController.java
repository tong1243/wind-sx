package com.wut.screenwebsx.Controller;


import com.wut.screencommonsx.Request.FeedbackRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.PersonalCenterService;
import com.wut.screenwebsx.Service.UserNoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2. 个人中心控制器
 */
@RestController
@RequestMapping("/api/personal-center")
@RequiredArgsConstructor
public class PersonalController {
    private final PersonalCenterService personalCenterService;
    private final UserNoticeService userNoticeService;

    // 2.1 提交问题反馈
    @PostMapping("/feedback")
    public ApiResponse<?> submitFeedback(@Valid @RequestBody FeedbackRequest request, Authentication authentication) {
        String phone = authentication.getName();
        return personalCenterService.submitFeedback(request, phone);
    }

    // 2.2 消息通知列表
    @GetMapping("/notices")
    public ApiResponse<?> listMyNotices(Authentication authentication,
                                        @RequestParam(defaultValue = "1") long pageNo,
                                        @RequestParam(defaultValue = "10") long pageSize,
                                        @RequestParam(required = false) Integer isRead) {
        String phone = authentication.getName();
        return userNoticeService.listMyNotices(phone, pageNo, pageSize, isRead);
    }

    // 2.3 单条消息标记已读
    @PostMapping("/notices/{noticeId}/read")
    public ApiResponse<?> markNoticeRead(Authentication authentication, @PathVariable Long noticeId) {
        String phone = authentication.getName();
        return userNoticeService.markNoticeRead(phone, noticeId);
    }

    // 2.4 一键已读
    @PostMapping("/notices/read-all")
    public ApiResponse<?> markAllNoticeRead(Authentication authentication) {
        String phone = authentication.getName();
        return userNoticeService.markAllNoticeRead(phone);
    }

    // 2.5 删除单条消息
    @DeleteMapping("/notices/{noticeId}")
    public ApiResponse<?> deleteNotice(Authentication authentication, @PathVariable Long noticeId) {
        String phone = authentication.getName();
        return userNoticeService.deleteNotice(phone, noticeId);
    }
}
