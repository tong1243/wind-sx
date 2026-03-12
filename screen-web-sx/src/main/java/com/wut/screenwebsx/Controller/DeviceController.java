package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.RadarTargetDataReq;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.Device.*;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.DeviceWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/device")
public class DeviceController {
    private final DeviceWebService deviceWebService;

    @Autowired
    public DeviceController(DeviceWebService deviceWebService) {
        this.deviceWebService = deviceWebService;
    }

    @GetMapping("/info")
    public DefaultDataResp getDeviceInfoData(@RequestParam("timestamp") String timestamp) {
        DeviceDataResp data = deviceWebService.collectDeviceInfoData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("设备分析设备信息", data);
    }

    @GetMapping("/data/real")
    public DefaultDataResp getDeviceRealTimeData(@RequestParam("timestamp") String timestamp) {
        RadarRealTimeDataResp data = deviceWebService.collectRadarRealTimeData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("雷达分析实时断面数据", data);
    }

    @PostMapping("/data/real/target")
    public DefaultDataResp getDeviceRealTimeDataWithTarget(@RequestBody TargetDataReq req) {
        RadarRealTimeDataResp data = deviceWebService.collectRadarRealTimeDataWithTarget(req);
        return ModelTransformUtil.getDefaultDataInstance("雷达分析实时断面数据(指定时间)", data);
    }

    @GetMapping("/data/period")
    public DefaultDataResp getDevicePeriodData(@RequestParam("timestamp") String timestamp, @RequestParam("rid") int rid) {
        RadarPeriodDataResp data = deviceWebService.collectRadarPeriodData(Long.parseLong(timestamp), rid);
        return ModelTransformUtil.getDefaultDataInstance("雷达分析今日断面数据", data);
    }

    @PostMapping("/data/target")
    public DefaultDataResp getDeviceTargetData(@RequestBody RadarTargetDataReq req) {
        RadarPeriodDataResp data = deviceWebService.collectRadarTargetData(req);
        return ModelTransformUtil.getDefaultDataInstance("雷达分析指定时间断面数据", data);
    }

    @GetMapping("/data/real/timeout")
    public DefaultDataResp getDeviceRealTimeTimeoutData(@RequestParam("timestamp") String timestamp) {
        DeviceRealTimeDataResp data = deviceWebService.collectDeviceRealTimeData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("设备分析实时断面时延", data);
    }

    @GetMapping("/radar/period/timeout")
    public DefaultDataResp getDeviceRadarPeriodTimeoutData(@RequestParam("timestamp") String timestamp, @RequestParam("rid") int rid) {
        DevicePeriodDataResp data = deviceWebService.collectDevicePeriodData(Long.parseLong(timestamp), rid);
        return ModelTransformUtil.getDefaultDataInstance("设备分析今日时延数据", data);
    }

    @PostMapping("/radar/target/timeout")
    public DefaultDataResp getDeviceRadarTargetTimeoutData(@RequestBody RadarTargetDataReq req) {
        DevicePeriodDataResp data = deviceWebService.collectDeviceTargetData(req);
        return ModelTransformUtil.getDefaultDataInstance("设备分析指定时间时延数据", data);
    }

    @GetMapping("/fiber/period/timeout")
    public DefaultDataResp getDeviceFiberPeriodTimeoutData(@RequestParam("timestamp") String timestamp) {
        DevicePeriodDataResp data = deviceWebService.collectDeviceFiberPeriodData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("设备分析今日光纤时延数据", data);
    }

    @PostMapping("/fiber/target/timeout")
    public DefaultDataResp getDeviceFiberTargetTimeoutData(@RequestBody TargetDataReq req) {
        DevicePeriodDataResp data = deviceWebService.collectDeviceFiberTargetData(req);
        return ModelTransformUtil.getDefaultDataInstance("设备分析指定时间光纤时延数据", data);
    }

}
