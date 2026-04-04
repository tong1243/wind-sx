package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.RoadStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4.1 路段运行状态模块接口。
 */
@RestController
@RequestMapping("/api/v1/road-status")
public class RoadStatusController {
    /** 路段运行状态业务服务。 */
    private final RoadStatusService roadStatusService;

    public RoadStatusController(RoadStatusService roadStatusService) {
        this.roadStatusService = roadStatusService;
    }

    /**
     * 4.1.5 交通状态分析。
     *
     * @param timestamp 毫秒时间戳
     * @return 交通状态分析结果
     */
    @GetMapping("/analysis")
    public DefaultDataResp getTrafficStatusAnalysis(@RequestParam("timestamp") String timestamp) {
        Object data = roadStatusService.collectTrafficStatusAnalysis(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("交通状态分析数据", data);
    }
}
