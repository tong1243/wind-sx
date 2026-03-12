package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.RadarInfo;

import java.util.List;

public interface RadarInfoService extends IService<RadarInfo> {
    public List<RadarInfo> getAllRadarInfo();

    public List<RadarInfo> getEnabledRadarInfo();

}
