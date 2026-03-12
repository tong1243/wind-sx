package com.wut.screendbmysqlsx.Service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.Section;

import java.util.List;

public interface SectionService extends IService<Section> {
    public List<Section> getListByDate(String date);
    public List<Section> getListByDateAndSec(String date, double sec);
    public List<Section> getListByTarget(String date, long startTimestamp, long endTimestamp);
    public List<Section> getListByTargetAndSec(String date, long startTimestamp, long endTimestamp, double sec);
    public List<Section> getLatestList(String date);

    public List<Section> getRadarRealTimeTargetList(String date, long startTimestamp, long endTimestamp);

}
