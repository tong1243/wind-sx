package com.wut.screenwebsx.Controller;


import com.wut.screencommonsx.Request.FeedbackRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.PersonalCenterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2. 个人中心控制器
 */
@RestController
@RequestMapping("/api/personal-center")
@RequiredArgsConstructor
public class PersonalController {
    private final PersonalCenterService personalCenterService;

    // 2.1 提交问题反馈
    @PostMapping("/feedback")
    public ApiResponse<?> submitFeedback(@Valid @RequestBody FeedbackRequest request, Authentication authentication) {
        String phone = authentication.getName();
        return personalCenterService.submitFeedback(request, phone);
    }
}