package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.TunnelRisk;

import java.util.List;

public interface TunnelRiskService extends IService<TunnelRisk> {
    public List<TunnelRisk> getThreeRisk(String date, long timestamp);

    public List<TunnelRisk> getAllRisk(String date, long timestamp);
}
