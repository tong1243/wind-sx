package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.Parameters;
import com.wut.screendbmysqlsx.Model.Posture;

import java.util.List;

public interface ParametersService extends IService<Parameters> {
    public List<Parameters> getListByDate(String date);
    public List<Parameters> getAllListByDate(String date);

    Parameters getSecStream(String date, int sId);
//    public List<Parameters> getListByTarget(String date, long startTimestamp, long endTimestamp);
//    public Posture getLatestOne(String date);

}
