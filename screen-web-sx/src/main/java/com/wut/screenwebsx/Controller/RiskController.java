package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.Risk.RiskDataResp;
import com.wut.screencommonsx.Response.Risk.RiskEventMinResp;
import com.wut.screencommonsx.Response.Risk.RiskEventResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.RiskWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {
@Autowired
public final RiskWebService riskWebService;

    public RiskController(RiskWebService riskWebService) {
        this.riskWebService = riskWebService;
    }
    @GetMapping("/info")
    public DefaultDataResp getRiskRealTimeData(@RequestParam("timestamp") String timestamp) {
        RiskEventResp data = riskWebService.collectRiskResp(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("风险分析实时数据", data);
    }
    @GetMapping("/history")
    public DefaultDataResp getRiskHistoryData(@RequestParam("timestamp") String timestamp) {
        List<RiskDataResp> data = riskWebService.collectRiskDataResp(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("风险分析历史数据", data);
    }
    @GetMapping("/target")
    public DefaultDataResp getRiskTargetData(@RequestParam("timestamp") String timestamp) {
        List<RiskEventMinResp> data = riskWebService.collectRiskDataMinResp(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("风险事件分钟数据", data);
    }
}
