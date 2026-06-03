package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新 VMS 模板请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateVmsContentTemplateReq {
    @NotBlank
    @Size(max = 16)
    private String controlLevel;

    @NotBlank
    @Size(max = 32)
    private String publishPosition;

    @NotBlank
    @Size(max = 16)
    private String vehicleType;

    @NotBlank
    @Size(max = 255)
    private String templateText;

    private Object templateGraphicJson;

    @Min(0)
    @Max(999999)
    private Integer sortNo;

    @Min(0)
    @Max(1)
    private Integer isEnabled;
}
