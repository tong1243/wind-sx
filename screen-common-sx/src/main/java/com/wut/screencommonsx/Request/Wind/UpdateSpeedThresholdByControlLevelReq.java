package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSpeedThresholdByControlLevelReq {
    @NotBlank
    @Size(max = 32)
    private String windLevelDesc;

    @Min(0)
    @Max(150)
    private Integer passengerSpeedLimit;

    @Min(0)
    @Max(150)
    private Integer freightSpeedLimit;
}

