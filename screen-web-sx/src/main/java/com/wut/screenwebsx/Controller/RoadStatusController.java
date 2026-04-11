package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindControlRoadStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4.1 路段运行状态模块接口。
 *
 * 所有接口以同一 timestamp 作为查询基准，
 * 便于前端在同一时间刻度下渲染总览、交通状态和事件检测信息。
 */
@RestController
@RequestMapping("/api/v1")
public class RoadStatusController {
    private final WindControlRoadStatusService roadStatusService;

    /**
     * 构造控制器并注入 4.1 业务服务。
     *
     * @param roadStatusService 路段状态业务服务
     */
    public RoadStatusController(WindControlRoadStatusService roadStatusService) {
        this.roadStatusService = roadStatusService;
    }

    /**
     * 查询全线路段运行总览。
     *
     * 返回数字孪生开关、基础设施数量和风区路段列表，
     * 用于首页“全局态势”卡片渲染。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 路段总览结果
     */
    @GetMapping("/road-statuses")
    public DefaultDataResp listRoadStatuses(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "road statuses",
                roadStatusService.getRoadRunOverview(timestamp)
        );
    }

    /**
     * 查询服务区车辆统计。
     *
     * 返回每个服务区的进站、出站和区内车辆数量，
     * 用于服务区负荷监控和分流判断。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 服务区统计列表
     */
    @GetMapping("/service-areas")
    public DefaultDataResp listServiceAreas(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "service areas",
                roadStatusService.getServiceAreaVehicleStats(timestamp)
        );
    }

    /**
     * 查询交通状态分析结果。
     *
     * 返回分路段流量和更新频率等指标，
     * 支撑前端“畅通/缓行/拥堵”态势判定。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 交通状态列表
     */
    @GetMapping("/traffic-states")
    public DefaultDataResp listTrafficStates(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "traffic states",
                roadStatusService.getTrafficStateAnalysis(timestamp)
        );
    }

    /**
     * 查询断面参数检测结果。
     *
     * direction 为可选参数：
     * 1=下行，2=上行；不传时返回双向结果。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param direction 方向过滤（可选，1=下行，2=上行）
     * @return 断面检测结果列表
     */
    @GetMapping("/section-parameter-detections")
    public DefaultDataResp listSectionParameterDetections(@RequestParam("timestamp") long timestamp,
                                                          @RequestParam(value = "direction", required = false) Integer direction) {
        return ModelTransformUtil.getDefaultDataInstance(
                "section parameter detections",
                roadStatusService.getSectionParameterDetections(timestamp, direction)
        );
    }

    /**
     * 查询事件检测信息。
     *
     * 返回超速、停车等检测事件及处理状态，
     * 供事件中心列表展示。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 事件检测列表
     */
    @GetMapping("/event-detections")
    public DefaultDataResp listEventDetections(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance(
                "event detections",
                roadStatusService.getEventDetectionInfos(timestamp)
        );
    }
}
