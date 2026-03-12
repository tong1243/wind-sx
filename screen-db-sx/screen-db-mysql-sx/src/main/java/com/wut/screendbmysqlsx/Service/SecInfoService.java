package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.SecInfo;
import com.wut.screendbmysqlsx.Model.TunnelSecInfo;

import java.util.List;

public interface SecInfoService extends IService<SecInfo> {
    public List<SecInfo> getAllSecInfo();
    public List<TunnelSecInfo> getAllTunnelSecInfo();
}
