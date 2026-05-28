package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateControlPlanReq {
    @NotNull
    private Long timestamp;

    @NotNull
    @Size(min = 1, max = 128)
    private String segment;

    @Min(0)
    @Max(12)
    private Integer realtimeWindLevel;

    @Min(0)
    @Max(12)
    private Integer forecastMaxWindLevel;

    @Min(0)
    private Double actualWindSpeedMs;

    @Min(0)
    private Double forecastMaxWindSpeed2hMs;

    private List<Double> forecastWindSpeedSeriesMs;

    /**
     * 是否为“预测窗口已更新”场景。
     * false 时，若仅发生实时风速短时回落，可维持原方案等待下一次 5 分钟预测更新。
     */
    private Boolean forecastWindowUpdated;

    @Min(1)
    @Max(2)
    private Integer direction;

    @Min(1)
    @Max(24)
    private Integer durationHours;

    @AssertTrue(message = "realtimeWindLevel/forecastMaxWindLevel 与 actualWindSpeedMs/forecastMaxWindSpeed2hMs 至少提供一组")
    public boolean isWindInputValid() {
        boolean hasLevelPair = realtimeWindLevel != null && forecastMaxWindLevel != null;
        boolean hasSpeedPair = actualWindSpeedMs != null && (
                forecastMaxWindSpeed2hMs != null
                        || (forecastWindSpeedSeriesMs != null && !forecastWindSpeedSeriesMs.isEmpty())
        );
        return hasLevelPair || hasSpeedPair;
    }
}
