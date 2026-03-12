package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.HomeBundleDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.HomeBundleWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
public class HomeBundleController {
    private final HomeBundleWebService homeBundleWebService;

    @Autowired
    public HomeBundleController(HomeBundleWebService homeBundleWebService) {
        this.homeBundleWebService = homeBundleWebService;
    }

    @GetMapping("/data")
    public DefaultDataResp getHomeBundleData(@RequestParam("timestamp") String timestamp) {
        HomeBundleDataResp data = homeBundleWebService.collectHomeBundleData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("数字孪生实时数据", data);
    }

}
