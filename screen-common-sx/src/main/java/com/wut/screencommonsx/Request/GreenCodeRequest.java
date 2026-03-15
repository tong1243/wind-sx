package com.wut.screencommonsx.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 4.1 生成绿码/4.2 创建通行凭证请求DTO
 */
@Data
public class GreenCodeRequest {
    @NotBlank(message = "起点不能为空")
    private String startPoint;

    @NotBlank(message = "终点不能为空")
    private String endPoint;

    @NotBlank(message = "出行时段不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}~\\d{2}:\\d{2}$", 
             message = "出行时段格式：yyyy-MM-dd HH:mm~HH:mm")
    private String travelTimeSlot;

    @NotBlank(message = "车型不能为空")
    private String vehicleType;

    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    // 可选：货物重量（吨）
    private String cargoWeight;
}