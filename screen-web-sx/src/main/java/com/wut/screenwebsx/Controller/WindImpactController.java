package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.Wind.UpdateSpeedThresholdValueReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindControlWindImpactService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 4.2 大风时空影响模块接口。
 *
 * 提供风区可视化、风力限速阈值维护、时空影响研判、
 * 风观测/历史/预测查询以及阻断时长预测能力。
 */
@RestController
@RequestMapping("/api/v1")
public class WindImpactController {
    private final WindControlWindImpactService windImpactService;

    /**
     * 构造控制器并注入 4.2 业务服务。
     *
     * @param windImpactService 大风时空影响业务服务
     */
    public WindImpactController(WindControlWindImpactService windImpactService) {
        this.windImpactService = windImpactService;
    }

    /**
     * 查询全线风力可视化数据。
     *
     * mode 支持 real/forecast/max4h 三类视图。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param mode 可选视图模式
     * @return 风区可视化结果
     */
    @GetMapping("/wind-sections")
    public DefaultDataResp listWindSections(@RequestParam("timestamp") long timestamp,
                                            @RequestParam(value = "mode", required = false) String mode) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind sections",
                windImpactService.getWindVisualization(timestamp, mode)
        );
    }

    /**
     * 查询风力限速阈值表。
     *
     * 返回不同风级对应的客车、货车和危化品车辆限速。
     *
     * @return 风级阈值列表
     */
    @GetMapping("/wind-speed-thresholds")
    public DefaultDataResp listWindSpeedThresholds() {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind speed thresholds",
                windImpactService.getSpeedThresholds()
        );
    }

    /**
     * 更新指定风级限速阈值。
     *
     * 仅更新请求体中提供的字段，未提供字段保持原值。
     *
     * @param windLevel 风力等级（1-12）
     * @param req 阈值更新参数
     * @return 更新后的阈值记录
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
                windImpactService.updateSpeedThreshold(body)
        );
    }

    /**
     * 查询风力时空影响研判结果。
     *
     * direction 取值规则：1=下行，2=上行；不传则返回双向数据。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param periodType 可选时段类型
     * @param direction 可选方向（1=下行，2=上行）
     * @return 时空影响研判结果
     */
    @GetMapping("/wind-impacts/spatiotemporal")
    public DefaultDataResp getWindImpactsSpatiotemporal(@RequestParam("timestamp") long timestamp,
                                                        @RequestParam(value = "periodType", required = false) String periodType,
                                                        @RequestParam(value = "direction", required = false) Integer direction) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind impact spatiotemporal",
                windImpactService.evaluateSpatiotemporalImpact(timestamp, periodType, direction)
        );
    }

    /**
     * 查询风观测、历史或预测数据。
     *
     * period 控制时间窗口类型，direction 取值为 1/2。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param period 可选周期类型
     * @param direction 可选方向（1=下行，2=上行）
     * @return 风数据序列结果
     */
    @GetMapping("/wind-observations")
    public DefaultDataResp listWindObservations(@RequestParam("timestamp") long timestamp,
                                                @RequestParam(value = "period", required = false) String period,
                                                @RequestParam(value = "direction", required = false) Integer direction) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind observations",
                windImpactService.queryWindData(timestamp, period, direction)
        );
    }

    /**
     * 查询阻断时长预测结果。
     *
     * 根据高风风险区段数量估算可能的阻断持续时间。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 阻断时长预测结果
     */
    @GetMapping("/block-duration-forecasts")
    public DefaultDataResp listBlockDurationForecasts(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "block duration forecast",
                windImpactService.predictBlockDuration(timestamp)
        );
    }
}
