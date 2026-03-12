package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.OriginalData;

public interface OriginalDataService extends IService<OriginalData> {
    public OriginalData getOneByTime(long timestamp);
    public OriginalData getOneById(int id);
}
