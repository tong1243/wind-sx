package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.WindThresholdUpdateReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindImpactService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4.2 大风时空影响模块接口。
 */
@RestController
@RequestMapping("/api/v1/wind-impact")
public class WindImpactController {
    /** 大风时空影响业务服务。 */
    private final WindImpactService windImpactService;

    public WindImpactController(WindImpactService windImpactService) {
        this.windImpactService = windImpactService;
    }

    /**
     * 4.2.1 全线风力可视化。
     */
    @GetMapping("/visualization/mainline")
    public DefaultDataResp getWindMainlineVisualization(@RequestParam("timestamp") String timestamp,
                                                        @RequestParam(value = "periodType", required = false) String periodType) {
        Object data = windImpactService.collectWindMainlineVisualization(Long.parseLong(timestamp), periodType);
        return ModelTransformUtil.getDefaultDataInstance("全线风力可视化数据", data);
    }

    /**
     * 4.2.2 查询风力限速阈值配置。
     */
    @GetMapping("/speed-thresholds")
    public DefaultDataResp getWindSpeedThresholdConfig() {
        Object data = windImpactService.collectWindSpeedThresholdConfig();
        return ModelTransformUtil.getDefaultDataInstance("风力限速阈值配置", data);
    }

    /**
     * 4.2.2 更新风力限速阈值配置。
     */
    @PostMapping("/speed-thresholds")
    public DefaultDataResp updateWindSpeedThreshold(@RequestBody WindThresholdUpdateReq req) {
        Object data = windImpactService.updateWindSpeedThreshold(req);
        return ModelTransformUtil.getDefaultDataInstance("风力限速阈值更新结果", data);
    }

    /**
     * 4.2.3 风力时空影响判断。
     */
    @GetMapping("/spacetime-impact")
    public DefaultDataResp getWindSpacetimeImpact(@RequestParam("timestamp") String timestamp,
                                                  @RequestParam(value = "periodType", required = false) String periodType,
                                                  @RequestParam(value = "direction", required = false) String direction) {
        Object data = windImpactService.collectWindSpacetimeImpact(Long.parseLong(timestamp), periodType, direction);
        return ModelTransformUtil.getDefaultDataInstance("风力时空影响判断数据", data);
    }

    /**
     * 4.2.4 大风数据查询。
     */
    @GetMapping("/query")
    public DefaultDataResp getWindDataQuery(@RequestParam("timestamp") String timestamp,
                                            @RequestParam(value = "periodType", required = false) String periodType,
                                            @RequestParam(value = "direction", required = false) String direction) {
        Object data = windImpactService.collectWindDataQuery(Long.parseLong(timestamp), periodType, direction);
        return ModelTransformUtil.getDefaultDataInstance("大风数据查询结果", data);
    }
}
