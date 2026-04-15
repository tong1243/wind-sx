package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindRiskSpeedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WindRiskSpeedController {
    private final WindRiskSpeedService windRiskSpeedService;

    public WindRiskSpeedController(WindRiskSpeedService windRiskSpeedService) {
        this.windRiskSpeedService = windRiskSpeedService;
    }

    @PostMapping("/wind-risk-speed/calculate")
    public DefaultDataResp calculateAndPersist(@RequestParam("startTimestamp") long startTimestamp,
                                               @RequestParam("endTimestamp") long endTimestamp,
                                               @RequestParam(value = "inputDataSource", required = false) String inputDataSource,
                                               @RequestParam(value = "outputDataSource", required = false) String outputDataSource) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind risk speed calculation",
                windRiskSpeedService.calculateAndPersist(startTimestamp, endTimestamp, inputDataSource, outputDataSource)
        );
    }

    @GetMapping("/wind-risk-sections/hourly")
    public DefaultDataResp listRiskSections(@RequestParam("startTimestamp") long startTimestamp,
                                            @RequestParam("endTimestamp") long endTimestamp,
                                            @RequestParam(value = "outputDataSource", required = false) String outputDataSource,
                                            @RequestParam(value = "direction", required = false) Integer direction) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind risk sections hourly",
                windRiskSpeedService.listRiskSections(startTimestamp, endTimestamp, outputDataSource, direction)
        );
    }

    @GetMapping("/wind-speed-limits/hourly")
    public DefaultDataResp listSpeedLimits(@RequestParam("startTimestamp") long startTimestamp,
                                           @RequestParam("endTimestamp") long endTimestamp,
                                           @RequestParam(value = "outputDataSource", required = false) String outputDataSource,
                                           @RequestParam(value = "direction", required = false) Integer direction) {
        return ModelTransformUtil.getDefaultDataInstance(
                "wind speed limits hourly",
                windRiskSpeedService.listSpeedLimits(startTimestamp, endTimestamp, outputDataSource, direction)
        );
    }
}
