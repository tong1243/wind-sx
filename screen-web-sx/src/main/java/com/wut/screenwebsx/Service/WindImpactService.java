package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Request.WindThresholdUpdateReq;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 大风时空影响模块服务（4.2）。
 */
@Component
public class WindImpactService {
    /** 第四章聚合服务。 */
    private final ControlModuleService controlModuleService;

    public WindImpactService(ControlModuleService controlModuleService) {
        this.controlModuleService = controlModuleService;
    }

    /** 4.2.1 全线风力可视化。 */
    public Map<String, Object> collectWindMainlineVisualization(long timestamp, String periodType) {
        return controlModuleService.collectWindMainlineVisualization(timestamp, periodType);
    }

    /** 4.2.2 查询风力限速阈值。 */
    public Map<String, Object> collectWindSpeedThresholdConfig() {
        return controlModuleService.collectWindSpeedThresholdConfig();
    }

    /** 4.2.2 更新风力限速阈值。 */
    public Map<String, Object> updateWindSpeedThreshold(WindThresholdUpdateReq req) {
        return controlModuleService.updateWindSpeedThreshold(req);
    }

    /** 4.2.3 风力时空影响判断。 */
    public Map<String, Object> collectWindSpacetimeImpact(long timestamp, String periodType, String direction) {
        return controlModuleService.collectWindSpacetimeImpact(timestamp, periodType, direction);
    }

    /** 4.2.4 大风数据查询。 */
    public Map<String, Object> collectWindDataQuery(long timestamp, String periodType, String direction) {
        return controlModuleService.collectWindDataQuery(timestamp, periodType, direction);
    }
}
