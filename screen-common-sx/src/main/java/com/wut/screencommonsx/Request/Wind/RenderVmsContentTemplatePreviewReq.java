package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * VMS 模板渲染预览请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RenderVmsContentTemplatePreviewReq {
    @NotBlank
    private String templateCode;

    private Map<String, String> variables;
}

