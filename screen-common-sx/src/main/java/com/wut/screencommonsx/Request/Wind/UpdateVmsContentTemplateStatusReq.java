package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新 VMS 模板启停状态请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateVmsContentTemplateStatusReq {
    @NotNull
    @Min(0)
    @Max(1)
    private Integer isEnabled;
}

