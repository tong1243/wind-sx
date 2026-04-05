package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.DutyStaffStatic;

import java.util.List;

/**
 * 执勤人员静态表服务接口。
 */
public interface DutyStaffStaticService extends IService<DutyStaffStatic> {
    /**
     * 查询启用的执勤人员，按排序号升序返回。
     *
     * @return 启用执勤人员列表
     */
    List<DutyStaffStatic> getEnabledStaff();
}
