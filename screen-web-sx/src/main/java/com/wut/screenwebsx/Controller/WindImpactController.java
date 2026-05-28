package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.Wind.UpdateSpeedThresholdByControlLevelReq;
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
     * mode 支持 real/forecast/max2h/max72h（兼容历史 max4h 入参）。
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
     * 返回不同风级对应的客车、货车限速。
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
     * 按管控等级更新阈值（主入口）。
     *
     * 规则：只允许更严格，不允许更宽松；低等级收紧到高等级后触发方案级联统一。
     *
     * @param controlLevel 被编辑的管控等级（1-5）
     * @param req 阈值更新参数
     * @return 更新结果
     */
    @PutMapping("/wind-speed-thresholds/{controlLevel}")
    public DefaultDataResp updateWindSpeedThreshold(@PathVariable("controlLevel") int controlLevel,
                                                    @Valid @RequestBody UpdateSpeedThresholdByControlLevelReq req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("windLevelDesc", req.getWindLevelDesc());
        body.put("passengerSpeedLimit", req.getPassengerSpeedLimit());
        body.put("freightSpeedLimit", req.getFreightSpeedLimit());
        return ModelTransformUtil.getDefaultDataInstance(
                "wind speed threshold updated by control level",
                windImpactService.updateSpeedThresholdByControlLevel(controlLevel, body)
        );
    }

    /**
     * 按管控等级编辑风力阈值映射（兼容别名入口）。
     *
     * 规则：只允许更严格，不允许更宽松；低等级收紧到高等级后触发方案级联统一。
     *
     * @param controlLevel 被编辑的管控等级（1-5）
     * @param req 编辑参数
     * @return 更新结果
     */
    @PutMapping("/wind-speed-thresholds/by-control-level/{controlLevel}")
    public DefaultDataResp updateWindSpeedThresholdByControlLevel(@PathVariable("controlLevel") int controlLevel,
                                                                  @Valid @RequestBody UpdateSpeedThresholdByControlLevelReq req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("windLevelDesc", req.getWindLevelDesc());
        body.put("passengerSpeedLimit", req.getPassengerSpeedLimit());
        body.put("freightSpeedLimit", req.getFreightSpeedLimit());
        return ModelTransformUtil.getDefaultDataInstance(
                "wind speed threshold updated by control level",
                windImpactService.updateSpeedThresholdByControlLevel(controlLevel, body)
        );
    }

    /**
     * 查询风力时空影响研判结果。
     *
     * direction 取值规则：1=去往哈密方向，2=去往吐鲁番方向；不传则返回双向数据。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param periodType 可选时段类型
     * @param direction 可选方向（1=去往哈密方向，2=去往吐鲁番方向）
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
     * 4.2.3 实时大风时空影响研判。
     *
     * 固定区间：K3178-K3192、K3192-K3197、K3197-K3204；
     * 固定双向：方向1(哈密) + 方向2(吐鲁番)，共 6 组记录。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 时空影响研判结果（实时）
     */
    @GetMapping("/wind-impacts/spatiotemporal/realtime")
    public DefaultDataResp getWindImpactsSpatiotemporalRealtime(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind impact spatiotemporal realtime",
                windImpactService.evaluateSpatiotemporalImpactReal(timestamp)
        );
    }

    /**
     * 4.2.3 未来2小时大风时空影响研判。
     *
     * 固定区间：K3178-K3192、K3192-K3197、K3197-K3204；
     * 固定双向：方向1(哈密) + 方向2(吐鲁番)，共 6 组记录。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 时空影响研判结果（未来2小时）
     */
    @GetMapping("/wind-impacts/spatiotemporal/future2h")
    public DefaultDataResp getWindImpactsSpatiotemporalFuture2h(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind impact spatiotemporal future2h",
                windImpactService.evaluateSpatiotemporalImpactFuture2h(timestamp)
        );
    }

    /**
     * 查询 APP 限速发布数据。
     *
     * periodType 取值：real/future2h/all，direction 取值：1/2。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param periodType 可选时段类型
     * @param direction 可选方向（1=去往哈密方向，2=去往吐鲁番方向）
     * @return APP 限速发布数据
     */
    @GetMapping("/wind-impacts/app-speed-publish")
    public DefaultDataResp getAppSpeedPublish(@RequestParam("timestamp") long timestamp,
                                              @RequestParam(value = "periodType", required = false) String periodType,
                                              @RequestParam(value = "direction", required = false) Integer direction) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind app speed publish",
                windImpactService.queryAppSpeedPublish(timestamp, periodType, direction)
        );
    }

    /**
     * 查询风观测、历史或预测数据。
     *
     * period 控制时间窗口类型，direction 取值为 1/2。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param period 可选周期类型
     * @param direction 可选方向（1=去往哈密方向，2=去往吐鲁番方向）
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
