package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSpeedThresholdReq {
    @NotNull
    @Min(1)
    @Max(12)
    private Integer windLevel;

    @NotNull
    @Min(0)
    @Max(150)
    private Integer passengerSpeedLimit;

    @NotNull
    @Min(0)
    @Max(150)
    private Integer freightSpeedLimit;

    @NotNull
    @Min(0)
    @Max(150)
    private Integer dangerousGoodsSpeedLimit;
}
