package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateControlPlanReq {
    @Min(0)
    @Max(12)
    private Integer minWindLevel;

    @Min(0)
    @Max(12)
    private Integer maxWindLevel;

    @Min(0)
    @Max(150)
    private Integer passengerSpeedLimit;

    @Min(0)
    @Max(150)
    private Integer freightSpeedLimit;

    @Size(max = 255)
    private String description;
}
