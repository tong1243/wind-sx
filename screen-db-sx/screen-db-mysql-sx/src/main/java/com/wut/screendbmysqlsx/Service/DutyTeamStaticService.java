package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.DutyTeamStatic;

import java.util.List;

/**
 * 执勤班组静态表服务接口。
 */
public interface DutyTeamStaticService extends IService<DutyTeamStatic> {
    /**
     * 查询启用的执勤班组，按排序号升序返回。
     *
     * @return 启用执勤班组列表
     */
    List<DutyTeamStatic> getEnabledTeams();
}
