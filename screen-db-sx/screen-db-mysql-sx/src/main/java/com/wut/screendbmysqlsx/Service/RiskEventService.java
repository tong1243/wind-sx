package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screencommonsx.Response.Risk.RiskLevelData;
import com.wut.screendbmysqlsx.Model.RiskEvent;

import java.util.List;

public interface RiskEventService extends IService<RiskEvent> {
    public List<RiskEvent> getAllRisk(String date, long timestamp);
    public List<RiskLevelData> getRiskLevelData(String date, long timestamp);
    List<RiskEvent> getTargetRisk(String date, long timestampStart, long timestampEnd);
}
