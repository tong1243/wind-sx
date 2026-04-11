package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.ClosureDeviceStatic;

import java.util.List;

/**
 * 封路设备静态表服务接口。
 */
public interface ClosureDeviceStaticService extends IService<ClosureDeviceStatic> {
    /**
     * 查询启用的封路设备，按排序号升序返回。
     *
     * @return 启用封路设备列表
     */
    List<ClosureDeviceStatic> getEnabledDevices();
}
