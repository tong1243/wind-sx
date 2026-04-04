package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateControlPlanReq {
    @NotNull
    private Long timestamp;

    @NotNull
    @Size(min = 1, max = 128)
    private String segment;

    @NotNull
    @Min(0)
    @Max(12)
    private Integer realtimeWindLevel;

    @NotNull
    @Min(0)
    @Max(12)
    private Integer forecastMaxWindLevel;
}
