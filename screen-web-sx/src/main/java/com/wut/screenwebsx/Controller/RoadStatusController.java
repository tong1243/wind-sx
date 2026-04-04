package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindControlCenterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
/**
 * 4.1 路段运行状态相关接口。
 * 该控制器仅负责参数接收与返回封装，业务计算由 WindControlCenterService 统一处理。
 */
public class RoadStatusController {
    private final WindControlCenterService windControlCenterService;

    public RoadStatusController(WindControlCenterService windControlCenterService) {
        this.windControlCenterService = windControlCenterService;
    }

    /**
     * 获取全线路段运行总览。
     */
    @GetMapping("/road-statuses")
    public DefaultDataResp listRoadStatuses(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "road statuses",
                windControlCenterService.getRoadRunOverview(timestamp)
        );
    }

    /**
     * 获取服务区车辆进出与在场统计。
     */
    @GetMapping("/service-areas")
    public DefaultDataResp listServiceAreas(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "service areas",
                windControlCenterService.getServiceAreaVehicleStats(timestamp)
        );
    }

    /**
     * 获取交通状态分析结果。
     */
    @GetMapping("/traffic-states")
    public DefaultDataResp listTrafficStates(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "traffic states",
                windControlCenterService.getTrafficStateAnalysis(timestamp)
        );
    }
}
