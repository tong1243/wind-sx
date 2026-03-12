package com.wut.screenwebsx.Service.DataPreSubService;

import com.wut.screencommonsx.Response.Risk.RiskData;
import com.wut.screencommonsx.Response.Risk.RiskEventMinResp;
import com.wut.screencommonsx.Response.Risk.RiskLevelData;
import com.wut.screencommonsx.Util.DataParamParseUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screendbmysqlsx.Model.Parameters;
import com.wut.screendbmysqlsx.Model.RiskEvent;
import com.wut.screendbmysqlsx.Model.TunnelRisk;
import com.wut.screendbmysqlsx.Service.ParametersService;
import com.wut.screendbmysqlsx.Service.RiskEventService;
import com.wut.screendbmysqlsx.Service.TunnelRiskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RiskDataPreService {
    private final RiskEventService riskEventService;
    private final TunnelRiskService tunnelRiskService;
    private final ParametersService parametersService;

    public RiskDataPreService(RiskEventService riskEventService, TunnelRiskService tunnelRiskService, ParametersService parametersService) {
        this.riskEventService = riskEventService;
        this.tunnelRiskService = tunnelRiskService;
        this.parametersService = parametersService;
    }


    public  List<TunnelRisk> initThreeTunnelRiskData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        return  tunnelRiskService.getThreeRisk(tableDateStr,timestamp);
    }
    public  List<TunnelRisk> initTunnelRiskData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        return  tunnelRiskService.getAllRisk(tableDateStr,timestamp);
    }

    public  List<RiskEvent> initRiskEventData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        return riskEventService.getAllRisk(tableDateStr,timestamp);
    }
    public  List<RiskEvent> initRiskEventMinData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        return riskEventService.getTargetRisk(tableDateStr,timestamp - 60000 , timestamp);
    }
    public List<RiskLevelData> initRiskLevelData(long timestamp){
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        return riskEventService.getRiskLevelData(tableDateStr,timestamp);
    }
    public List<RiskData> initRiskData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Parameters> parametersList = parametersService.getListByDate(tableDateStr);
        List<TunnelRisk> allRisk = tunnelRiskService.getThreeRisk(tableDateStr, timestamp);

        // 筛选roadId为3、4、7的记录
        List<Parameters> filteredParameters = parametersList.stream()
                .filter(p -> Arrays.asList(3, 4, 7).contains(p.getRoadId()))
                .collect(Collectors.toList());

        // 获取这些roadId对应的风险等级
        Map<Integer, Integer> roadIdToRiskLevel = allRisk.stream()
                .filter(r -> Arrays.asList(3, 4, 7).contains(r.getSId()))
                .collect(Collectors.toMap(
                        TunnelRisk::getSId,
                        TunnelRisk::getRiskLevel,
                        (existing, replacement) -> existing // 如果有重复，保留第一个
                ));

        // 构建结果列表
        return filteredParameters.stream()
                .map(p -> {
                    RiskData riskData = new RiskData();
                    riskData.setSid(p.getRoadId());
                    riskData.setDensity(DataParamParseUtil.getRoundValue(p.getDensity()));
                    riskData.setSpeed(DataParamParseUtil.getRoundValue(p.getSpeed()));
                    riskData.setRiskLevel(roadIdToRiskLevel.getOrDefault(p.getRoadId(), 0)); // 默认值为0
                    return riskData;
                })
                .collect(Collectors.toList());
    }

}
