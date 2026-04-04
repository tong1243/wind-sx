package com.wut.screenwebsx.Service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 路段运行状态模块服务（4.1）。
 * 负责承接控制器请求并转发到聚合服务。
 */
@Component
public class RoadStatusService {
    /** 第四章聚合服务。 */
    private final ControlModuleService controlModuleService;

    public RoadStatusService(ControlModuleService controlModuleService) {
        this.controlModuleService = controlModuleService;
    }

    /**
     * 获取交通状态分析数据。
     *
     * @param timestamp 毫秒时间戳
     * @return 分析结果
     */
    public Map<String, Object> collectTrafficStatusAnalysis(long timestamp) {
        return controlModuleService.collectTrafficStatusAnalysis(timestamp);
    }
}
