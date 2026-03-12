package com.wut.screenwebsx.Service;



import com.wut.screencommonsx.Response.Risk.*;
import com.wut.screendbmysqlsx.Model.RiskEvent;
import com.wut.screendbmysqlsx.Model.TunnelRisk;
import com.wut.screenwebsx.Config.DockingInterfaceConfig.Docking;
import com.wut.screenwebsx.Service.DataPreSubService.RiskDataPreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.wut.screendbmysqlsx.Util.DbModelTransformUtil.convertRiskEvent;
import static com.wut.screendbmysqlsx.Util.DbModelTransformUtil.convertTunnelRisk;

@Component
public class RiskWebService {
    private final RiskDataPreService riskDataPreService;

    @Autowired
    public RiskWebService(RiskDataPreService riskDataPreService) {
        this.riskDataPreService = riskDataPreService;
    }

    @Docking
    public RiskEventResp collectRiskResp(long timestamp) {
        // 获取风险事件数据并转换类型
        List<com.wut.screendbmysqlsx.Model.RiskEvent> modelRiskEventList = riskDataPreService.initRiskEventData(timestamp);
        List<com.wut.screencommonsx.Response.Risk.RiskEvent> responseRiskEventList = new ArrayList<>();
        for (com.wut.screendbmysqlsx.Model.RiskEvent modelRiskEvent : modelRiskEventList) {
            responseRiskEventList.add(convertRiskEvent(modelRiskEvent));
        }

        // 获取隧道风险数据并转换类型
        List<com.wut.screendbmysqlsx.Model.TunnelRisk> modelTunnelRiskList = riskDataPreService.initThreeTunnelRiskData(timestamp);
        List<com.wut.screencommonsx.Response.Risk.TunnelRisk> responseTunnelRiskList = new ArrayList<>();
        for (com.wut.screendbmysqlsx.Model.TunnelRisk modelTunnelRisk : modelTunnelRiskList) {
            responseTunnelRiskList.add(convertTunnelRisk(modelTunnelRisk));
        }

        // 获取风险等级数据
        List<RiskLevelData> riskLevelData = riskDataPreService.initRiskLevelData(timestamp);
        // 获取风险数据
        List<RiskData> riskData = riskDataPreService.initRiskData(timestamp);

        // 将所有收集的数据封装到 RiskEventResp 对象中并返回
        return new RiskEventResp(riskLevelData, riskData, responseRiskEventList, responseTunnelRiskList);
    }

    public List<RiskDataResp> collectRiskDataResp(long timestamp) {
        List<TunnelRisk> tunnelRisks = riskDataPreService.initTunnelRiskData(timestamp);
        List<RiskDataResp> riskDataRespList = new ArrayList<>();
        for (TunnelRisk tunnelRisk : tunnelRisks){
            riskDataRespList.add(new RiskDataResp(tunnelRisk.getSId(),1, tunnelRisk.getTimestamp(),  tunnelRisk.getRiskLevel(), 1));
        }
     return riskDataRespList;
    }

    public List<RiskEventMinResp> collectRiskDataMinResp(long timestamp) {
        List<RiskEvent> riskEventList = riskDataPreService.initRiskEventMinData(timestamp);
        List<RiskEventMinResp> riskEventMinResp = new ArrayList<>();
        for (RiskEvent riskEvent : riskEventList){
            riskEventMinResp.add(new RiskEventMinResp(riskEvent.getTimestamp(), riskEvent.getCarId(), riskEvent.getPosition(), riskEvent.getRiskType()));
        }
        return riskEventMinResp;
    }
}
