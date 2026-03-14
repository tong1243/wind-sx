package com.wut.screencommonsx.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 2.1 问题反馈请求DTO
 */
@Data
public class FeedbackRequest {
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 500, message = "反馈内容最多500字")
    private String content;

    // 可选：反馈类型（建议/投诉/故障）
    private String type;

    // 可选：联系方式
    private String contact;
}