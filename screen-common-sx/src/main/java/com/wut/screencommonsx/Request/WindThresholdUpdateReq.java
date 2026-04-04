package com.wut.screencommonsx.Request;

/**
 * 风力限速阈值更新请求模型。
 */
public class WindThresholdUpdateReq {
    /** 管控等级，如：一级管控/二级管控。 */
    private String controlLevel;
    /** 风力范围，如：9-10级。 */
    private String windRange;
    /** 小客车限速（km/h）。 */
    private Integer lightVehicleSpeedLimit;
    /** 客货车限速（km/h）。 */
    private Integer heavyVehicleSpeedLimit;

    /** 获取管控等级。 */
    public String getControlLevel() {
        return controlLevel;
    }

    /** 设置管控等级。 */
    public void setControlLevel(String controlLevel) {
        this.controlLevel = controlLevel;
    }

    /** 获取风力范围。 */
    public String getWindRange() {
        return windRange;
    }

    /** 设置风力范围。 */
    public void setWindRange(String windRange) {
        this.windRange = windRange;
    }

    /** 获取小客车限速。 */
    public Integer getLightVehicleSpeedLimit() {
        return lightVehicleSpeedLimit;
    }

    /** 设置小客车限速。 */
    public void setLightVehicleSpeedLimit(Integer lightVehicleSpeedLimit) {
        this.lightVehicleSpeedLimit = lightVehicleSpeedLimit;
    }

    /** 获取客货车限速。 */
    public Integer getHeavyVehicleSpeedLimit() {
        return heavyVehicleSpeedLimit;
    }

    /** 设置客货车限速。 */
    public void setHeavyVehicleSpeedLimit(Integer heavyVehicleSpeedLimit) {
        this.heavyVehicleSpeedLimit = heavyVehicleSpeedLimit;
    }
}
