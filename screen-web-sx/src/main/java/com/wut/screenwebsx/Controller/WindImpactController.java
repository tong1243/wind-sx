package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.Wind.UpdateSpeedThresholdValueReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindControlCenterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
/**
 * 4.2 大风时空影响分析接口。
 */
public class WindImpactController {
    private final WindControlCenterService windControlCenterService;

    public WindImpactController(WindControlCenterService windControlCenterService) {
        this.windControlCenterService = windControlCenterService;
    }

    /**
     * 获取风区分段可视化信息（实时/预测/72小时最大）。
     */
    @GetMapping("/wind-sections")
    public DefaultDataResp listWindSections(@RequestParam("timestamp") long timestamp,
                                            @RequestParam(value = "mode", required = false) String mode) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind sections",
                windControlCenterService.getWindVisualization(timestamp, mode)
        );
    }

    /**
     * 获取风力等级限速阈值配置。
     */
    @GetMapping("/wind-speed-thresholds")
    public DefaultDataResp listWindSpeedThresholds() {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind speed thresholds",
                windControlCenterService.getSpeedThresholds()
        );
    }

    /**
     * 按风力等级更新限速阈值。
     */
    @PutMapping("/wind-speed-thresholds/{windLevel}")
    public DefaultDataResp updateWindSpeedThreshold(@PathVariable("windLevel") int windLevel,
                                                    @Valid @RequestBody UpdateSpeedThresholdValueReq req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("windLevel", windLevel);
        body.put("passengerSpeedLimit", req.getPassengerSpeedLimit());
        body.put("freightSpeedLimit", req.getFreightSpeedLimit());
        body.put("dangerousGoodsSpeedLimit", req.getDangerousGoodsSpeedLimit());
        return ModelTransformUtil.getDefaultDataInstance(
                "wind speed threshold updated",
                windControlCenterService.updateSpeedThreshold(body)
        );
    }

    /**
     * 获取风力时空影响评估结果。
     */
    @GetMapping("/wind-impacts/spatiotemporal")
    public DefaultDataResp getWindImpactsSpatiotemporal(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind impact spatiotemporal",
                windControlCenterService.evaluateSpatiotemporalImpact(timestamp)
        );
    }

    /**
     * 查询风观测/历史/预测数据。
     */
    @GetMapping("/wind-observations")
    public DefaultDataResp listWindObservations(@RequestParam("timestamp") long timestamp,
                                                @RequestParam(value = "period", required = false) String period,
                                                @RequestParam(value = "direction", required = false) String direction) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind observations",
                windControlCenterService.queryWindData(timestamp, period, direction)
        );
    }

    /**
     * 获取阻断时长预测。
     */
    @GetMapping("/block-duration-forecasts")
    public DefaultDataResp listBlockDurationForecasts(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "block duration forecast",
                windControlCenterService.predictBlockDuration(timestamp)
        );
    }
}
